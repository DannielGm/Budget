package com.personal.presupuesto

import android.content.Context
import androidx.room.Room
import com.personal.presupuesto.data.BudgetDatabase
import com.personal.presupuesto.data.BudgetRepository
import com.personal.presupuesto.data.DatedRate
import com.personal.presupuesto.data.ExchangeRateParser
import com.personal.presupuesto.data.ExchangeRateRepository
import com.personal.presupuesto.data.ExchangeRateHttp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import java.math.BigDecimal
import java.util.Calendar as JavaCalendar
import java.util.Locale
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
class BudgetTest {
    private fun n(value: String) = BigDecimal(value)
    @Test fun zeroRowRateReturnsZero() {
        assertEquals(0, convert(n("500"), n("0")).compareTo(BigDecimal.ZERO))
    }

    @Test fun zeroExpenseRateUsesBudgetRateForDisplayTotals() {
        val budget = Budget(
            "Septiembre",
            n("1000"),
            n("10"),
            n("0"),
            listOf(
                Category("HOGAR", true, listOf(Expense("1", "Agua", n("500"), n("10"))))
            ),
            monthId = "2026-09"
        )

        assertEquals(0, budget.cashUsd.compareTo(n("50")))
        assertEquals(0, budget.categories.first().totalUsdAt(n("10")).compareTo(n("50")))
    }
    @Test fun cashBalanceExcludesCreditAndDeductsConversions() {
        val budget = Budget("Septiembre", n("1000"), n("10"), n("100"), listOf(
            Category("HOGAR", true, listOf(Expense("1", "Internet", n("200"), n("20")))),
            Category("GASTOS PERSONALES", false, listOf(Expense("2", "Compra", n("300"), n("10"))))
        ), monthId = "2026-09")
        // Phase 4.3: cashUsd uses the month's incomeRate (10), not the row rate (20).
        // 200 Bs / 10 = 20 USD.
        assertEquals(0, budget.cashUsd.compareTo(n("20")))
        assertEquals(0, budget.balanceBs.compareTo(n("700")))
        assertEquals(0, budget.balanceUsd!!.compareTo(n("70")))
    }
    @Test fun debtIncludesUnpaidOpeningBalancesAndCreditPurchases() {
        val budget = Budget("Septiembre", n("1000"), n("10"), n("0"), listOf(
            Category("GASTOS PERSONALES", false, listOf(Expense("1", "Compra", n("300"), n("10"))))
        ), listOf(Debt("Visa", n("500"), n("200"))), monthId = "2026-09")
        assertEquals(0, budget.debtBs.compareTo(n("600")))
        assertEquals(0, budget.debtUsd!!.compareTo(n("60")))
    }
    @Test fun zeroIncomeRateMakesBalanceUnavailable() {
        val budget = Budget("Septiembre", n("100"), n("0"), n("0"), emptyList(), monthId = "2026-09")
        assertNull(budget.balanceUsd)
    }

    @Test fun dateFilterMatchesSameDayIgnoringTime() {
        val day = 1725177600000L // 2024-09-01 00:00 UTC (approx)
        val budget = Budget(
            "Septiembre",
            n("1000"),
            n("10"),
            n("0"),
            listOf(
                Category(
                    "HOGAR",
                    true,
                    listOf(
                        Expense("1", "Internet", n("200"), n("20"), day),
                        Expense("2", "Agua", n("50"), n("5"), day + 3L * 60 * 60 * 1000)
                    )
                )
            ),
            monthId = "2026-09"
        )
        val filtered = budget.filteredCategories(day + 12L * 60 * 60 * 1000)
        assertEquals(1, filtered.size)
        assertEquals(2, filtered.first().rows.size)
    }

