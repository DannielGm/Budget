package com.personal.presupuesto

import java.math.BigDecimal
import java.math.MathContext
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

private val moneyContext = MathContext.DECIMAL128

// The workbook seed has no year column, so its month is pinned here.
const val SEED_MONTH_ID = "2026-09"

private val monthNames = listOf(
    "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
    "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
)

private fun dateKey(calendar: Calendar): Long =
    calendar.get(Calendar.YEAR).toLong() * 10_000 +
        (calendar.get(Calendar.MONTH) + 1).toLong() * 100 +
        calendar.get(Calendar.DAY_OF_MONTH).toLong()

// Material3 reports picker selections as the chosen day at 00:00 UTC.
private fun utcCalendar(timeInMillis: Long): Calendar =
    Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { this.timeInMillis = timeInMillis }

fun monthIdOf(timestamp: Long): String {
    val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
    return "%04d-%02d".format(Locale.US, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1)
}

// Read picker months in UTC: interpreting midnight as local time can select the previous month.
fun monthIdFromPicker(utcTimeMillis: Long): String {
    val calendar = utcCalendar(utcTimeMillis)
    return "%04d-%02d".format(Locale.US, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1)
}

fun pickerStartOfMonth(monthId: String): Long {
    require(Regex("[0-9]{4}-(0[1-9]|1[0-2])").matches(monthId)) { "Mes inválido" }
    return utcCalendar(0).apply {
        clear()
        set(monthId.substringBefore('-').toInt(), monthId.substringAfter('-').toInt() - 1, 1)
    }.timeInMillis
}

@Suppress("UNUSED_PARAMETER") // Retained during the calendar UI migration.
fun isSelectableBudgetDay(utcTimeMillis: Long, storedMonthIds: Set<String>): Boolean =
    isSelectableDay(utcTimeMillis)

fun currentMonthId(): String = monthIdOf(System.currentTimeMillis())

fun isFutureMonth(monthId: String): Boolean {
    require(Regex("[0-9]{4}-(0[1-9]|1[0-2])").matches(monthId)) { "Mes inválido" }
    return monthId > currentMonthId()
}

fun parseExpenseTimestamp(text: String): Long? {
    val position = java.text.ParsePosition(0)
    val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { isLenient = false }
    val parsed = format.parse(text, position) ?: return null
    return parsed.time.takeIf { position.index == text.length }
}

