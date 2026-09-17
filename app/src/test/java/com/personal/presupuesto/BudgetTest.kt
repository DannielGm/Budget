package com.personal.presupuesto

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

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
            )
        )

        assertEquals(0, budget.cashUsd.compareTo(n("50")))
        assertEquals(0, budget.categories.first().totalUsdAt(n("10")).compareTo(n("50")))
    }
    @Test fun cashBalanceExcludesCreditAndDeductsConversions() {
        val budget = Budget("Septiembre", n("1000"), n("10"), n("100"), listOf(
            Category("HOGAR", true, listOf(Expense("1", "Internet", n("200"), n("20")))),
            Category("GASTOS PERSONALES", false, listOf(Expense("2", "Compra", n("300"), n("10"))))
        ))
        assertEquals(0, budget.cashBs.compareTo(n("200")))
        assertEquals(0, budget.cashUsd.compareTo(n("10")))
        assertEquals(0, budget.balanceBs.compareTo(n("700")))
        assertEquals(0, budget.balanceUsd!!.compareTo(n("70")))
    }
    @Test fun debtIncludesUnpaidOpeningBalancesAndCreditPurchases() {
        val budget = Budget("Septiembre", n("1000"), n("10"), n("0"), listOf(
            Category("GASTOS PERSONALES", false, listOf(Expense("1", "Compra", n("300"), n("10"))))
        ), listOf(Debt("Visa", n("500"), n("200"))))
        assertEquals(0, budget.debtBs.compareTo(n("600")))
        assertEquals(0, budget.debtUsd!!.compareTo(n("60")))
    }
    @Test fun zeroIncomeRateMakesBalanceUnavailable() {
        val budget = Budget("Septiembre", n("100"), n("0"), n("0"), emptyList())
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
            )
        )
        val filtered = budget.filteredCategories(day + 12L * 60 * 60 * 1000)
        assertEquals(1, filtered.size)
        assertEquals(2, filtered.first().rows.size)
    }
}
