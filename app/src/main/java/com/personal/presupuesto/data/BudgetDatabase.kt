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

    @Query("SELECT * FROM budgets ORDER BY monthId LIMIT 1")
    suspend fun firstBudget(): BudgetEntity?

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
    version = 2,
    exportSchema = false
)
abstract class BudgetDatabase : RoomDatabase() {
    abstract fun budgetDao(): BudgetDao

    companion object {
        fun create(context: Context): BudgetDatabase = Room.databaseBuilder(
            context,
            BudgetDatabase::class.java,
            "presupuesto.db"
        ).fallbackToDestructiveMigration().build()
    }
}

class BudgetRepository(private val database: BudgetDatabase) {
    private val dao = database.budgetDao()
    private val monthId = "september"

    suspend fun loadOrSeed(seed: Budget): Budget {
        if (dao.countBudgets() == 0) save(seed)
        return load() ?: error("No se pudo leer el presupuesto guardado")
    }

    suspend fun load(): Budget? {
        val record = dao.firstBudget() ?: return null
        val categories = dao.categories(record.monthId)
        val expenses = dao.expenses(record.monthId).groupBy { it.categoryName }
        return Budget(
            record.monthLabel,
            record.incomeBs.toBigDecimal(),
            record.incomeRate.toBigDecimal(),
            record.conversionBs.toBigDecimal(),
            categories.map { category ->
                Category(category.name, category.cashExpense, expenses[category.name].orEmpty().map {
                    Expense(it.id, it.label, it.amountBs.toBigDecimal(), it.rate.toBigDecimal(), it.timestamp)
                })
            },
            dao.debts(record.monthId).map { Debt(it.label, it.openingBs.toBigDecimal(), it.paymentBs.toBigDecimal()) }
        )
    }

    suspend fun save(budget: Budget) {
        database.withTransaction {
            dao.deleteCategories(monthId)
            dao.deleteExpenses(monthId)
            dao.deleteDebts(monthId)
            dao.insertBudget(BudgetEntity(monthId, budget.monthLabel, budget.incomeBs.toPlainString(), budget.incomeRate.toPlainString(), budget.conversionBs.toPlainString()))
            dao.insertCategories(budget.categories.mapIndexed { index, category -> CategoryEntity(monthId, category.name, category.cashExpense, index) })
            dao.insertExpenses(budget.categories.flatMap { category -> category.rows.mapIndexed { index, expense ->
                ExpenseEntity(monthId, expense.id, category.name, expense.label, expense.amountBs.toPlainString(), expense.rate.toPlainString(), expense.timestamp, index)
            } })
            dao.insertDebts(budget.debts.mapIndexed { index, debt -> DebtEntity(monthId, debt.label, debt.openingBs.toPlainString(), debt.paymentBs.toPlainString(), index) })
        }
    }

    suspend fun clearAllAndSeed(seed: Budget) {
        database.withTransaction {
            dao.deleteAllBudgets()
            dao.deleteAllCategories()
            dao.deleteAllExpenses()
            dao.deleteAllDebts()
            save(seed)
        }
    }

}