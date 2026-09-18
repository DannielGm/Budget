package com.personal.presupuesto.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.personal.presupuesto.Budget
import com.personal.presupuesto.Category
import com.personal.presupuesto.Debt
import com.personal.presupuesto.Expense
import com.personal.presupuesto.isFutureMonth
import com.personal.presupuesto.monthDisplayName
import com.personal.presupuesto.monthName
import com.personal.presupuesto.monthIdOf
import com.personal.presupuesto.isPastOrPresentTimestamp
import java.math.BigDecimal

@Entity(tableName = "budgets")
data class BudgetEntity(
    @androidx.room.PrimaryKey val monthId: String,
    val monthLabel: String,
    val incomeBs: String,
    val incomeRate: String,
    val conversionBs: String,
    val openingBalanceBs: String
)

@Entity(primaryKeys = ["monthId", "name"], tableName = "categories")
data class CategoryEntity(
    val monthId: String,
    val name: String,
    val cashExpense: Boolean,
    val position: Int
)

@Entity(primaryKeys = ["monthId", "id"], tableName = "expenses")
data class ExpenseEntity(
    val monthId: String,
    val id: String,
    val categoryName: String,
    val label: String,
    val amountBs: String,
    val rate: String,
    val timestamp: Long,
    val position: Int
)

@Entity(primaryKeys = ["monthId", "label"], tableName = "debts")
data class DebtEntity(
    val monthId: String,
    val label: String,
    val openingBs: String,
    val paymentBs: String,
    val position: Int
)

// One row per BCV publication day ("yyyy-MM-dd"). Dates without their own entry resolve
// to the latest previous one (weekends and holidays follow the last business day).
@Entity(tableName = "exchange_rates")
data class ExchangeRateEntity(
    @androidx.room.PrimaryKey val dateIso: String,
    val rateBsPerUsd: String,
    val fetchedAt: Long
)

@Dao
interface BudgetDao {
    @Query("SELECT COUNT(*) FROM budgets")
    suspend fun countBudgets(): Int

    @Query("SELECT * FROM budgets ORDER BY monthId ASC")
    suspend fun budgets(): List<BudgetEntity>

    @Query("SELECT * FROM budgets WHERE monthId = :monthId LIMIT 1")
    suspend fun budget(monthId: String): BudgetEntity?

    @Query("SELECT * FROM categories WHERE monthId = :monthId ORDER BY position")
    suspend fun categories(monthId: String): List<CategoryEntity>

    @Query("SELECT * FROM expenses WHERE monthId = :monthId ORDER BY categoryName, position")
    suspend fun expenses(monthId: String): List<ExpenseEntity>

    @Query("SELECT * FROM debts WHERE monthId = :monthId ORDER BY position")
    suspend fun debts(monthId: String): List<DebtEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudget(budget: BudgetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpenses(expenses: List<ExpenseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDebts(debts: List<DebtEntity>)

    @Query("DELETE FROM categories WHERE monthId = :monthId")
    suspend fun deleteCategories(monthId: String)

    @Query("DELETE FROM expenses WHERE monthId = :monthId")
    suspend fun deleteExpenses(monthId: String)

    @Query("DELETE FROM debts WHERE monthId = :monthId")
    suspend fun deleteDebts(monthId: String)
    @Query("DELETE FROM budgets")
    suspend fun deleteAllBudgets(): Unit

    @Query("DELETE FROM categories")
    suspend fun deleteAllCategories(): Unit

    @Query("DELETE FROM expenses")
    suspend fun deleteAllExpenses(): Unit

    @Query("DELETE FROM debts")
    suspend fun deleteAllDebts(): Unit

    @Query("SELECT * FROM exchange_rates WHERE dateIso <= :dateIso ORDER BY dateIso DESC LIMIT 1")
    suspend fun rateOnOrBefore(dateIso: String): ExchangeRateEntity?

    @Query("SELECT * FROM exchange_rates WHERE dateIso < :dateIso ORDER BY dateIso DESC LIMIT 1")
    suspend fun rateBefore(dateIso: String): ExchangeRateEntity?

    @Query("SELECT * FROM exchange_rates WHERE dateIso >= :dateIso ORDER BY dateIso ASC LIMIT 1")
    suspend fun rateOnOrAfter(dateIso: String): ExchangeRateEntity?

    @Query("SELECT dateIso FROM exchange_rates")
    suspend fun rateDates(): List<String>

    @Query("SELECT COUNT(*) FROM exchange_rates")
    suspend fun countRates(): Int

    @Query("SELECT * FROM budgets WHERE monthId < :monthId ORDER BY monthId DESC LIMIT 1")
    suspend fun newestBudgetBefore(monthId: String): BudgetEntity?

    @Query("UPDATE budgets SET incomeRate = :rate WHERE monthId = :monthId")
    suspend fun updateIncomeRate(monthId: String, rate: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExchangeRates(rates: List<ExchangeRateEntity>)
}

@Database(
    entities = [BudgetEntity::class, CategoryEntity::class, ExpenseEntity::class, DebtEntity::class, ExchangeRateEntity::class],
    // v3 moves month ids to sortable YYYY-MM values.
    // v4 adds the carried opening balance and the BCV exchange-rate cache, non-destructively.
    version = 4,
    exportSchema = false
)
abstract class BudgetDatabase : RoomDatabase() {
    abstract fun budgetDao(): BudgetDao

    companion object {
        // v3 → v4 is non-destructive on purpose: stored months, their history, and the
        // September seed survive the upgrade. Existing month rates keep their stored value
        // until the BCV refresh repairs them.
        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE budgets ADD COLUMN openingBalanceBs TEXT NOT NULL DEFAULT '0'")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS exchange_rates (" +
                        "dateIso TEXT NOT NULL PRIMARY KEY, " +
                        "rateBsPerUsd TEXT NOT NULL, " +
                        "fetchedAt INTEGER NOT NULL)"
                )
            }
        }

        fun create(context: Context): BudgetDatabase = Room.databaseBuilder(
            context,
            BudgetDatabase::class.java,
            "presupuesto.db"
        ).addMigrations(MIGRATION_3_4).build()
    }
}

