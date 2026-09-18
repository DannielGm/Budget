package com.personal.presupuesto

import android.content.Context
import org.json.JSONObject
import java.math.BigDecimal

object SeedLoader {
    fun load(context: Context): Budget {
        val root = context.assets.open("september.private.json").bufferedReader().use {
            JSONObject(it.readText())
        }
        require(root.getInt("schemaVersion") == 1)
        require(root.getString("sourceSheet") == "September")
        val categories = root.getJSONArray("categories")
        return Budget(
            root.getString("monthLabel"),
            root.getString("incomeBs").toBigDecimal(),
            root.getString("incomeRate").toBigDecimal(),
            (0 until categories.length()).map { i ->
                val category = categories.getJSONObject(i)
                val rows = category.getJSONArray("rows")
                Category(category.getString("name"), category.getBoolean("cashExpense"),
                    (0 until rows.length()).map { j ->
                        val row = rows.getJSONObject(j)
                        Expense(row.getString("id"), row.getString("label"),
                            row.getString("amountBs").toBigDecimal(), row.getString("rate").toBigDecimal())
                    })
            },
            root.getJSONArray("debts").let { debts ->
                (0 until debts.length()).map { i ->
                    val debt = debts.getJSONObject(i)
                    Debt(debt.getString("label"), debt.getString("openingBs").toBigDecimal(),
                        debt.getString("paymentBs").toBigDecimal())
                }
            },
            SEED_MONTH_ID
        )
    }
}
