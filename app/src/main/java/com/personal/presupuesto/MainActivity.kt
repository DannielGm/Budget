package com.personal.presupuesto

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.personal.presupuesto.data.BudgetDatabase
import com.personal.presupuesto.data.BudgetRepository
import com.personal.presupuesto.ui.theme.PresupuestoTheme
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun BigDecimal.display(): String = setScale(2, RoundingMode.HALF_UP).toPlainString()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = BudgetRepository(BudgetDatabase.create(applicationContext))
        setContent { PresupuestoTheme { BudgetApp(repository, applicationContext) } }
    }
}

@Composable
private fun BudgetApp(repository: BudgetRepository, context: android.content.Context) {
    var budget by remember { mutableStateOf<Budget?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var editingBudget by remember { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<Pair<String, Expense>?>(null) }
    var addingToCategory by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun persist(updated: Budget) {
        budget = updated
        saving = true
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { repository.save(updated) } }
                .onFailure { error = "No se pudo guardar el cambio." }
            saving = false
        }
    }

    LaunchedEffect(Unit) {
        runCatching {
            val seed = withContext(Dispatchers.IO) { SeedLoader.load(context) }
            withContext(Dispatchers.IO) { repository.loadOrSeed(seed) }
        }.onSuccess { budget = it }
            .onFailure { error = "No se pudo cargar el presupuesto." }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when {
            error != null && budget == null -> ErrorState(error!!)
            budget == null -> LoadingState()
            else -> BudgetScreen(
                budget = budget!!,
                saving = saving,
                onEditBudget = { editingBudget = true },
                onAddExpense = { addingToCategory = it },
                onEditExpense = { category, expense -> editingExpense = category to expense },
                onDeleteExpense = { categoryName, expense -> persist(budget!!.withoutExpense(categoryName, expense.id)) }
            )
        }
    }

    budget?.let { current ->
        if (editingBudget) {
            BudgetEditorDialog(current, onDismiss = { editingBudget = false }) {
                editingBudget = false
                persist(it)
            }
        }
        editingExpense?.let { (category, expense) ->
            ExpenseEditorDialog(category, expense, onDismiss = { editingExpense = null }) {
                editingExpense = null
                persist(current.replaceExpense(category, it))
            }
        }
        addingToCategory?.let { category ->
            ExpenseEditorDialog(category, null, onDismiss = { addingToCategory = null }) {
                addingToCategory = null
                persist(current.addExpense(category, it))
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text("Cargando tu presupuesto", style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ErrorState(message: String) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Algo salió mal", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(message)
    }
}

@Composable
private fun BudgetScreen(
    budget: Budget,
    saving: Boolean,
    onEditBudget: () -> Unit,
    onAddExpense: (String) -> Unit,
    onEditExpense: (String, Expense) -> Unit,
    onDeleteExpense: (String, Expense) -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize().safeDrawingPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Presupuesto personal", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(budget.monthLabel, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("Tu mes, en números claros", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (saving) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                else TextButton(onClick = onEditBudget) { Text("Editar") }
            }
        }
        item { BalanceCard(budget, onEditBudget) }
        item { MetricsRow(budget) }
        item { DebtCard(budget) }
        item { Text("Gastos por categoría", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp)) }
        items(budget.categories, key = { it.name }) { category ->
            CategoryCard(category, onAddExpense, onEditExpense, onDeleteExpense)
        }
    }
}

@Composable
private fun BalanceCard(budget: Budget, onEdit: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Saldo disponible", color = Color.White.copy(alpha = .78f), style = MaterialTheme.typography.labelLarge)
            Text("Bs ${budget.balanceBs.display()}", color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("$ ${budget.balanceUsd?.display() ?: "No disponible"}", color = Color.White.copy(alpha = .86f), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(4.dp))
            OutlinedButton(onClick = onEdit) { Text("Ajustar ingresos y conversiones") }
        }
    }
}

@Composable
private fun MetricsRow(budget: Budget) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MetricCard("Ingresos", "Bs ${budget.incomeBs.display()}", Modifier.weight(1f))
        MetricCard("Gastos de caja", "Bs ${budget.cashBs.display()}", Modifier.weight(1f))
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(5.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DebtCard(budget: Budget) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Deuda", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Bs ${budget.debtBs.display()}", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
            Text("Incluye compras a crédito del mes", color = MaterialTheme.colorScheme.onSurfaceVariant)
            budget.debts.forEach { debt -> Text("${debt.label}: Bs ${debt.remainingBs.display()}") }
        }
    }
}