data class MonthSummary(val monthId: String, val label: String)

class BudgetRepository(
    private val database: BudgetDatabase,
    // Optional: tests and offline paths keep working without BCV coverage.
    val exchange: ExchangeRateRepository? = null
) {
    private val dao = database.budgetDao()

    suspend fun listMonthIds(): List<String> = dao.budgets().map { it.monthId }

    // The DAO orders YYYY-MM ids chronologically, so the newest month is last.
    suspend fun listMonths(): List<MonthSummary> =
        dao.budgets().map { MonthSummary(it.monthId, monthDisplayName(it.monthId)) }

    suspend fun loadOrSeed(seed: Budget): Budget {
        val existing = dao.budgets().lastOrNull()
        if (existing != null) return load(existing.monthId) ?: error("No se pudo leer el presupuesto guardado")
        save(seed)
        return load(seed.monthId) ?: error("No se pudo leer el presupuesto guardado")
    }

    // Startup always opens the local current month, regardless of the last viewed month.
    suspend fun loadCurrentMonth(seed: Budget): Budget = database.withTransaction {
        val template = loadOrSeed(seed)
        loadOrCreate(com.personal.presupuesto.currentMonthId(), template)
    }

    // Historical months are created by confirmed calendar navigation or recorded data.
    // Startup also creates the current month when missing.
    // Categories are carried over by name and type; amounts, debts, and income stay at zero.
    suspend fun loadOrCreate(monthId: String, template: Budget): Budget {
        require(!isFutureMonth(monthId)) { "No se pueden crear meses futuros" }
        load(monthId)?.let { return it }
        // Phase 4.3 carry-forward: a newly created month starts from the newest stored
        // month's closing cash balance and its rolled debt, at the month's BCV rate.
        // This fires only at creation; reloading an existing month never re-carries.
        val predecessor = dao.newestBudgetBefore(monthId)?.let { load(it.monthId) }
        val blank = template.copy(
            monthId = monthId,
            monthLabel = monthDisplayName(monthId),
            incomeBs = BigDecimal.ZERO,
            incomeRate = exchange?.monthRateFor(monthId) ?: BigDecimal.ZERO,
            conversionBs = BigDecimal.ZERO,
            categories = template.categories.map { it.copy(rows = emptyList()) },
            debts = carryDebtsFrom(predecessor),
            openingBalanceBs = predecessor?.balanceBs ?: BigDecimal.ZERO
        )
        save(blank)
        return blank
    }

    // Rolls the predecessor month's debt into the new month: per-source remainders plus a
    // traceable row for its credit purchases (the app tracks purchases aggregated, not per card).
    private fun carryDebtsFrom(predecessor: Budget?): List<Debt> {
        predecessor ?: return emptyList()
        val rolled = predecessor.debts.map { Debt(it.label, it.remainingBs, BigDecimal.ZERO) }
        val purchases = predecessor.creditPurchasesBs
        return if (purchases.signum() > 0) {
            rolled + Debt("Compras a crédito ${monthName(predecessor.monthId)}", purchases, BigDecimal.ZERO)
        } else rolled
    }

    suspend fun load(monthId: String): Budget? {
        val record = dao.budget(monthId) ?: return null
        val categories = dao.categories(record.monthId)
        val expenses = dao.expenses(record.monthId).groupBy { it.categoryName }
        return Budget(
            monthDisplayName(record.monthId),
            record.incomeBs.toBigDecimal(),
            record.incomeRate.toBigDecimal(),
            record.conversionBs.toBigDecimal(),
            categories.map { category ->
                Category(category.name, category.cashExpense, expenses[category.name].orEmpty().map {
                    Expense(it.id, it.label, it.amountBs.toBigDecimal(), it.rate.toBigDecimal(), it.timestamp)
                })
            },
            dao.debts(record.monthId).map { Debt(it.label, it.openingBs.toBigDecimal(), it.paymentBs.toBigDecimal()) },
            record.monthId,
            record.openingBalanceBs.toBigDecimal()
        )
    }

    suspend fun save(budget: Budget) {
        val monthId = budget.monthId
        require(!isFutureMonth(monthId)) { "No se pueden guardar meses futuros" }
        require(budget.categories.all { category -> category.rows.all { isPastOrPresentTimestamp(it.timestamp) } }) {
            "No se permiten fechas futuras"
        }
        val monthLabel = monthDisplayName(monthId)
        database.withTransaction {
            dao.deleteCategories(monthId)
            dao.deleteExpenses(monthId)
            dao.deleteDebts(monthId)
            dao.insertBudget(BudgetEntity(monthId, monthLabel, budget.incomeBs.toPlainString(), budget.incomeRate.toPlainString(), budget.conversionBs.toPlainString(), budget.openingBalanceBs.toPlainString()))
            dao.insertCategories(budget.categories.mapIndexed { index, category -> CategoryEntity(monthId, category.name, category.cashExpense, index) })
            dao.insertExpenses(budget.categories.flatMap { category -> category.rows.mapIndexed { index, expense ->
                ExpenseEntity(monthId, expense.id, category.name, expense.label, expense.amountBs.toPlainString(), expense.rate.toPlainString(), expense.timestamp, index)
            } })
            dao.insertDebts(budget.debts.mapIndexed { index, debt -> DebtEntity(monthId, debt.label, debt.openingBs.toPlainString(), debt.paymentBs.toPlainString(), index) })
        }
    }

    suspend fun recordExpense(sourceMonthId: String, categoryName: String, expense: Expense): Budget =
        database.withTransaction {
            require(isPastOrPresentTimestamp(expense.timestamp)) { "No se permiten fechas futuras" }
            val source = load(sourceMonthId) ?: error("Mes no encontrado")
            val category = source.categories.first { it.name == categoryName }
            val targetId = monthIdOf(expense.timestamp)
            val target = loadOrCreate(targetId, source)
            if (targetId != sourceMonthId) {
                save(source.copy(categories = source.categories.map {
                    it.copy(rows = it.rows.filterNot { row -> row.id == expense.id })
                }))
            }
            val categories = if (target.categories.any { it.name == categoryName }) target.categories
                else target.categories + category.copy(rows = emptyList())
            val updated = target.copy(categories = categories.map {
                if (it.name == categoryName) it.copy(rows = it.rows.filterNot { row -> row.id == expense.id } + expense)
                else it
            })
            save(updated)
            updated
        }

    suspend fun clearMonth(monthId: String): Budget = database.withTransaction {
        val current = load(monthId) ?: error("Mes no encontrado")
        val cleared = current.copy(
            incomeBs = BigDecimal.ZERO, incomeRate = BigDecimal.ZERO, conversionBs = BigDecimal.ZERO,
            categories = current.categories.map { it.copy(rows = emptyList()) }, debts = emptyList(),
            openingBalanceBs = BigDecimal.ZERO
        )
        save(cleared)
        cleared
    }

    suspend fun clearAllKeepingMonth(monthId: String): Budget = database.withTransaction {
        val cleared = clearMonth(monthId)
        clearAll()
        save(cleared)
        cleared
    }

    suspend fun clearAll() {
        database.withTransaction {
            dao.deleteAllExpenses()
            dao.deleteAllDebts()
            dao.deleteAllCategories()
            dao.deleteAllBudgets()
        }
    }

}