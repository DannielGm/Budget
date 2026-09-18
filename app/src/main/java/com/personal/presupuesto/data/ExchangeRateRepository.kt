package com.personal.presupuesto.data

import com.personal.presupuesto.currentMonthId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class DatedRate(val dateIso: String, val rateBsPerUsd: BigDecimal)

/** Thin blocking HTTP used only by [HttpExchangeRateHttp]; fakes replace it in tests. */
interface ExchangeRateHttp {
    fun get(url: String): String
}

class HttpExchangeRateHttp : ExchangeRateHttp {
    override fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}

/** Parses the two dolarapi.com payloads; pure functions so tests feed canned JSON. */
object ExchangeRateParser {
    private fun numberToBigDecimal(value: Any?): BigDecimal? =
        value?.toString()?.toBigDecimalOrNull()?.takeIf { it.signum() >= 0 }

    // {"promedio": 847.4442, "fechaActualizacion": "2026-09-17T00:00:00-04:00", ...}
    fun parseOfficial(json: String): DatedRate? = runCatching {
        val root = JSONObject(json)
        val rate = numberToBigDecimal(root.opt("promedio")) ?: return null
        val date = root.getString("fechaActualizacion").take(10)
        DatedRate(date, rate)
    }.getOrNull()

    // [{"promedio": 17.5591, "fecha": "2023-01-03"}, ...] (business days only)
    fun parseHistory(json: String): List<DatedRate> = runCatching {
        val array = JSONArray(json)
        (0 until array.length()).mapNotNull { index ->
            val entry = array.getJSONObject(index)
            val rate = numberToBigDecimal(entry.opt("promedio")) ?: return@mapNotNull null
            DatedRate(entry.getString("fecha"), rate)
        }
    }.getOrDefault(emptyList())
}

/** One snapshot per ensure/refresh call; drives the badge and the stale indicator. */
data class RateSnapshot(
    val todayIso: String,
    val displayedDateIso: String?,
    val displayedRate: BigDecimal?,
    val updatedToday: Boolean,
    val error: String? = null
)

/**
 * BCV exchange rates via dolarapi.com. Rates are cached in the local exchange_rates
 * table so normal starts make no network call; today's official rate is fetched when
 * missing, and the historical series backfills any date the app needs.
 */