fun localDateFromPicker(utcTimeMillis: Long, time: Long = 0L): Long {
    val day = utcCalendar(utcTimeMillis)
    return Calendar.getInstance().apply {
        timeInMillis = time
        set(Calendar.YEAR, day.get(Calendar.YEAR))
        set(Calendar.MONTH, day.get(Calendar.MONTH))
        set(Calendar.DAY_OF_MONTH, day.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

fun pickerDateFromLocal(timestamp: Long): Long {
    val local = Calendar.getInstance().apply { timeInMillis = timestamp }
    return utcCalendar(0).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

fun monthName(monthId: String): String {
    val month = monthId.substringAfterLast('-').toIntOrNull() ?: return monthId
    return monthNames.getOrNull(month - 1) ?: monthId
}

// The UI shows month names; the year only appears when it is not the current one.
fun monthDisplayName(monthId: String): String {
    val year = monthId.substringBefore('-')
    return if (year == currentMonthId().substringBefore('-')) monthName(monthId) else "${monthName(monthId)} $year"
}

fun currentLocalDateKey(): Long =
    dateKey(Calendar.getInstance().apply { timeInMillis = System.currentTimeMillis() })

fun pickerDateKey(utcTimeMillis: Long): Long = dateKey(utcCalendar(utcTimeMillis))

fun isSelectableDay(utcTimeMillis: Long): Boolean = pickerDateKey(utcTimeMillis) <= currentLocalDateKey()

fun isPastOrPresentYear(year: Int): Boolean = year <= Calendar.getInstance().get(Calendar.YEAR)

fun isPastOrPresentTimestamp(timestamp: Long): Boolean =
    dateKey(Calendar.getInstance().apply { timeInMillis = timestamp }) <= currentLocalDateKey()
fun convert(amount: BigDecimal, rate: BigDecimal): BigDecimal =
    if (rate.signum() == 0) BigDecimal.ZERO else amount.divide(rate, moneyContext)

private fun Long.sameDayAs(other: Long): Boolean {
    val first = Calendar.getInstance().apply { timeInMillis = this@sameDayAs }
    val second = Calendar.getInstance().apply { timeInMillis = other }
    return first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
        first.get(Calendar.MONTH) == second.get(Calendar.MONTH) &&
        first.get(Calendar.DAY_OF_MONTH) == second.get(Calendar.DAY_OF_MONTH)
}

data class Expense(
    val id: String,
    val label: String,
    val amountBs: BigDecimal,
    val rate: BigDecimal,
    val timestamp: Long = System.currentTimeMillis()
) {
    val amountUsd: BigDecimal get() = convert(amountBs, rate)

    // Phase 4.3: conversions always use the caller's context rate (the month's first BCV
    // entry in month view, the picked day's BCV rate in day view). The stored per-row rate
    // is kept only as a historical record and no longer drives conversions.
    fun amountUsdAt(contextRate: BigDecimal): BigDecimal = convert(amountBs, contextRate)
}
data class Category(val name: String, val cashExpense: Boolean, val rows: List<Expense>) {
    val totalBs: BigDecimal get() = rows.fold(BigDecimal.ZERO) { total, row -> total + row.amountBs }
    val totalUsd: BigDecimal get() = rows.fold(BigDecimal.ZERO) { total, row -> total + row.amountUsd }
    fun totalUsdAt(defaultRate: BigDecimal): BigDecimal = rows.fold(BigDecimal.ZERO) { total, row -> total + row.amountUsdAt(defaultRate) }
}
data class Debt(val label: String, val openingBs: BigDecimal, val paymentBs: BigDecimal) {
    val remainingBs: BigDecimal get() = openingBs - paymentBs
}

data class Budget(
    val monthLabel: String,
    val incomeBs: BigDecimal,
    val incomeRate: BigDecimal,
    val categories: List<Category>,
    val debts: List<Debt> = emptyList(),
    val monthId: String,
    // Carried from the previous month's closing balance; editable in the budget editor.
    val openingBalanceBs: BigDecimal = BigDecimal.ZERO
) {
    val creditPurchasesBs: BigDecimal get() = categories.filter { !it.cashExpense }.fold(BigDecimal.ZERO) { a, c -> a + c.totalBs }
    val debtBs: BigDecimal get() = debts.fold(creditPurchasesBs) { total, debt -> total + debt.remainingBs }
    val debtUsd: BigDecimal? get() = if (incomeRate.signum() == 0) null else convert(debtBs, incomeRate)

    val incomeUsd: BigDecimal get() = convert(incomeBs, incomeRate)
    val cashBs: BigDecimal get() = categories.filter { it.cashExpense }.fold(BigDecimal.ZERO) { a, c -> a + c.totalBs }
    val cashUsd: BigDecimal get() = categories.filter { it.cashExpense }.fold(BigDecimal.ZERO) { a, c -> a + c.totalUsdAt(incomeRate) }
    val balanceBs: BigDecimal get() = openingBalanceBs + incomeBs - cashBs
    // September K6 is unguarded, unlike row conversion formulas. Show unavailable at zero rate.
    val balanceUsd: BigDecimal? get() = if (incomeRate.signum() == 0) null else convert(balanceBs, incomeRate)

    // Context-rate variants: day views divide by the picked day's BCV rate instead of the month's.
    fun cashUsdAt(rate: BigDecimal): BigDecimal = convert(cashBs, rate)
    fun debtUsdAt(rate: BigDecimal): BigDecimal? = if (rate.signum() == 0) null else convert(debtBs, rate)
    fun balanceUsdAt(rate: BigDecimal): BigDecimal? = if (rate.signum() == 0) null else convert(balanceBs, rate)

    fun filteredCategories(dateFilter: Long?, rangeDays: Int = 1): List<Category> {
        if (dateFilter == null) return categories
        val start = Calendar.getInstance().apply {
            timeInMillis = dateFilter
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (rangeDays >= 30) {
                set(Calendar.DAY_OF_MONTH, 1)
            } else {
                set(Calendar.DAY_OF_MONTH, (get(Calendar.DAY_OF_MONTH) - rangeDays.coerceAtLeast(1) + 1).coerceAtLeast(1))
            }
        }.timeInMillis
        val end = Calendar.getInstance().apply {
            timeInMillis = dateFilter
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (rangeDays >= 30) {
                set(Calendar.DAY_OF_MONTH, 1)
                add(Calendar.MONTH, 1)
            } else {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }.timeInMillis
        return categories.map { category ->
            category.copy(rows = category.rows.filter { expense ->
                if (rangeDays <= 1) expense.timestamp.sameDayAs(dateFilter)
                else expense.timestamp >= start && expense.timestamp < end
            })
        }.filter { it.rows.isNotEmpty() }
    }
}
