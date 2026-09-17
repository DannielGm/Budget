package com.personal.presupuesto

import android.content.Context
import androidx.room.Room
import com.personal.presupuesto.data.BudgetDatabase
import com.personal.presupuesto.data.BudgetRepository
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
                Category("HOGAR", true, listOf(Expense("1", "Agua", n("500"), n("0"))))
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
        assertEquals(0, budget.cashBs.compareTo(n("200")))
        assertEquals(0, budget.cashUsd.compareTo(n("10")))
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

    private fun memoryRepository(): Pair<BudgetDatabase, BudgetRepository> {
        val database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), BudgetDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        return database to BudgetRepository(database)
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
            assertEquals(currentMonthId(), opened.monthId)
            assertEquals(BigDecimal.ZERO, opened.incomeBs)
            assertEquals(BigDecimal.ZERO, opened.incomeRate)
            assertEquals(BigDecimal.ZERO, opened.conversionBs)
            assertEquals(listOf(Category("HOGAR", true, emptyList())), opened.categories)
            assertTrue(opened.debts.isEmpty())
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

    @Test fun budgetCalendarAllowsOnlyStoredMonthsAndCurrentPastOrPresentDays() {
        val past = monthIdMonthsAhead(-1)
        assertTrue(isSelectableBudgetDay(pickerStartOfMonth(past), setOf(past)))
        assertFalse(isSelectableBudgetDay(pickerStartOfMonth(past), emptySet()))
        assertTrue(isSelectableBudgetDay(pickerStartOfMonth(currentMonthId()), emptySet()))
        assertTrue(isSelectableBudgetDay(utcMidnightOfLocalDate(0), emptySet()))
        assertFalse(isSelectableBudgetDay(utcMidnightOfLocalDate(1), setOf(currentMonthId())))
        val future = monthIdMonthsAhead(1)
        assertFalse(isSelectableBudgetDay(pickerStartOfMonth(future), setOf(future)))
    }

    @Test fun typedDatesAreStrictAndPickerPreservesLocalDay() {
        assertEquals(null, parseExpenseTimestamp("2026-02-30 12:00"))
        assertEquals(null, parseExpenseTimestamp("not a date"))
        assertEquals(null, parseExpenseTimestamp("2026-09-01 12:00garbage"))
        val date = parseExpenseTimestamp("2026-09-01 12:00")!!
        assertEquals(date, localDateFromPicker(pickerDateFromLocal(date), date))
    }

    @Test fun datesAfterTodayAreNeverAllowed() {
        val threeDaysAhead = 3L * 24 * 60 * 60 * 1000

        assertTrue(isPastOrPresentTimestamp(System.currentTimeMillis()))
        assertTrue(isPastOrPresentTimestamp(System.currentTimeMillis() - 10L * 24 * 60 * 60 * 1000))
        assertFalse(isPastOrPresentTimestamp(System.currentTimeMillis() + threeDaysAhead))

        assertTrue(isSelectableDay(utcMidnightOfLocalDate(0)))
        assertFalse(isSelectableDay(utcMidnightOfLocalDate(0) + threeDaysAhead))

        assertTrue(isPastOrPresentYear(JavaCalendar.getInstance().get(JavaCalendar.YEAR)))
        assertFalse(isPastOrPresentYear(JavaCalendar.getInstance().get(JavaCalendar.YEAR) + 1))
    }
}