class ExchangeRateRepository(
    database: BudgetDatabase,
    private val http: ExchangeRateHttp = HttpExchangeRateHttp()
) {
    private val dao = database.budgetDao()

    @Volatile private var lastHistoryFetchAt: Long = 0L

        // Keeps the badge updating daily while normal starts stay network-free.
    suspend fun ensureFresh(force: Boolean = false): RateSnapshot {
        val today = currentLocalDateIso()
        var error: String? = null
        val stored = dao.rateOnOrBefore(today)
        if (force || stored == null || stored.dateIso != today) {
            runCatching {
                val official = ExchangeRateParser.parseOfficial(http.get(OFFICIAL_URL)) ?: return@runCatching
                dao.insertExchangeRates(
                    listOf(ExchangeRateEntity(official.dateIso, official.rateBsPerUsd.toPlainString(), System.currentTimeMillis()))
                )
            }.onFailure { error = it.message ?: "Error de red" }
        }
        backfillIfNeeded()
        refreshMonthRates()
        val latest = dao.rateOnOrBefore(today)
        return RateSnapshot(
            todayIso = today,
            displayedDateIso = latest?.dateIso,
            displayedRate = latest?.rateBsPerUsd?.toBigDecimal(),
            updatedToday = latest?.dateIso == today,
            error = error
        )
        }


    // Pulls the full historical series and stores only the missing dates. Runs when the
    // table is empty, when a stored month has no boundary coverage, or after a gap.
                suspend fun backfillIfNeeded(): Int {
        val needsCoverage = dao.countRates() == 0 || missingMonthBoundaryDates()
        if (!needsCoverage && System.currentTimeMillis() - lastHistoryFetchAt < HISTORY_RETRY_MILLIS) return 0
        return withContext(Dispatchers.IO) {
            runCatching {
                val rates = ExchangeRateParser.parseHistory(http.get(HISTORY_URL))
                val existing = dao.rateDates().toSet()
                val fresh = rates.filter { it.dateIso !in existing }
                lastHistoryFetchAt = System.currentTimeMillis()
                if (fresh.isNotEmpty()) {
                    dao.insertExchangeRates(
                        fresh.map { ExchangeRateEntity(it.dateIso, it.rateBsPerUsd.toPlainString(), System.currentTimeMillis()) }
                    )
                    fresh.size
                } else 0
            }.getOrDefault(0)
        }
    }

    // Latest stored rate on or before the given day; falls back to the month's rate when
    // the day has no coverage (offline, or before the API's history start).
    suspend fun dayRate(dateIso: String, monthRate: BigDecimal): BigDecimal {
        val local = dao.rateOnOrBefore(dateIso)?.rateBsPerUsd?.toBigDecimal()
        if (local != null) return local
        backfillIfNeeded()
        return dao.rateOnOrBefore(dateIso)?.rateBsPerUsd?.toBigDecimal() ?: monthRate
    }

    /**
     * The month's default rate: the first BCV entry of the month for the current month
     * (provisionally the last rate before the month until BCV publishes one), and the
     * rate of the month's last day for finished months.
     */
    suspend fun monthRateFor(monthId: String): BigDecimal {
        val monthStart = "${monthId}-01"
        return if (monthId >= currentMonthId()) {
            dao.rateOnOrAfter(monthStart)?.rateBsPerUsd?.toBigDecimal()
                ?: dao.rateBefore(monthStart)?.rateBsPerUsd?.toBigDecimal()
                ?: BigDecimal.ZERO
        } else {
            dao.rateOnOrBefore(monthEndIso(monthId))?.rateBsPerUsd?.toBigDecimal() ?: BigDecimal.ZERO
        }
    }

    // Self-healing: recomputes every stored month's rate from the local table and rewrites
    // only where it differs, so months never sit at zero once the API can supply them.
    // The current month follows its first-of-month entry; finished months follow their
    // last day. Values the rules already agree with are preserved.
    suspend fun refreshMonthRates(): Int = withContext(Dispatchers.IO) {
        var changed = 0
        for (record in dao.budgets()) {
            val target = monthRateFor(record.monthId)
            if (target != record.incomeRate.toBigDecimal()) {
                dao.updateIncomeRate(record.monthId, target.toPlainString())
                changed++
            }
        }
        changed
    }

    private suspend fun missingMonthBoundaryDates(): Boolean {
        val months = dao.budgets().map { it.monthId } + currentMonthId()
        for (monthId in months.distinct()) {
            if (dao.rateOnOrAfter("${monthId}-01") == null) return true
            if (monthId < currentMonthId() && dao.rateOnOrBefore(monthEndIso(monthId)) == null) return true
        }
        return false
    }

    private fun monthEndIso(monthId: String): String {
        val calendar = Calendar.getInstance().apply {
            clear()
            set(monthId.substringBefore('-').toInt(), monthId.substringAfter('-').toInt() - 1, 1)
            add(Calendar.MONTH, 1)
            add(Calendar.DATE, -1)
        }
        return "%04d-%02d-%02d".format(
            Locale.US,
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    companion object {
        private const val OFFICIAL_URL = "https://ve.dolarapi.com/v1/dolares/oficial"
        private const val HISTORY_URL = "https://ve.dolarapi.com/v1/historicos/dolares/oficial"
        private const val HISTORY_RETRY_MILLIS = 5L * 60 * 1000
    }
}

fun currentLocalDateIso(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

fun dateIsoOf(timestamp: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(timestamp))