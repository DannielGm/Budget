package com.personal.presupuesto

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Date
import java.util.Locale
import java.util.Calendar as JavaCalendar
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

private fun BigDecimal.display(): String = DecimalFormat(
    "#,##0.00",
    DecimalFormatSymbols(Locale("es", "ES"))
).format(setScale(2, RoundingMode.HALF_UP))

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BudgetApp(repository: BudgetRepository, context: Context, isDarkTheme: Boolean, onThemeToggle: () -> Unit) {
    var budget by remember { mutableStateOf<Budget?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var editingBudget by remember { mutableStateOf(false) }
    var editingDebt by remember { mutableStateOf<Debt?>(null) }
    var editingExpense by remember { mutableStateOf<Pair<String, Expense>?>(null) }
    var addingToCategory by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var selectedCategoryName by remember { mutableStateOf<String?>(null) }
    var dateFilter by remember { mutableStateOf<Long?>(null) }
    var graphRangeDays by remember { mutableStateOf(1) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showSummary by remember { mutableStateOf(false) }
    var showDebtSummary by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }
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
            val dayMillis = 24L * 60 * 60 * 1000
            val startTime = System.currentTimeMillis() - 30L * dayMillis
            val labels = listOf("Supermercado", "Transporte", "Farmacia", "Restaurante", "Suscripción", "Servicios", "Materiales", "Combustible")
            val mockCategories = current.categories.map { category ->
                val mockExpenses = (0 until 8).map { index ->
                    val amount = BigDecimal(random.nextInt(4500) + 250)
                    Expense(
                        UUID.randomUUID().toString(),
                        "${labels[index]} ${category.name.lowercase(Locale.getDefault())}",
                        amount,
                        BigDecimal(random.nextInt(16) + 5),
                        startTime + random.nextLong(30L * dayMillis)
                    )
                }
                category.copy(rows = category.rows + mockExpenses)
            }
            val mockDebts = current.debts + listOf(
                Debt("Tarjeta Mock", BigDecimal("8500"), BigDecimal("1750")),
                Debt("Préstamo Mock", BigDecimal("12000"), BigDecimal("3000"))
            )
            persist(
                current.copy(
                    incomeBs = current.incomeBs + BigDecimal("25000"),
                    conversionBs = current.conversionBs + BigDecimal("3500"),
                    categories = mockCategories,
                    debts = mockDebts
                )
            )
        }
    }

    fun clearAllData() {
        budget?.let { current ->
            saving = true
            scope.launch {
                runCatching {
                    val cleared = current.copy(
                        incomeBs = BigDecimal.ZERO,
                        incomeRate = BigDecimal.ZERO,
                        conversionBs = BigDecimal.ZERO,
                        categories = current.categories.map { it.copy(rows = emptyList()) },
                        debts = emptyList()
                    )
                    withContext(Dispatchers.IO) {
                        repository.clearAll()
                        repository.save(cleared)
                    }
                    cleared
                }.onSuccess {
                    budget = it
                    selectedCategoryName = null
                    dateFilter = null
                    showSummary = false
                    showDebtSummary = false
                }.onFailure {
                    error = "No se pudo limpiar los datos."
                }
                saving = false
            }
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when {
            error != null && budget == null -> ErrorState(error!!)
            budget == null -> LoadingState()
            else -> if (showDebtSummary) {
                DebtSummaryScreen(
                    budget = budget!!,
                    onBack = { showDebtSummary = false },
                    onEditDebt = { editingDebt = it }
                )
            } else if (showSummary) {
                BudgetSummaryScreen(budget = budget!!, onBack = { showSummary = false })
            } else {
                BudgetScreen(
                    budget = budget!!,
                    saving = saving,
                    selectedCategoryName = selectedCategoryName,
                    isDarkTheme = isDarkTheme,
                    dateFilter = dateFilter,
                    graphRangeDays = graphRangeDays,
                    onThemeToggle = onThemeToggle,
                    onCategorySelect = { selectedCategoryName = it },
                    onDateFilterChange = { dateFilter = it },
                    onRangeChange = { days ->
                        graphRangeDays = days
                        if (dateFilter == null) dateFilter = System.currentTimeMillis()
                    },
                    onEditBudget = { editingBudget = true },
                    onOpenSummary = { showSummary = true },
                    onOpenDebtSummary = { showDebtSummary = true },
                    onAddCategory = { category ->
                        selectedCategoryName = category.name
                        persist(budget!!.copy(categories = budget!!.categories + category))
                    },
                    onDeleteCategory = { categoryName ->
                        val remaining = budget!!.categories.filterNot { it.name == categoryName }
                        persist(budget!!.copy(categories = remaining))
                        selectedCategoryName = remaining.firstOrNull()?.name
                    },
                    onEditDebt = { editingDebt = it },
                    onAddExpense = { addingToCategory = it },
                    onEditExpense = { category, expense -> editingExpense = category to expense },
                    onDeleteExpense = { categoryName, expense -> persist(budget!!.withoutExpense(categoryName, expense.id)) },
                    onFillMockData = { fillMockData() },
                    onClearAllData = { clearAllData() },
                    onOpenDatePicker = { showDatePicker = true }
                )
            }
        }
    }

    DateFilterPicker(
        show = showDatePicker,
        onDismiss = { showDatePicker = false },
        onSelect = { selected ->
            dateFilter = selected
            showDatePicker = false
        }
    )

    budget?.let { current ->
        if (editingBudget) {
            BudgetEditorDialog(current, onDismiss = { editingBudget = false }) {
                editingBudget = false
                persist(it)
            }
        }
        editingDebt?.let { debt ->
            DebtEditorDialog(debt, onDismiss = { editingDebt = null }) {
                editingDebt = null
                persist(current.replaceDebt(it))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateFilterPicker(show: Boolean, onDismiss: () -> Unit, onSelect: (Long) -> Unit) {
    if (!show) return
    val pickerState = rememberDatePickerState()
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    pickerState.selectedDateMillis?.let(onSelect)
                }
            ) { Text("Listo") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    ) {
        DatePicker(state = pickerState, title = null, showModeToggle = false)
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
private fun EmptyState() {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Presupuesto vacío", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Todos los datos guardados fueron eliminados. Cierra y vuelve a abrir la aplicación para cargar el presupuesto inicial.")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BudgetScreen(
    budget: Budget,
    saving: Boolean,
    selectedCategoryName: String?,
    isDarkTheme: Boolean,
    dateFilter: Long?,
    graphRangeDays: Int,
    onThemeToggle: () -> Unit,
    onCategorySelect: (String) -> Unit,
    onDateFilterChange: (Long?) -> Unit,
    onRangeChange: (Int) -> Unit,
    onEditBudget: () -> Unit,
    onOpenSummary: () -> Unit,
    onOpenDebtSummary: () -> Unit,
    onAddCategory: (Category) -> Unit,
    onDeleteCategory: (String) -> Unit,
    onEditDebt: (Debt) -> Unit,
    onAddExpense: (String) -> Unit,
    onEditExpense: (String, Expense) -> Unit,
    onDeleteExpense: (String, Expense) -> Unit,
    onFillMockData: () -> Unit,
    onClearAllData: () -> Unit,
    onOpenDatePicker: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(budget.monthLabel, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                ),
                actions = {
                    if (saving) CircularProgressIndicator(Modifier.size(24.dp).padding(4.dp), strokeWidth = 2.dp, color = Color.White)
                    IconButton(onClick = onThemeToggle) {
                        Icon(if (isDarkTheme) Icons.Default.Star else Icons.Default.Settings, "Toggle Theme")
                    }
                    IconButton(onClick = onEditBudget) { Icon(Icons.Default.Edit, "Editar presupuesto") }
                    IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, "Más opciones") }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Llenar datos de prueba") },
                            onClick = {
                                onFillMockData()
                                menuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Limpiar todos los datos") },
                            onClick = {
                                onClearAllData()
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
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            BalanceCard(budget, onEditBudget, onOpenSummary)
            Spacer(Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Flujo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        listOf(1 to "1d", 7 to "7d", 30 to "30d").forEach { (days, label) ->
                            FilterChip(
                                modifier = Modifier.height(32.dp),
                                selected = graphRangeDays == days,
                                onClick = { onRangeChange(days) },
                                label = { Text(label) }
                            )
                        }
                    }
                    if (dateFilter != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Mostrando desde ${SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(dateFilter))}",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { onDateFilterChange(null) }) { Text("Todo") }
                        }
                    }
                    BudgetGraph(
                        budget = budget,
                        modifier = Modifier
                            .height(168.dp)
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectTapGestures(onTap = { onOpenDatePicker() })
                            },
                        dateFilter = dateFilter,
                        rangeDays = graphRangeDays
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            DebtCard(budget, onOpenDebtSummary)
            Spacer(Modifier.height(16.dp))

            val filteredCategories = budget.filteredCategories(dateFilter, graphRangeDays)
            val selectedCategory = filteredCategories.find { it.name == selectedCategoryName } ?: filteredCategories.firstOrNull()
            if (selectedCategory != null) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    if (selectedCategory.rows.size > 2) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Desliza para ver más gastos",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Icon(
                                    Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Más gastos abajo",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    item {
                        AnimatedContent(
                            targetState = selectedCategory.name,
                            transitionSpec = {
                                (slideInHorizontally { it / 2 } + fadeIn()).togetherWith(
                                    slideOutHorizontally { -it / 2 } + fadeOut()
                                )
                            },
                            label = "category card transition"
                        ) { categoryName ->
                            val animatedCategory = budget.categories.firstOrNull { it.name == categoryName } ?: selectedCategory
                            CategoryCardContent(
                                category = animatedCategory,
                                defaultRate = budget.incomeRate,
                                onCategoryClick = { showCategoryPicker = true },
                                onSwipeCategory = { direction ->
                                    val currentIndex = budget.categories.indexOfFirst { it.name == animatedCategory.name }
                                    val nextIndex = (currentIndex + direction).mod(budget.categories.size)
                                    onCategorySelect(budget.categories[nextIndex].name)
                                },
                                onAdd = onAddExpense,
                                onEdit = onEditExpense,
                                onDelete = onDeleteExpense
                            )
                        }
                    }
                }
            } else {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(if (dateFilter != null) "No hay gastos en esa fecha" else "Selecciona una categoría", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }

    if (showCategoryPicker) {
        CategoryPickerDialog(
            categories = budget.categories,
            selectedCategoryName = selectedCategoryName,
            onDismiss = { showCategoryPicker = false },
            onSelect = {
                onCategorySelect(it)
                showCategoryPicker = false
            },
            onAdd = { category ->
                onAddCategory(category)
            },
            onDelete = { categoryName ->
                onDeleteCategory(categoryName)
            }
        )
    }
}

@Composable
private fun BudgetGraph(budget: Budget, modifier: Modifier, dateFilter: Long? = null, rangeDays: Int = 1) {
    val expenses = (if (dateFilter == null) budget.categories else budget.filteredCategories(dateFilter, rangeDays)).flatMap { it.rows }.sortedBy { it.timestamp }
    val income = budget.incomeBs
    val conversion = budget.conversionBs

    val dataPoints = remember(expenses, income, conversion) {
        var currentBalance = income - conversion
        val points = mutableListOf<BigDecimal>()
        points.add(currentBalance)
        expenses.forEach {
            val category = budget.categories.find { c -> c.rows.any { row -> row.id == it.id } }
            if (category?.cashExpense == true) {
                currentBalance -= it.amountBs
            }
            points.add(currentBalance)
        }
        points
    }

    if (dataPoints.isEmpty()) return

    val graphColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val gridColorSecondary = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    Canvas(modifier.padding(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 24.dp)) {
        val width = size.width
        val height = size.height
        val maxVal = dataPoints.maxOf { it }.toFloat().coerceAtLeast(1f)
        val minVal = dataPoints.minOf { it }.toFloat()
        val range = (maxVal - minVal).coerceAtLeast(1f)

        for (i in 0..4) {
            val y = (height / 4f) * i
            drawLine(
                color = gridColor,
                start = androidx.compose.ui.geometry.Offset(0f, y),
                end = androidx.compose.ui.geometry.Offset(width, y),
                strokeWidth = 1.dp.toPx()
            )
        }
        for (i in 0..5) {
            val x = (width / 5f) * i
            drawLine(
                color = gridColorSecondary,
                start = androidx.compose.ui.geometry.Offset(x, 0f),
                end = androidx.compose.ui.geometry.Offset(x, height),
                strokeWidth = 1.dp.toPx()
            )
        }

        val path = Path()
        dataPoints.forEachIndexed { i, valRow ->
            val x = i * (width / (dataPoints.size - 1).coerceAtLeast(1))
            val y = height - ((valRow.toFloat() - minVal) / range * height)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, graphColor, style = Stroke(width = 3.dp.toPx()))

        val axisPaint = android.graphics.Paint().apply {
            color = axisColor
            textSize = 10.dp.toPx()
            isAntiAlias = true
        }
        val dateFormat = SimpleDateFormat(if (rangeDays == 1) "HH:mm" else "dd/MM", Locale.getDefault())
        val labels = expenses.map { dateFormat.format(Date(it.timestamp)) }
        if (labels.isNotEmpty()) {
            val lastIndex = labels.lastIndex
            drawContext.canvas.nativeCanvas.drawText(labels.first(), 0f, height + 18.dp.toPx(), axisPaint)
            if (lastIndex > 1) {
                drawContext.canvas.nativeCanvas.drawText(labels[lastIndex / 2], width / 2f, height + 18.dp.toPx(), axisPaint)
            }
            drawContext.canvas.nativeCanvas.drawText(labels.last(), width - axisPaint.measureText(labels.last()), height + 18.dp.toPx(), axisPaint)
        }
    }
}

@Composable
private fun CategoryCardContent(
    category: Category,
    defaultRate: BigDecimal,
    onCategoryClick: () -> Unit,
    onSwipeCategory: (Int) -> Unit,
    onAdd: (String) -> Unit,
    onEdit: (String, Expense) -> Unit,
    onDelete: (String, Expense) -> Unit
) {
    Card(
        Modifier
            .fillMaxWidth()
            .pointerInput(category.name) {
                var horizontalDistance = 0f
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (kotlin.math.abs(horizontalDistance) > 48f) {
                            onSwipeCategory(if (horizontalDistance < 0) 1 else -1)
                        }
                        horizontalDistance = 0f
                    },
                    onDragCancel = { horizontalDistance = 0f },
                    onHorizontalDrag = { _, dragAmount ->
                        horizontalDistance += dragAmount
                    }
                )
            }
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        category.name,
                        modifier = Modifier.clickable(onClick = onCategoryClick),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = { onAdd(category.name) }) { Icon(Icons.Default.Add, "Agregar") }
            }
            category.rows.forEach { expense ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clickable { onEdit(category.name, expense) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(expense.label, style = MaterialTheme.typography.bodyLarge)
                        Text("$ ${expense.amountUsdAt(defaultRate).display()}  ·  Bs ${expense.amountBs.display()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(expense.timestamp))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { onEdit(category.name, expense) }) { Icon(Icons.Default.Edit, "Editar", modifier = Modifier.size(20.dp)) }
                    IconButton(onClick = { onDelete(category.name, expense) }) { Icon(Icons.Default.Delete, "Borrar", modifier = Modifier.size(20.dp)) }
                }
            }
            HorizontalDivider()
            Text("Total  $ ${category.totalUsdAt(defaultRate).display()}  ·  Bs ${category.totalBs.display()}", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun BalanceCard(budget: Budget, onEdit: () -> Unit, onOpenSummary: () -> Unit) {
    Card(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenSummary),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.96f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Saldo disponible", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .82f), style = MaterialTheme.typography.labelLarge)
            Text("$ ${budget.balanceUsd?.display() ?: BigDecimal.ZERO.display()}", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("Bs ${budget.balanceBs.display()}", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .9f), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(2.dp))
            OutlinedButton(
                onClick = onEdit,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f))
            ) { Text("Ajustar ingresos y conversiones", color = MaterialTheme.colorScheme.onPrimary) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BudgetSummaryScreen(budget: Budget, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Resumen del presupuesto", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(budget.monthLabel, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            MetricCard("Ingresos", "$ ${budget.incomeUsd.display()}", Modifier.fillMaxWidth())
            MetricCard("Gastos", "$ ${budget.cashUsd.display()}", Modifier.fillMaxWidth())
            MetricCard("Conversión", "$ ${convert(budget.conversionBs, budget.incomeRate).display()}", Modifier.fillMaxWidth())
            MetricCard("Saldo disponible", "$ ${budget.balanceUsd?.display() ?: BigDecimal.ZERO.display()}", Modifier.fillMaxWidth())
            MetricCard("Deuda total", "$ ${budget.debtUsd?.display() ?: BigDecimal.ZERO.display()}", Modifier.fillMaxWidth())
        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryPickerDialog(
    categories: List<Category>,
    selectedCategoryName: String?,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onAdd: (Category) -> Unit,
    onDelete: (String) -> Unit
) {
    var adding by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var cashExpense by remember { mutableStateOf(true) }
    var invalid by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Categorías de gastos") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { category ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            category.name,
                            modifier = Modifier.weight(1f).clickable { onSelect(category.name) },
                            fontWeight = if (category.name == selectedCategoryName) FontWeight.Bold else FontWeight.Normal
                        )
                        IconButton(onClick = { onDelete(category.name) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Eliminar categoría")
                        }
                    }
                }
                if (adding) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; invalid = false },
                        label = { Text("Nombre") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = cashExpense, onCheckedChange = { cashExpense = it })
                        Text("Gasto de caja")
                    }
                    if (invalid) Text("Usa un nombre nuevo.", color = MaterialTheme.colorScheme.error)
                    Button(onClick = {
                        val trimmed = name.trim()
                        if (trimmed.isBlank() || categories.any { it.name.equals(trimmed, ignoreCase = true) }) {
                            invalid = true
                        } else {
                            onAdd(Category(trimmed, cashExpense, emptyList()))
                            name = ""
                            adding = false
                        }
                    }) { Text("Guardar categoría") }
                } else {
                    OutlinedButton(onClick = { adding = true }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("Agregar categoría")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}

@Composable
private fun DebtCard(budget: Budget, onOpenSummary: () -> Unit) {
    Card(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenSummary)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Deuda", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("$ ${budget.debtUsd?.display() ?: BigDecimal.ZERO.display()}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
            }
            Text("Toca para ver el detalle", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DebtSummaryScreen(
    budget: Budget,
    onBack: () -> Unit,
    onEditDebt: (Debt) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Resumen de deuda", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard("Deuda total", "$ ${budget.debtUsd?.display() ?: BigDecimal.ZERO.display()}", Modifier.fillMaxWidth())
            if (budget.debts.isEmpty()) {
                Text("No hay deudas registradas.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                budget.debts.forEach { debt ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(debt.label, fontWeight = FontWeight.SemiBold)
                                Text("Saldo restante: $ ${convert(debt.remainingBs, budget.incomeRate).display()}", style = MaterialTheme.typography.bodySmall)
                                Text("Inicial: $ ${convert(debt.openingBs, budget.incomeRate).display()}  ·  Pagado: $ ${convert(debt.paymentBs, budget.incomeRate).display()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { onEditDebt(debt) }) {
                                Icon(Icons.Default.Edit, contentDescription = "Editar deuda")
                            }
                        }
                    }
                }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseEditorDialog(category: String, expense: Expense?, onDismiss: () -> Unit, onSave: (Expense) -> Unit) {
    var label by remember { mutableStateOf(expense?.label.orEmpty()) }
    var amount by remember { mutableStateOf(expense?.amountBs?.toPlainString().orEmpty()) }
    var rate by remember { mutableStateOf(expense?.rate?.toPlainString().orEmpty()) }
    var timestamp by remember { mutableStateOf(expense?.timestamp ?: System.currentTimeMillis()) }
    var timestampText by remember {
        mutableStateOf(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp)))
    }
    var invalid by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val dateState = rememberDatePickerState(timestamp)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { selected ->
                        val current = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).parse(timestampText) ?: Date(selected)
                        val calendar = JavaCalendar.getInstance().apply {
                            time = current
                            set(JavaCalendar.YEAR, JavaCalendar.getInstance().apply { timeInMillis = selected }.get(JavaCalendar.YEAR))
                            set(JavaCalendar.MONTH, JavaCalendar.getInstance().apply { timeInMillis = selected }.get(JavaCalendar.MONTH))
                            set(JavaCalendar.DAY_OF_MONTH, JavaCalendar.getInstance().apply { timeInMillis = selected }.get(JavaCalendar.DAY_OF_MONTH))
                        }
                        timestamp = calendar.timeInMillis
                        timestampText = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(calendar.time)
                    }
                    showDatePicker = false
                }) { Text("Guardar fecha") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") } }
        ) {
            DatePicker(state = dateState)
        }
    }

    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (expense == null) "Nuevo gasto" else "Editar gasto") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(category, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            OutlinedTextField(label = { Text("Descripción") }, value = label, onValueChange = { label = it }, singleLine = true)
            MoneyField("Monto en Bs", amount) { amount = it }
            MoneyField("Tasa", rate) { rate = it }
            Text("Fecha de creación: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(timestamp))}")
            OutlinedTextField(
                value = timestampText,
                onValueChange = { timestampText = it },
                label = { Text("Fecha y hora (yyyy-MM-dd HH:mm)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = { showDatePicker = true }) { Text("Elegir fecha") }
            Button(onClick = { val now = System.currentTimeMillis(); timestamp = now; timestampText = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(now)) }) { Text("Usar fecha actual") }
            if (invalid) Text("Completa la descripción y usa valores válidos.", color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { Button(onClick = {
        val parsedAmount = amount.toBigDecimalOrNull()
        val parsedRate = rate.toBigDecimalOrNull()
        val parsedTimestamp = try {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).parse(timestampText)?.time ?: timestamp
        } catch (_: Exception) { timestamp }
        if (label.isNotBlank() && parsedAmount != null && parsedRate != null && parsedAmount.signum() >= 0 && parsedRate.signum() >= 0) onSave(Expense(expense?.id ?: UUID.randomUUID().toString(), label.trim(), parsedAmount, parsedRate, parsedTimestamp)) else invalid = true
    }) { Text("Guardar") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
private fun DebtEditorDialog(debt: Debt, onDismiss: () -> Unit, onSave: (Debt) -> Unit) {
    var label by remember { mutableStateOf(debt.label) }
    var opening by remember { mutableStateOf(debt.openingBs.toPlainString()) }
    var payment by remember { mutableStateOf(debt.paymentBs.toPlainString()) }
    var invalid by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar deuda") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(label = { Text("Nombre") }, value = label, onValueChange = { label = it }, singleLine = true)
                MoneyField("Saldo inicial en Bs", opening) { opening = it }
                MoneyField("Pago realizado en Bs", payment) { payment = it }
                if (invalid) Text("Usa valores válidos y no negativos.", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(onClick = {
                val openingValue = opening.toBigDecimalOrNull()
                val paymentValue = payment.toBigDecimalOrNull()
                if (label.isNotBlank() && openingValue != null && paymentValue != null && openingValue.signum() >= 0 && paymentValue.signum() >= 0) {
                    onSave(Debt(label.trim(), openingValue, paymentValue))
                } else {
                    invalid = true
                }
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
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

private fun Budget.replaceDebt(replacement: Debt): Budget = copy(debts = debts.map { if (it.label == replacement.label) replacement else it })
