package com.personal.presupuesto

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
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
import java.util.Random

private fun BigDecimal.display(): String = setScale(2, RoundingMode.HALF_UP).toPlainString()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = BudgetRepository(BudgetDatabase.create(applicationContext))
        setContent {
            val systemDark = isSystemInDarkTheme()
            var darkTheme by remember { mutableStateOf(systemDark) }
            PresupuestoTheme(darkTheme = darkTheme) {
                BudgetApp(repository, applicationContext, darkTheme) { darkTheme = !darkTheme }
            }
        }
    }
}

@Composable
private fun BudgetApp(repository: BudgetRepository, context: Context, isDarkTheme: Boolean, onThemeToggle: () -> Unit) {
    var budget by remember { mutableStateOf<Budget?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var editingBudget by remember { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<Pair<String, Expense>?>(null) }
    var addingToCategory by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var selectedCategoryName by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(budget) {
        if (selectedCategoryName == null && budget != null) {
            selectedCategoryName = budget?.categories?.firstOrNull()?.name
        }
    }

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

    fun fillMockData() {
        budget?.let { current ->
            val random = Random()
            val startTime = System.currentTimeMillis() - 25L * 24 * 60 * 60 * 1000 // 25 days ago
            val mockCategories = current.categories.map { category ->
                val mockExpenses = (1..3).map { i ->
                    Expense(
                        UUID.randomUUID().toString(),
                        "Gasto Mock $i",
                        BigDecimal(random.nextInt(1000) + 100),
                        current.incomeRate,
                        startTime + random.nextLong() % (20L * 24 * 60 * 60 * 1000)
                    )
                }
                category.copy(rows = category.rows + mockExpenses)
            }
            persist(current.copy(categories = mockCategories))
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when {
            error != null && budget == null -> ErrorState(error!!)
            budget == null -> LoadingState()
            else -> BudgetScreen(
                budget = budget!!,
                saving = saving,
                selectedCategoryName = selectedCategoryName,
                isDarkTheme = isDarkTheme,
                onThemeToggle = onThemeToggle,
                onCategorySelect = { selectedCategoryName = it },
                onEditBudget = { editingBudget = true },
                onAddExpense = { addingToCategory = it },
                onEditExpense = { category, expense -> editingExpense = category to expense },
                onDeleteExpense = { categoryName, expense -> persist(budget!!.withoutExpense(categoryName, expense.id)) },
                onFillMockData = { fillMockData() }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BudgetScreen(
    budget: Budget,
    saving: Boolean,
    selectedCategoryName: String?,
    isDarkTheme: Boolean,
    onThemeToggle: () -> Unit,
    onCategorySelect: (String) -> Unit,
    onEditBudget: () -> Unit,
    onAddExpense: (String) -> Unit,
    onEditExpense: (String, Expense) -> Unit,
    onDeleteExpense: (String, Expense) -> Unit,
    onFillMockData: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(budget.monthLabel, fontWeight = FontWeight.Bold) },
                actions = {
                    if (saving) CircularProgressIndicator(Modifier.size(24.dp).padding(4.dp), strokeWidth = 2.dp)
                    IconButton(onClick = onThemeToggle) {
                        Icon(if (isDarkTheme) Icons.Default.Star else Icons.Default.Settings, "Toggle Theme")
                    }
                    IconButton(onClick = onEditBudget) { Icon(Icons.Default.Edit, "Editar presupuesto") }
                    IconButton(onClick = { menuExpanded = true }) { Icon(Icons.AutoMirrored.Filled.List, "Categorías") }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        budget.categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                onClick = {
                                    onCategorySelect(category.name)
                                    menuExpanded = false
                                }
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Llenar datos de prueba") },
                            onClick = {
                                onFillMockData()
                                menuExpanded = false
                            }
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            BalanceCard(budget, onEditBudget)
            Spacer(Modifier.height(12.dp))
            MetricsRow(budget)
            Spacer(Modifier.height(12.dp))
            BudgetGraph(budget, Modifier.height(120.dp).fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            DebtCard(budget)
            Spacer(Modifier.height(16.dp))

            val selectedCategory = budget.categories.find { it.name == selectedCategoryName }
            if (selectedCategory != null) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    item {
                        CategoryCardContent(selectedCategory, onAddExpense, onEditExpense, onDeleteExpense)
                    }
                }
            } else {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text("Selecciona una categoría", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun BudgetGraph(budget: Budget, modifier: Modifier) {
    val expenses = budget.categories.flatMap { it.rows }.sortedBy { it.timestamp }
    val income = budget.incomeBs
    val conversion = budget.conversionBs

    val dataPoints = remember(expenses, income, conversion) {
        var currentBalance = income - conversion
        val points = mutableListOf<BigDecimal>()
        points.add(currentBalance)
        expenses.forEach {
            if (budget.categories.find { c -> c.rows.contains(it) }?.cashExpense == true) {
                currentBalance -= it.amountBs
            }
            points.add(currentBalance)
        }
        points
    }

    if (dataPoints.isEmpty()) return

    val primaryColor = MaterialTheme.colorScheme.primary
    Canvas(modifier.padding(8.dp)) {
        val width = size.width
        val height = size.height
        val maxVal = dataPoints.maxOf { it }.toFloat().coerceAtLeast(1f)
        val minVal = dataPoints.minOf { it }.toFloat()
        val range = (maxVal - minVal).coerceAtLeast(1f)

        val path = Path()
        dataPoints.forEachIndexed { i, valRow ->
            val x = i * (width / (dataPoints.size - 1).coerceAtLeast(1))
            val y = height - ((valRow.toFloat() - minVal) / range * height)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, primaryColor, style = Stroke(width = 3.dp.toPx()))
    }
}

@Composable
private fun CategoryCardContent(
    category: Category,
    onAdd: (String) -> Unit,
    onEdit: (String, Expense) -> Unit,
    onDelete: (String, Expense) -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(category.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        if (category.cashExpense) "Gasto de caja" else "Compra a crédito",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { onAdd(category.name) }) { Icon(Icons.Default.Add, "Agregar") }
            }
            category.rows.forEach { expense ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(expense.label, style = MaterialTheme.typography.bodyLarge)
                        Text("Bs ${expense.amountBs.display()}  ·  $ ${expense.amountUsd.display()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { onEdit(category.name, expense) }) { Icon(Icons.Default.Edit, "Editar", modifier = Modifier.size(20.dp)) }
                    IconButton(onClick = { onDelete(category.name, expense) }) { Icon(Icons.Default.Delete, "Borrar", modifier = Modifier.size(20.dp)) }
                }
            }
            HorizontalDivider()
            Text("Total  Bs ${category.totalBs.display()}  ·  $ ${category.totalUsd.display()}", fontWeight = FontWeight.SemiBold)
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
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Deuda", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("Bs ${budget.debtBs.display()}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
            }
            Text("Incluye compras a crédito del mes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            budget.debts.forEach { debt ->
                Text("${debt.label}: Bs ${debt.remainingBs.display()}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun BudgetEditorDialog(budget: Budget, onDismiss: () -> Unit, onSave: (Budget) -> Unit) {
    var income by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf(budget.incomeRate.toPlainString()) }
    var conversion by remember { mutableStateOf(budget.conversionBs.toPlainString()) }
    var isAdditive by remember { mutableStateOf(false) }
    var invalid by remember { mutableStateOf(false) }

    AlertDialog(onDismissRequest = onDismiss, title = { Text("Editar resumen") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MoneyField(if (isAdditive) "Monto a añadir" else "Ingreso total en Bs", income) { income = it }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = isAdditive, onCheckedChange = { isAdditive = it })
                Text("Sumar al ingreso actual (Bs ${budget.incomeBs.display()})", style = MaterialTheme.typography.bodyMedium)
            }
            MoneyField("Tasa del ingreso", rate) { rate = it }
            MoneyField("Conversión en Bs", conversion) { conversion = it }
            if (invalid) Text("Usa números válidos y valores no negativos.", color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { Button(onClick = {
        val parsedIncome = income.toBigDecimalOrNull() ?: if (isAdditive) BigDecimal.ZERO else budget.incomeBs
        val parsedRate = rate.toBigDecimalOrNull()
        val parsedConversion = conversion.toBigDecimalOrNull()

        if (parsedRate != null && parsedConversion != null && parsedRate.signum() >= 0 && parsedConversion.signum() >= 0 && parsedIncome.signum() >= 0) {
            val finalIncome = if (isAdditive) budget.incomeBs + parsedIncome else parsedIncome
            onSave(budget.copy(incomeBs = finalIncome, incomeRate = parsedRate, conversionBs = parsedConversion))
        } else invalid = true
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
