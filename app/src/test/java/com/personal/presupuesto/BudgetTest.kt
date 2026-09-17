package com.personal.presupuesto

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class BudgetTest {
    private fun n(value: String) = BigDecimal(value)
    @Test fun zeroRowRateReturnsZero() {
        assertEquals(0, convert(n("500"), n("0")).compareTo(BigDecimal.ZERO))
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
}
