package com.personal.presupuesto

import java.math.BigDecimal
import java.math.MathContext

private val moneyContext = MathContext.DECIMAL128
fun convert(amount: BigDecimal, rate: BigDecimal): BigDecimal =
    if (rate.signum() == 0) BigDecimal.ZERO else amount.divide(rate, moneyContext)

data class Expense(
    val id: String,
    val label: String,
    val amountBs: BigDecimal,
    val rate: BigDecimal,
    val timestamp: Long = System.currentTimeMillis()
) {
    val amountUsd: BigDecimal get() = convert(amountBs, rate)
}
data class Category(val name: String, val cashExpense: Boolean, val rows: List<Expense>) {
    val totalBs: BigDecimal get() = rows.fold(BigDecimal.ZERO) { total, row -> total + row.amountBs }
    val totalUsd: BigDecimal get() = rows.fold(BigDecimal.ZERO) { total, row -> total + row.amountUsd }
}
data class Debt(val label: String, val openingBs: BigDecimal, val paymentBs: BigDecimal) {
    val remainingBs: BigDecimal get() = openingBs - paymentBs
}

data class Budget(
    val monthLabel: String,
    val incomeBs: BigDecimal,
    val incomeRate: BigDecimal,
    val conversionBs: BigDecimal,
    val categories: List<Category>,
    val debts: List<Debt> = emptyList()
) {
    val creditPurchasesBs: BigDecimal get() = categories.filter { !it.cashExpense }.fold(BigDecimal.ZERO) { a, c -> a + c.totalBs }
    val debtBs: BigDecimal get() = debts.fold(creditPurchasesBs) { total, debt -> total + debt.remainingBs }
    val debtUsd: BigDecimal? get() = if (incomeRate.signum() == 0) null else convert(debtBs, incomeRate)

    val incomeUsd: BigDecimal get() = convert(incomeBs, incomeRate)
    val cashBs: BigDecimal get() = categories.filter { it.cashExpense }.fold(BigDecimal.ZERO) { a, c -> a + c.totalBs }
    val cashUsd: BigDecimal get() = categories.filter { it.cashExpense }.fold(BigDecimal.ZERO) { a, c -> a + c.totalUsd }
    val balanceBs: BigDecimal get() = incomeBs - cashBs - conversionBs
    // September K6 is unguarded, unlike row conversion formulas. Show unavailable at zero rate.
    val balanceUsd: BigDecimal? get() = if (incomeRate.signum() == 0) null else convert(balanceBs, incomeRate)
}