@Composable
private fun CategoryCard(
    category: Category,
    onAdd: (String) -> Unit,
    onEdit: (String, Expense) -> Unit,
    onDelete: (String, Expense) -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(category.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(if (category.cashExpense) "Gasto de caja" else "Compra a crédito", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { onAdd(category.name) }) { Text("Agregar") }
            }
            category.rows.forEach { expense ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(expense.label, style = MaterialTheme.typography.bodyLarge)
                        Text("Bs ${expense.amountBs.display()}  ·  $ ${expense.amountUsd.display()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { onEdit(category.name, expense) }) { Text("Editar") }
                    TextButton(onClick = { onDelete(category.name, expense) }) { Text("Borrar") }
                }
            }
            HorizontalDivider()
            Text("Total  Bs ${category.totalBs.display()}  ·  $ ${category.totalUsd.display()}", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun BudgetEditorDialog(budget: Budget, onDismiss: () -> Unit, onSave: (Budget) -> Unit) {
    var income by remember { mutableStateOf(budget.incomeBs.toPlainString()) }
    var rate by remember { mutableStateOf(budget.incomeRate.toPlainString()) }
    var conversion by remember { mutableStateOf(budget.conversionBs.toPlainString()) }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Editar resumen") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MoneyField("Ingreso en Bs", income) { income = it }
            MoneyField("Tasa del ingreso", rate) { rate = it }
            MoneyField("Conversión en Bs", conversion) { conversion = it }
            if (invalid) Text("Usa números válidos y valores no negativos.", color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { Button(onClick = {
        val values = listOf(income, rate, conversion).mapNotNull { it.toBigDecimalOrNull() }
        if (values.size == 3 && values.all { it.signum() >= 0 }) onSave(budget.copy(incomeBs = values[0], incomeRate = values[1], conversionBs = values[2])) else invalid = true
    }) { Text("Guardar") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
private fun ExpenseEditorDialog(category: String, expense: Expense?, onDismiss: () -> Unit, onSave: (Expense) -> Unit) {
    var label by remember { mutableStateOf(expense?.label.orEmpty()) }
    var amount by remember { mutableStateOf(expense?.amountBs?.toPlainString().orEmpty()) }
    var rate by remember { mutableStateOf(expense?.rate?.toPlainString().orEmpty()) }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (expense == null) "Nuevo gasto" else "Editar gasto") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(category, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            OutlinedTextField(label = { Text("Descripción") }, value = label, onValueChange = { label = it }, singleLine = true)
            MoneyField("Monto en Bs", amount) { amount = it }
            MoneyField("Tasa", rate) { rate = it }
            if (invalid) Text("Completa la descripción y usa valores válidos.", color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { Button(onClick = {
        val parsedAmount = amount.toBigDecimalOrNull()
        val parsedRate = rate.toBigDecimalOrNull()
        if (label.isNotBlank() && parsedAmount != null && parsedRate != null && parsedAmount.signum() >= 0 && parsedRate.signum() >= 0) onSave(Expense(expense?.id ?: UUID.randomUUID().toString(), label.trim(), parsedAmount, parsedRate)) else invalid = true
    }) { Text("Guardar") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
private fun MoneyField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(label = { Text(label) }, value = value, onValueChange = onValueChange, singleLine = true, modifier = Modifier.fillMaxWidth())
}

private fun Budget.replaceExpense(categoryName: String, replacement: Expense): Budget = copy(categories = categories.map { category ->
    if (category.name == categoryName) category.copy(rows = category.rows.map { if (it.id == replacement.id) replacement else it }) else category
})

private fun Budget.addExpense(categoryName: String, expense: Expense): Budget = copy(categories = categories.map { category ->
    if (category.name == categoryName) category.copy(rows = category.rows + expense) else category
})

private fun Budget.withoutExpense(categoryName: String, expenseId: String): Budget = copy(categories = categories.map { category ->
    if (category.name == categoryName) category.copy(rows = category.rows.filterNot { it.id == expenseId }) else category
})