                        private fun memoryRepository(fake: ExchangeRateHttp = object : ExchangeRateHttp { override fun get(url: String) = "" }): Pair<BudgetDatabase, BudgetRepository> {
        val database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), BudgetDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val exchange = ExchangeRateRepository(database, fake)
        return database to BudgetRepository(database, exchange)
    }


    private fun monthIdMonthsAhead(months: Int): String {
        val calendar = JavaCalendar.getInstance().apply { add(JavaCalendar.MONTH, months) }
        return "%04d-%02d".format(Locale.US, calendar.get(JavaCalendar.YEAR), calendar.get(JavaCalendar.MONTH) + 1)
    }

    // Material3 hands back the picked day at 00:00 UTC, so build the same shape here.
    private fun utcMidnightOfLocalDate(offsetDays: Int): Long {
        val local = JavaCalendar.getInstance()
        return JavaCalendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(
                local.get(JavaCalendar.YEAR),
                local.get(JavaCalendar.MONTH),
                local.get(JavaCalendar.DAY_OF_MONTH) + offsetDays,
                0,
                0,
                0
            )
        }.timeInMillis
    }

    private fun budgetFor(monthId: String) = Budget(
        monthId = monthId,
        monthLabel = "Ignorado",
        incomeBs = n("1000"),
        incomeRate = n("10"),
        conversionBs = n("0"),
        categories = emptyList()
    )

    @Test fun repositorySupportsMultipleMonths() = runBlocking {
        val (db, repo) = memoryRepository()
        val previous = monthIdMonthsAhead(-1)
        val current = currentMonthId()

        repo.save(budgetFor(previous))
        repo.save(budgetFor(current))

        // YYYY-MM ids sort chronologically, unlike the previous opaque month names.
        assertEquals(listOf(previous, current), repo.listMonthIds())
        assertEquals(listOf(previous, current), repo.listMonths().map { it.monthId })
        assertEquals(monthDisplayName(previous), repo.load(previous)?.monthLabel)
        assertEquals(monthDisplayName(current), repo.load(current)?.monthLabel)
        db.close()
    }

    @Test fun monthNamesComeFromTheMonthId() {
        assertEquals("Septiembre", monthName("2026-09"))
        assertEquals("Enero", monthName("2026-01"))
        assertEquals("Diciembre", monthName("2026-12"))
        assertEquals(monthName(currentMonthId()), monthDisplayName(currentMonthId()))
        assertEquals("Mayo 1999", monthDisplayName("1999-05"))
        assertEquals("sin-forma", monthName("sin-forma"))
    }

    @Test fun futureMonthsAreRejected() = runBlocking {
        val (db, repo) = memoryRepository()
        val future = budgetFor(monthIdMonthsAhead(2))

        var saveRejected = false
        try { repo.save(future) } catch (_: IllegalArgumentException) { saveRejected = true }
        assertTrue(saveRejected)

        var createRejected = false
        try { repo.loadOrCreate(future.monthId, future) } catch (_: IllegalArgumentException) { createRejected = true }
        assertTrue(createRejected)

        assertEquals(emptyList<String>(), repo.listMonthIds())
        db.close()
    }

    @Test fun loadOrCreateMakesAnEmptyMonthAndKeepsCategoryNames() = runBlocking {
        val (db, repo) = memoryRepository()
        val current = currentMonthId()
        val template = budgetFor(current).copy(
            categories = listOf(
                Category("HOGAR", true, listOf(Expense("1", "Agua", n("500"), n("5")))),
                Category("GASTOS PERSONALES", false, emptyList())
            ),
            debts = listOf(Debt("Visa", n("100"), n("50")))
        )

        val created = repo.loadOrCreate(current, template)

        assertEquals(current, created.monthId)
        assertEquals(monthDisplayName(current), created.monthLabel)
        assertEquals(listOf("HOGAR", "GASTOS PERSONALES"), created.categories.map { it.name })
        assertEquals(listOf(true, false), created.categories.map { it.cashExpense })
        assertTrue(created.categories.all { it.rows.isEmpty() })
        assertTrue(created.debts.isEmpty())
        assertEquals(0, created.incomeBs.compareTo(BigDecimal.ZERO))
        assertEquals(0, created.incomeRate.compareTo(BigDecimal.ZERO))

        // A second call reuses the stored month instead of creating another one.
        val reloaded = repo.loadOrCreate(current, template)
        assertEquals(listOf(current), repo.listMonthIds())
        assertEquals(created.categories.map { it.name }, reloaded.categories.map { it.name })
        db.close()
    }

    @Test fun expenseMovesBetweenMonthsAndClearIsIsolated() = runBlocking {
        val (db, repo) = memoryRepository()
        try {
            val current = currentMonthId()
            val source = budgetFor(current).copy(categories = listOf(Category("HOGAR", true, emptyList())))
            repo.save(source)
            val pastDate = JavaCalendar.getInstance().apply { add(JavaCalendar.MONTH, -1) }.timeInMillis
            val expense = Expense("move", "Prueba", n("20"), n("2"), pastDate)
            val past = repo.recordExpense(current, "HOGAR", expense)
            assertEquals(monthIdOf(pastDate), past.monthId)
            assertEquals(listOf(expense), past.categories.single().rows)
            assertTrue(repo.load(current)!!.categories.single().rows.isEmpty())
            val moved = repo.recordExpense(past.monthId, "HOGAR", expense.copy(timestamp = System.currentTimeMillis()))
            assertEquals(current, moved.monthId)
            assertEquals(1, moved.categories.single().rows.size)
            assertTrue(repo.load(past.monthId)!!.categories.single().rows.isEmpty())
            repo.clearMonth(past.monthId)
            assertEquals(1, repo.load(current)!!.categories.single().rows.size)
            assertEquals(2, repo.listMonths().size)
            repo.clearAllKeepingMonth(past.monthId)
            assertEquals(listOf(past.monthId), repo.listMonthIds())
            assertEquals(past.monthId, repo.loadOrSeed(source).monthId)
            assertTrue(repo.load(past.monthId)!!.categories.single().rows.isEmpty())
        } finally { db.close() }
    }

    @Test fun startupCreatesCurrentMonthWithoutCopyingHistoricalValues() = runBlocking {
        val (db, repo) = memoryRepository()
        try {
            val past = monthIdMonthsAhead(-1)
            val history = budgetFor(past).copy(
                categories = listOf(Category("HOGAR", true, listOf(
                    Expense("old", "Histórico", n("20"), n("2"), localDateFromPicker(pickerStartOfMonth(past)))
                ))),
                debts = listOf(Debt("Visa", n("100"), n("10")))
            )
            repo.save(history)
            val before = repo.load(past)
            val opened = repo.loadCurrentMonth(budgetFor(currentMonthId()))
            assertEquals(listOf(Category("HOGAR", true, emptyList())), opened.categories)
            // Phase 4.3: debts are carried forward (100 - 10 = 90).
            assertEquals(1, opened.debts.size)
            assertEquals(0, opened.debts.first().openingBs.compareTo(n("90")))
            assertEquals(before, repo.load(past))
            repo.load(past) // Browsing history must not change the startup month.
            assertEquals(opened, BudgetRepository(db).loadCurrentMonth(history))
            assertEquals(listOf(past, currentMonthId()), repo.listMonthIds())
        } finally { db.close() }
    }

    @Test fun startupPreservesExistingCurrentMonthAndDoesNotReseedAfterClear() = runBlocking {
        val (db, repo) = memoryRepository()
        try {
            val past = budgetFor(monthIdMonthsAhead(-1))
            val current = budgetFor(currentMonthId()).copy(incomeBs = n("321"))
            repo.save(past)
            repo.save(current)
            val before = repo.load(current.monthId)
            assertEquals(before, repo.loadCurrentMonth(past))
            repo.clearAllKeepingMonth(past.monthId)
            val opened = repo.loadCurrentMonth(past)
            assertEquals(current.monthId, opened.monthId)
            assertEquals(BigDecimal.ZERO, opened.incomeBs)
            assertEquals(BigDecimal.ZERO, repo.load(past.monthId)!!.incomeBs)
        } finally { db.close() }
    }

    @Test fun firstStartupKeepsSeedAndOpensCurrentMonth() = runBlocking {
        val (db, repo) = memoryRepository()
        try {
            val seed = budgetFor(monthIdMonthsAhead(-1))
            assertEquals(currentMonthId(), repo.loadCurrentMonth(seed).monthId)
            assertEquals(seed.incomeBs, repo.load(seed.monthId)!!.incomeBs)
        } finally { db.close() }
    }

    @Test fun calendarMonthSelectionUsesUtcAcrossYearsAndTimeZones() {
        val original = TimeZone.getDefault()
        try {
            for (zone in listOf("America/Los_Angeles", "Pacific/Kiritimati", "UTC")) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                for (month in listOf("2024-12", "2025-01", "2024-02")) {
                    val first = pickerStartOfMonth(month)
                    assertEquals(month, monthIdFromPicker(first))
                    assertEquals(month, monthIdOf(localDateFromPicker(first)))
                }
                assertEquals("2024-12", monthIdFromPicker(pickerStartOfMonth("2025-01") - 1))
            }
        } finally { TimeZone.setDefault(original) }
    }

    @Test fun budgetCalendarAllowsUnstoredPastDatesAndTodayButNotFuture() {
        val past = monthIdMonthsAhead(-1)
        assertTrue(isSelectableBudgetDay(pickerStartOfMonth(past), setOf(past)))
        assertTrue(isSelectableBudgetDay(pickerStartOfMonth(past), emptySet()))
        assertTrue(isSelectableBudgetDay(pickerStartOfMonth(currentMonthId()), emptySet()))
        assertTrue(isSelectableBudgetDay(utcMidnightOfLocalDate(0), emptySet()))
        assertFalse(isSelectableBudgetDay(utcMidnightOfLocalDate(1), setOf(currentMonthId())))
        val future = monthIdMonthsAhead(1)
        assertFalse(isSelectableBudgetDay(pickerStartOfMonth(future), setOf(future)))
    }

    @Test fun rateParsingRoundTripsCanonicalPayloads() {
        assertEquals(
            DatedRate("2026-09-17", n("847.4442")),
            ExchangeRateParser.parseOfficial(
                """
                {"promedio": 847.4442, "fechaActualizacion": "2026-09-17T00:00:00-04:00"}
                """.trimIndent()
            )
        )
        assertEquals(
            listOf(DatedRate("2023-01-03", n("17.5591"))),
            ExchangeRateParser.parseHistory(
                """[{"promedio": 17.5591, "fecha": "2023-01-03"}]"""
            )
        )
    }

    @Test fun currentMonthUsesFirstEntryOfThatMonthProvisionallyLastBeforeMonth() = runBlocking {
        val fake = FakeHttp(
            official = mapOf("https://ve.dolarapi.com/v1/dolares/oficial" to """{"promedio": 847.4442, "fechaActualizacion": "2026-09-17T00:00:00-04:00"}"""),
            history = listOf(
                """{"promedio": 100.0, "fecha": "2026-08-29"}""",
                """{"promedio": 200.0, "fecha": "2026-09-01"}"""
            )
        )
        val (db, repo) = memoryRepository(fake)
        try {
            val monthId = currentMonthId()
            repo.exchange!!.backfillIfNeeded()
            assertEquals(n("200.0"), repo.exchange!!.monthRateFor(monthId))
        } finally { db.close() }
    }

    @Test fun pastMonthUsesLastDayRate() = runBlocking {
        val fake = FakeHttp(
            official = mapOf("https://ve.dolarapi.com/v1/dolares/oficial" to """{"promedio": 0, "fechaActualizacion": "2026-09-17T00:00:00-04:00"}"""),
            history = listOf(
                """{"promedio": 100.0, "fecha": "2026-08-01"}""",
                """{"promedio": 250.0, "fecha": "2026-08-30"}"""
            )
        )
        val (db, repo) = memoryRepository(fake)
        try {
            repo.exchange!!.backfillIfNeeded()
            assertEquals(n("250.0"), repo.exchange!!.monthRateFor("2026-08"))
        } finally { db.close() }
    }

    @Test fun dayRateReturnsLatestOnOrBeforeWithMonthFallbackWhenMissing() = runBlocking {
        val fake = FakeHttp(
            official = mapOf("https://ve.dolarapi.com/v1/dolares/oficial" to """{"promedio": 0, "fechaActualizacion": "2026-09-17T00:00:00-04:00"}"""),
            history = listOf(
                """{"promedio": 100.0, "fecha": "2026-09-01"}""",
                """{"promedio": 200.0, "fecha": "2026-09-05"}"""
            )
        )
        val (db, repo) = memoryRepository(fake)
        try {
            repo.exchange!!.backfillIfNeeded()
            assertEquals(n("200.0"), repo.exchange!!.dayRate("2026-09-05", n("50.0")))
            // Fallback used for date before history start.
            assertEquals(n("50.0"), repo.exchange!!.dayRate("2022-12-31", n("50.0")))
        } finally { db.close() }
    }

    @Test fun ensureTodaySeedsBadgeAndIsIdempotentWhenAlreadyPresent() = runBlocking {
        val fake = FakeHttp(
            official = mapOf(
                "https://ve.dolarapi.com/v1/dolares/oficial" to """{"promedio": 847.4442, "fechaActualizacion": "2026-09-17T00:00:00-04:00"}"""
            ),
            history = emptyList()
        )
        val (db, repo) = memoryRepository(fake)
        try {
            val first = repo.exchange!!.ensureFresh()
            assertEquals(n("847.4442"), first.displayedRate)
            assertEquals(1, fake.requests.count { it.contains("oficial") && !it.contains("/historic") })
            val second = repo.exchange!!.ensureFresh()
            assertEquals(n("847.4442"), second.displayedRate)
            assertEquals(1, fake.requests.count { it.contains("oficial") && !it.contains("/historic") })
        } finally { db.close() }
    }

    @Test fun refreshMonthRatesHealsZeroValuedMonthsFromCache() = runBlocking {
        val fake = FakeHttp(
            official = emptyMap(),
            history = listOf(
                """{"promedio": 100.0, "fecha": "2026-09-01"}""",
                """{"promedio": 250.0, "fecha": "2026-08-30"}"""
            )
        )
        val (db, repo) = memoryRepository(fake)
        try {
            repo.save(Budget("Septiembre", n("1000"), n("0"), n("0"), emptyList(), monthId = "2026-09"))
            repo.save(Budget("AGO", n("500"), n("0"), n("0"), emptyList(), monthId = "2026-08"))
            val before = repo.load("2026-09")!!
            assertEquals(n("0"), before.incomeRate)
            repo.exchange!!.backfillIfNeeded()
            val healed = repo.exchange!!.refreshMonthRates()
            assertEquals(2, healed)
            assertEquals(n("100.0"), repo.load("2026-09")!!.incomeRate)
            assertEquals(n("250.0"), repo.load("2026-08")!!.incomeRate)
        } finally { db.close() }
    }

    @Test fun creationalRateUsesHistoryWhenNoLocalEntries() = runBlocking {
        val fake = FakeHttp(
            official = mapOf(
                "https://ve.dolarapi.com/v1/dolares/oficial" to """{"promedio": 0, "fechaActualizacion": "2026-09-17T00:00:00-04:00"}"""
            ),
            history = listOf(
                """{"promedio": 400.0, "fecha": "2026-08-30"}"""
            )
        )
        val (db, repo) = memoryRepository(fake)
        try {
            repo.exchange!!.backfillIfNeeded()
            val template = budgetFor("2026-08")
            val created = repo.loadOrCreate("2026-08", template)
            assertEquals(n("400.0"), created.incomeRate)
        } finally { db.close() }
    }

    @Test fun carryForwardRolloverAssignedCorrectOpeningValues() = runBlocking {
        val (db, repo) = memoryRepository()
        try {
            val septemberId = "2024-01"
            val octoberId = "2024-02"
            
            // Sept: 1000 income, 10 rate, 200 cash exp -> 800 balance.
            // One debt: Visa 500 opening, 200 payment -> 300 remaining.
            // One credit purchase: 150 Bs.
            val sept = Budget(
                monthLabel = "Enero 2024",
                incomeBs = n("1000"),
                incomeRate = n("10"),
                conversionBs = n("0"),
                categories = listOf(
                    Category("CASH", true, listOf(Expense("e1", "Cash", n("200"), n("10"), 1704067200000L))), // 2024-01-01
                    Category("CREDIT", false, listOf(Expense("e2", "Credit", n("150"), n("10"), 1704067200000L)))
                ),
                debts = listOf(Debt("Visa", n("500"), n("200"))),
                monthId = septemberId
            )
            repo.save(sept)
            
            val template = budgetFor(octoberId)
            val oct = repo.loadOrCreate(octoberId, template)
            
            // 1. Balance Rollover: Sept closing (800) -> Oct opening (800).
            assertEquals(0, oct.openingBalanceBs.compareTo(n("800")))
            
            // 2. Debt Rollover: 
            // - Visa (300 remaining)
            // - Compras a crédito Enero (150)
            assertEquals(2, oct.debts.size)
            
            val visa = oct.debts.find { it.label == "Visa" }!!
            assertEquals(0, visa.openingBs.compareTo(n("300")))
            assertEquals(0, visa.paymentBs.compareTo(BigDecimal.ZERO))
            
            val creditRow = oct.debts.find { it.label.contains("Enero") }!!
            assertEquals(0, creditRow.openingBs.compareTo(n("150")))
        } finally { db.close() }
    }
}

class FakeHttp(
    official: Map<String, String>,
    history: List<String>
) : ExchangeRateHttp {
    private val official = official.toMap()
    private val historyRaw = history
    val requests = mutableListOf<String>()

    override fun get(url: String): String {
        requests.add(url)
        return when {
            url == "https://ve.dolarapi.com/v1/dolares/oficial" -> official.getValue(url)
            url == "https://ve.dolarapi.com/v1/historicos/dolares/oficial" -> "[${historyRaw.joinToString(",")}]"
            else -> throw UnsupportedOperationException(url)
        }
    }

    companion object {
        val EMPTY = FakeHttp(emptyMap(), emptyList())
    }
}
