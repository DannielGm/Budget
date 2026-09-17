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
import com.personal.presupuesto.monthIdOf
import com.personal.presupuesto.isPastOrPresentTimestamp
import java.math.BigDecimal

@Entity(tableName = "budgets")
data class BudgetEntity(
    @androidx.room.PrimaryKey val monthId: String,
    val monthLabel: String,
    val incomeBs: String,
    val incomeRate: String,
    val conversionBs: String
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
    suspend fun deleteAllDebts(): Unit}

@Database(
    entities = [BudgetEntity::class, CategoryEntity::class, ExpenseEntity::class, DebtEntity::class],
    // v3 moves month ids from the legacy "september" form to sortable YYYY-MM values.
    version = 3,
    exportSchema = false
)
abstract class BudgetDatabase : RoomDatabase() {
    abstract fun budgetDao(): BudgetDao

    companion object {
        fun create(context: Context): BudgetDatabase = Room.databaseBuilder(
            context,
            BudgetDatabase::class.java,
            "presupuesto.db"
        ).fallbackToDestructiveMigration(dropAllTables = true).build()
    }
}

data class MonthSummary(val monthId: String, val label: String)

class BudgetRepository(private val database: BudgetDatabase) {
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

    // Months exist only because data was recorded for them, so a missing one starts empty.
    // Categories are carried over by name and type; amounts, debts, and income stay at zero.
    suspend fun loadOrCreate(monthId: String, template: Budget): Budget {
        require(!isFutureMonth(monthId)) { "No se pueden crear meses futuros" }
        load(monthId)?.let { return it }
        val blank = template.copy(
            monthId = monthId,
            monthLabel = monthDisplayName(monthId),
            incomeBs = BigDecimal.ZERO,
            incomeRate = BigDecimal.ZERO,
            conversionBs = BigDecimal.ZERO,
            categories = template.categories.map { it.copy(rows = emptyList()) },
            debts = emptyList()
        )
        save(blank)
        return blank
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
            record.monthId
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
            dao.insertBudget(BudgetEntity(monthId, monthLabel, budget.incomeBs.toPlainString(), budget.incomeRate.toPlainString(), budget.conversionBs.toPlainString()))
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
            categories = current.categories.map { it.copy(rows = emptyList()) }, debts = emptyList()
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