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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WbSunny
import com.personal.presupuesto.ui.theme.BudgetButton as Button
import com.personal.presupuesto.ui.theme.BudgetCalendar
import com.personal.presupuesto.ui.theme.BudgetCard as Card
import com.personal.presupuesto.ui.theme.BudgetCheckbox as Checkbox
import com.personal.presupuesto.ui.theme.BudgetChip as FilterChip
import com.personal.presupuesto.ui.theme.BudgetDialog as AlertDialog
import com.personal.presupuesto.ui.theme.BudgetDivider as HorizontalDivider
import com.personal.presupuesto.ui.theme.BudgetIcon as Icon
import com.personal.presupuesto.ui.theme.BudgetIconButton as IconButton
import com.personal.presupuesto.ui.theme.BudgetOutlinedButton as OutlinedButton
import com.personal.presupuesto.ui.theme.BudgetScrollColumn as Scaffold
import com.personal.presupuesto.ui.theme.BudgetSpinner as CircularProgressIndicator
import com.personal.presupuesto.ui.theme.BudgetText as Text
import com.personal.presupuesto.ui.theme.BudgetTextButton as TextButton
import com.personal.presupuesto.ui.theme.BudgetTextField as OutlinedTextField
import com.personal.presupuesto.ui.theme.BudgetMenuItem
import com.personal.presupuesto.ui.theme.BudgetOverflowMenu
import com.personal.presupuesto.ui.theme.BudgetTypography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Date
import java.util.Locale
import java.util.Calendar as JavaCalendar
import com.personal.presupuesto.data.BudgetDatabase
import com.personal.presupuesto.data.BudgetRepository
import com.personal.presupuesto.data.ExchangeRateRepository
import com.personal.presupuesto.data.HttpExchangeRateHttp
import com.personal.presupuesto.data.dateIsoOf
import com.personal.presupuesto.data.MonthSummary
import com.personal.presupuesto.ui.theme.BudgetTheme
import com.personal.presupuesto.ui.theme.LocalBudgetColors
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
        val database = BudgetDatabase.create(applicationContext)
        val exchange = ExchangeRateRepository(database, HttpExchangeRateHttp())
        val repository = BudgetRepository(database, exchange)
        setContent {
            val systemDark = isSystemInDarkTheme()
            var darkTheme by remember { mutableStateOf(systemDark) }
            PresupuestoTheme(darkTheme = darkTheme) {
                BudgetApp(repository, exchange, applicationContext, darkTheme) { darkTheme = !darkTheme }
            }
        }
    }
}

@Composable
private fun BudgetApp(repository: BudgetRepository, exchange: ExchangeRateRepository, context: Context, isDarkTheme: Boolean, onThemeToggle: () -> Unit) {
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
    var showSummary by remember { mutableStateOf(false) }
    var showDebtSummary by remember { mutableStateOf(false) }
    var months by remember { mutableStateOf<List<MonthSummary>>(emptyList()) }
    var confirmClearAll by remember { mutableStateOf(false) }
    var confirmClearMonth by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var rateBadge by remember { mutableStateOf<String?>(null) }
    var rateStale by remember { mutableStateOf(false) }
    var rateRefreshing by remember { mutableStateOf(false) }
    var rateVersion by remember { mutableStateOf(0) }

    // BCV rate maintenance: startup runs non-forced (network-free when today is cached);
    // the badge button forces a full refresh. Failures keep the last known rate marked stale.
    fun refreshRates(force: Boolean) {
        if (rateRefreshing) return
        rateRefreshing = true
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { exchange.ensureFresh(force) } }
                .onSuccess { snapshot ->
                    val rate = snapshot.displayedRate
                    rateBadge = rate?.let { "${it.display()} Bs/USD" }
                    rateStale = rate != null && !snapshot.updatedToday
                    rateVersion++
                }
                .onFailure { rateStale = rateBadge != null }
            rateRefreshing = false
        }
    }

    fun persist(updated: Budget) {
        if (saving) return
        saving = true
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.save(updated)
                    repository.listMonths()
                }
            }.onSuccess { stored -> budget = updated; months = stored }
                .onFailure { error = "No se pudo guardar el cambio." }
            saving = false
        }
    }

    LaunchedEffect(Unit) {
        runCatching {
            val seed = withContext(Dispatchers.IO) { SeedLoader.load(context) }
            withContext(Dispatchers.IO) {
                repository.loadCurrentMonth(seed) to repository.listMonths()
            }
        }.onSuccess { (loaded, stored) ->
            budget = loaded
            months = stored
            refreshRates(false)
        }
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
                    categories = mockCategories,
                    debts = mockDebts
                )
            )
        }
    }

    fun clearAllData() {
        if (saving) return
        budget?.let { current ->
            saving = true
            scope.launch {
                runCatching {
                    val cleared = withContext(Dispatchers.IO) {
                        repository.clearAllKeepingMonth(current.monthId)
                    }
                    months = withContext(Dispatchers.IO) { repository.listMonths() }
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

    fun recordExpense(category: String, expense: Expense) {
        val current = budget ?: return
        if (saving) return
        saving = true
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.recordExpense(current.monthId, category, expense) to repository.listMonths()
                }
            }.onSuccess { (updated, stored) ->
                budget = updated
                months = stored
                selectedCategoryName = category
                dateFilter = null
                graphRangeDays = 1
            }.onFailure { error = "No se pudo guardar el gasto." }
            saving = false
        }
    }

    fun switchMonth(monthId: String, selectedDay: Long?) {
        val current = budget ?: return
        if (saving || isFutureMonth(monthId)) return
        if (current.monthId == monthId) {
            dateFilter = selectedDay
            graphRangeDays = if (selectedDay == null) 30 else 1
            return
        }
        saving = true
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.loadOrCreate(monthId, current) to repository.listMonths()
                }
            }.onSuccess { (selected, stored) ->
                budget = selected
                months = stored
                selectedCategoryName = null
                dateFilter = selectedDay
                graphRangeDays = if (selectedDay == null) 30 else 1
                showSummary = false
                showDebtSummary = false
            }.onFailure { error = "No se pudo cargar el mes seleccionado." }
            saving = false
        }
    }

    // Keeps the month in the list so its history stays comparable, but zeroes every value.
    fun clearCurrentMonth() {
        val current = budget ?: return
        if (saving) return
        saving = true
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { repository.clearMonth(current.monthId) } }
                .onSuccess {
                    budget = it
                    selectedCategoryName = null
                    dateFilter = null
                    graphRangeDays = 1
                    showSummary = false
                    showDebtSummary = false
                }.onFailure { error = "No se pudo limpiar el mes." }
            saving = false
        }
    }

    // Month view converts with the month's BCV rate; a day filter converts with that day's rate.
        val contextRate by produceState(
        initialValue = budget?.incomeRate ?: BigDecimal.ZERO,
        budget?.monthId, budget?.incomeRate, dateFilter, rateVersion
    ) {
        val current = budget ?: return@produceState
        value = if (dateFilter == null) current.incomeRate
        else {
            val filter = dateFilter
            if (filter != null) withContext(Dispatchers.IO) { exchange.dayRate(dateIsoOf(filter), current.incomeRate) }
            else current.incomeRate
        }
    }

    Box(Modifier.fillMaxSize().background(LocalBudgetColors.current.background)) {
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
                    contextRate = contextRate,
                    rateBadge = rateBadge,
                    rateStale = rateStale,
                    rateRefreshing = rateRefreshing,
                    onRefreshRate = { refreshRates(true) },
                    onThemeToggle = onThemeToggle,
                    onCategorySelect = { selectedCategoryName = it },
                    onDateFilterChange = { dateFilter = it; graphRangeDays = 30 },
                    onRangeChange = { days ->
                        graphRangeDays = days
                        if (days != 30 && dateFilter == null) {
                            dateFilter = localDateFromPicker(pickerStartOfMonth(budget!!.monthId))
                        }
                    },
                    onEditBudget = { editingBudget = true },
                    onGoToToday = { switchMonth(currentMonthId(), null) },
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
                    onClearAllData = { confirmClearAll = true },
                    onClearCurrentMonth = { confirmClearMonth = true },
                    onMonthSelect = { month, day -> switchMonth(month, day) }
                )
            }
        }
    }

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
                recordExpense(category, it)
            }
        }
        addingToCategory?.let { category ->
            ExpenseEditorDialog(category, null, onDismiss = { addingToCategory = null }) {
                addingToCategory = null
                recordExpense(category, it)
            }
        }
        if (confirmClearAll) {
            ConfirmDialog(
                title = "Limpiar todos los datos",
                message = "Se borrarán los gastos, deudas e ingresos de todos los meses guardados. Esta acción no se puede deshacer.",
                confirmLabel = "Limpiar todo",
                onConfirm = {
                    confirmClearAll = false
                    clearAllData()
                },
                onDismiss = { confirmClearAll = false }
            )
        }
        if (confirmClearMonth) {
            ConfirmDialog(
                title = "Limpiar mes actual",
                message = "Se borrarán los gastos, deudas e ingresos de ${monthDisplayName(current.monthId)}. El mes seguirá disponible en la lista.",
                confirmLabel = "Limpiar mes",
                onConfirm = {
                    confirmClearMonth = false
                    clearCurrentMonth()
                },
                onDismiss = { confirmClearMonth = false }
            )
        }
    }
}

@Composable
private fun BudgetMonthPicker(monthId: String, activeDay: Long?, onDismiss: () -> Unit, onSelect: (String, Long?) -> Unit) {
    com.personal.presupuesto.ui.theme.BudgetCalendar(monthId, activeDay, onDismiss, onSelect)
}

@Composable
private fun LoadingState() {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text("Cargando tu presupuesto", style = BudgetTypography.bodyLarge)
    }
}

@Composable
private fun ErrorState(message: String) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Algo salió mal", style = BudgetTypography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(message)
    }
}

@Composable
private fun ConfirmDialog(title: String, message: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("Cancelar") }
                Button(onClick = onConfirm) { Text(confirmLabel) }
            }
        }
    )
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Presupuesto vacío", style = BudgetTypography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Todos los datos guardados fueron eliminados. Cierra y vuelve a abrir la aplicación para cargar el presupuesto inicial.")
    }
}

// App-owned top bar: primary background, white content, status-bar inset.
@Composable
private fun BudgetTopBar(
    title: @Composable () -> Unit,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Column(Modifier.fillMaxWidth().background(BudgetTheme.colors.primary).statusBarsPadding()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            navigationIcon?.invoke()
            Box(Modifier.weight(1f).padding(horizontal = 8.dp)) { title() }
            actions()
        }
    }
}

@Composable
private fun BudgetScreen(
    budget: Budget,
    saving: Boolean,
    selectedCategoryName: String?,
    isDarkTheme: Boolean,
    dateFilter: Long?,
    graphRangeDays: Int,
    contextRate: BigDecimal,
    rateBadge: String?,
    rateStale: Boolean,
    rateRefreshing: Boolean,
    onRefreshRate: () -> Unit,
    onThemeToggle: () -> Unit,
    onCategorySelect: (String) -> Unit,
    onDateFilterChange: (Long?) -> Unit,
    onRangeChange: (Int) -> Unit,
    onEditBudget: () -> Unit,
    onGoToToday: () -> Unit,
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
    onClearCurrentMonth: () -> Unit,
    onMonthSelect: (String, Long?) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var categoryTransitionDirection by remember { mutableStateOf(1) }

    Column(Modifier.fillMaxSize()) {
        BudgetTopBar(
            title = {
                Box {
                    var showMonthPicker by remember { mutableStateOf(false) }
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.clickable(enabled = !saving) { showMonthPicker = true }
                    ) {
                        Text(
                            text = if (dateFilter == null) monthDisplayName(budget.monthId)
                            else "${monthName(budget.monthId)} - ${SimpleDateFormat("d", Locale.getDefault()).format(Date(dateFilter))}",
                            style = BudgetTypography.brandHeadline,
                            color = Color.White,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    if (showMonthPicker) {
                        BudgetMonthPicker(
                            monthId = budget.monthId,
                            activeDay = dateFilter,
                            onDismiss = { showMonthPicker = false },
                            onSelect = { month, day ->
                                showMonthPicker = false
                                onMonthSelect(month, day)
                            }
                        )
                    }
                }
            },
            actions = {
                if (saving) CircularProgressIndicator(Modifier.size(24.dp).padding(4.dp), color = Color.White)
                val isNotToday = budget.monthId != currentMonthId() || dateFilter != null
                if (isNotToday) {
                    IconButton(onClick = onGoToToday) { Icon(Icons.Default.Home, "Ir a hoy", tint = Color.White) }
                }
                IconButton(onClick = onThemeToggle) {
                    Icon(
                        if (isDarkTheme) Icons.Default.WbSunny else Icons.Default.NightsStay,
                        "Toggle Theme",
                        tint = Color.White
                    )
                }
                IconButton(onClick = onEditBudget) { Icon(Icons.Default.Edit, "Editar presupuesto", tint = Color.White) }
                IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, "Más opciones", tint = Color.White) }
            }
        )
        BudgetOverflowMenu(
            expanded = menuExpanded,
            onDismiss = { menuExpanded = false },
            items = listOf(
                BudgetMenuItem("Llenar datos de prueba") { onFillMockData() },
                BudgetMenuItem("Limpiar mes actual") { onClearCurrentMonth() },
                BudgetMenuItem("Limpiar todos los datos") { onClearAllData() }
            )
        )
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            BalanceCard(budget, onEditBudget, onOpenSummary, contextRate, rateBadge, rateStale, rateRefreshing, onRefreshRate)
            Spacer(Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                containerColor = BudgetTheme.colors.surfaceVariant
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Flujo", style = BudgetTypography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        listOf(1 to "1d", 7 to "7d", 30 to "Mes").forEach { (days, label) ->
                            FilterChip(
                                modifier = Modifier.height(32.dp),
                                selected = if (dateFilter == null) days == 30 else graphRangeDays == days,
                                onClick = { onRangeChange(days) },
                                label = { Text(label) }
                            )
                        }
                    }
                    if (dateFilter != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${if (graphRangeDays == 30) "Mes" else "${graphRangeDays}d hasta"} ${SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(dateFilter))}",
                                style = BudgetTypography.labelMedium
                            )
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { onDateFilterChange(null) }) { Text("Ver mes") }
                        }
                    }
                    var showCalendarOnGraph by remember { mutableStateOf(false) }
                    if (showCalendarOnGraph) {
                        BudgetMonthPicker(
                            monthId = budget.monthId,
                            activeDay = dateFilter,
                            onDismiss = { showCalendarOnGraph = false },
                            onSelect = { month, day ->
                                showCalendarOnGraph = false
                                if (day != null) onDateFilterChange(day)
                            }
                        )
                    }
                    BudgetGraph(
                        budget = budget,
                        modifier = Modifier
                            .height(168.dp)
                            .fillMaxWidth()
                            .clickable { showCalendarOnGraph = true },
                        dateFilter = dateFilter,
                        rangeDays = graphRangeDays
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            DebtCard(budget, onOpenDebtSummary, contextRate)
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
                                    style = BudgetTypography.labelSmall,
                                    color = BudgetTheme.colors.onSurfaceVariant
                                )
                                Icon(
                                    Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Más gastos abajo",
                                    tint = BudgetTheme.colors.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    item {
                        AnimatedContent(
                            targetState = selectedCategory.name,
                            transitionSpec = {
                                val direction = if (categoryTransitionDirection > 0) 1 else -1
                                (slideInHorizontally { it / 2 * direction } + fadeIn()).togetherWith(
                                    slideOutHorizontally { -it / 2 * direction } + fadeOut()
                                )
                            },
                            label = "category card transition"
                        ) { categoryName ->
                            val animatedCategory = filteredCategories.firstOrNull { it.name == categoryName } ?: selectedCategory
                            CategoryCardContent(
                                category = animatedCategory,
                                defaultRate = contextRate,
                                onCategoryClick = { showCategoryPicker = true },
                                onSwipeCategory = { direction ->
                                    if (filteredCategories.isNotEmpty()) {
                                        val currentIndex = filteredCategories.indexOfFirst { it.name == animatedCategory.name }
                                        val nextIndex = (currentIndex + direction).mod(filteredCategories.size)
                                        categoryTransitionDirection = direction
                                        onCategorySelect(filteredCategories[nextIndex].name)
                                    }
                                },
                                onAdd = onAddExpense,
                                onEdit = onEditExpense,
                                onDelete = onDeleteExpense
                            )
                        }
                    }
                }
            } else {
                Column(
                    Modifier.weight(1f).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        if (dateFilter != null) "No hay gastos en esa fecha" else "No hay categorías de gastos",
                        style = BudgetTypography.bodyLarge
                    )
                    if (dateFilter == null) {
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { showCategoryPicker = true }) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.size(6.dp))
                            Text("Crear categoría")
                        }
                    }
                }
            }
        }
    }

    if (showCategoryPicker) {
        CategoryPickerDialog(
            categories = budget.categories,
            selectedCategoryName = selectedCategoryName,
            onDismiss = { showCategoryPicker = false },
            onSelect = { categoryName ->
                val currentIndex = budget.categories.indexOfFirst { it.name == selectedCategoryName }
                val nextIndex = budget.categories.indexOfFirst { it.name == categoryName }
                if (currentIndex >= 0 && nextIndex >= 0 && currentIndex != nextIndex) {
                    categoryTransitionDirection = if (nextIndex > currentIndex) 1 else -1
                }
                onCategorySelect(categoryName)
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

    val dataPoints = remember(expenses, income, budget.openingBalanceBs) {
        var currentBalance = budget.openingBalanceBs + income
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

    val graphColor = BudgetTheme.colors.primary
    val gridColor = BudgetTheme.colors.onSurface.copy(alpha = 0.08f)
    val gridColorSecondary = BudgetTheme.colors.onSurface.copy(alpha = 0.06f)
    val axisColor = BudgetTheme.colors.onSurfaceVariant.toArgb()
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
                        style = BudgetTypography.titleLarge,
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
                        Text(expense.label, style = BudgetTypography.bodyLarge)
                        Text("$ ${expense.amountUsdAt(defaultRate).display()}  ·  Bs ${expense.amountBs.display()}", style = BudgetTypography.bodySmall, color = BudgetTheme.colors.onSurfaceVariant)
                        Text(
                            "${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(expense.timestamp))}",
                            style = BudgetTypography.bodySmall,
                            color = BudgetTheme.colors.onSurfaceVariant
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
private fun BalanceCard(
    budget: Budget,
    onEdit: () -> Unit,
    onOpenSummary: () -> Unit,
    contextRate: BigDecimal,
    rateBadge: String?,
    rateStale: Boolean,
    rateRefreshing: Boolean,
    onRefreshRate: () -> Unit
) {
    Card(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenSummary),
        containerColor = BudgetTheme.colors.primary.copy(alpha = 0.96f)
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                rateBadge?.let {
                    Text(
                        it + if (rateStale) " (sin actualizar)" else "",
                        color = BudgetTheme.colors.onPrimary.copy(alpha = .85f),
                        style = BudgetTypography.labelMedium
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onRefreshRate, enabled = !rateRefreshing) {
                    if (rateRefreshing) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = BudgetTheme.colors.onPrimary)
                    } else {
                        Icon(Icons.Filled.Refresh, contentDescription = "Actualizar tasa BCV", tint = BudgetTheme.colors.onPrimary, modifier = Modifier.size(20.dp))
                    }
                }
            }
            Text(
                text = "$ ${budget.balanceUsdAt(contextRate)?.display() ?: BigDecimal.ZERO.display()}",
                color = BudgetTheme.colors.onPrimary,
                style = BudgetTypography.brandHeadline.copy(fontSize = 42.sp, fontFamily = FontFamily.Serif),
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "Bs ${budget.balanceBs.display()}",
                color = BudgetTheme.colors.onPrimary.copy(alpha = .9f),
                style = BudgetTypography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            HorizontalDivider()
            
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Ingresos", color = BudgetTheme.colors.onPrimary.copy(alpha = .7f), style = BudgetTypography.labelSmall)
                    Text("Bs ${budget.incomeBs.display()}", color = BudgetTheme.colors.onPrimary, style = BudgetTypography.bodyMedium, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Gastos de caja", color = BudgetTheme.colors.onPrimary.copy(alpha = .7f), style = BudgetTypography.labelSmall)
                    Text("Bs ${budget.cashBs.display()}", color = BudgetTheme.colors.onPrimary, style = BudgetTypography.bodyMedium, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(4.dp))
            OutlinedButton(
                onClick = onEdit,
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, BudgetTheme.colors.onPrimary.copy(alpha = 0.5f))
            ) { Text("Ingresos", color = BudgetTheme.colors.onPrimary) }
        }
    }
}

@Composable
private fun BudgetSummaryScreen(budget: Budget, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        BudgetTopBar(
            title = { Text("Resumen del presupuesto", fontWeight = FontWeight.Bold, color = Color.White) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
                }
            }
        )
        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(monthDisplayName(budget.monthId), style = BudgetTypography.headlineSmall, fontWeight = FontWeight.Bold)
            MetricCard("Ingresos", "$ ${budget.incomeUsd.display()}", Modifier.fillMaxWidth())
            MetricCard("Gastos", "$ ${budget.cashUsd.display()}", Modifier.fillMaxWidth())
            MetricCard("Saldo inicial", "$ ${(if (budget.incomeRate.signum() == 0) BigDecimal.ZERO else convert(budget.openingBalanceBs, budget.incomeRate)).display()}  ·  Bs ${budget.openingBalanceBs.display()}", Modifier.fillMaxWidth())
            MetricCard("Tasa BCV del mes", "${budget.incomeRate.display()} Bs/USD", Modifier.fillMaxWidth())
            MetricCard("Saldo disponible", "$ ${budget.balanceUsd?.display() ?: BigDecimal.ZERO.display()}", Modifier.fillMaxWidth())
            MetricCard("Deuda total", "$ ${budget.debtUsd?.display() ?: BigDecimal.ZERO.display()}", Modifier.fillMaxWidth())
        }
    }
}

@Suppress("UNUSED_PARAMETER")
@Composable
private fun MetricsRow(budget: Budget) {}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier) {
    Card(modifier, containerColor = BudgetTheme.colors.surfaceVariant) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = BudgetTypography.labelMedium, color = BudgetTheme.colors.onSurfaceVariant)
            Spacer(Modifier.height(5.dp))
            Text(value, style = BudgetTypography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

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
                        Text("Tipo", style = BudgetTypography.labelLarge)
                        Spacer(Modifier.size(8.dp))
                        FilterChip(
                            selected = cashExpense,
                            onClick = { cashExpense = true },
                            label = { Text("Contado") }
                        )
                        Spacer(Modifier.size(6.dp))
                        FilterChip(
                            selected = !cashExpense,
                            onClick = { cashExpense = false },
                            label = { Text("Crédito") }
                        )
                    }
                    if (invalid) Text("Usa un nombre nuevo.", color = LocalBudgetColors.current.error)
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
private fun DebtCard(budget: Budget, onOpenSummary: () -> Unit, contextRate: BigDecimal) {
    Card(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenSummary)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Deuda", style = BudgetTypography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("$ ${budget.debtUsdAt(contextRate)?.display() ?: BigDecimal.ZERO.display()}", style = BudgetTypography.titleMedium, color = BudgetTheme.colors.secondary, fontWeight = FontWeight.Bold)
            }
            Text("Toca para ver el detalle", style = BudgetTypography.bodySmall, color = BudgetTheme.colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun DebtSummaryScreen(
    budget: Budget,
    onBack: () -> Unit,
    onEditDebt: (Debt) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        BudgetTopBar(
            title = { Text("Resumen de deuda", fontWeight = FontWeight.Bold, color = Color.White) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
                }
            }
        )
        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard("Deuda total", "$ ${budget.debtUsd?.display() ?: BigDecimal.ZERO.display()}", Modifier.fillMaxWidth())
            if (budget.debts.isEmpty()) {
                Text("No hay deudas registradas.", color = BudgetTheme.colors.onSurfaceVariant)
            } else {
                budget.debts.forEach { debt ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(debt.label, fontWeight = FontWeight.SemiBold)
                                Text("Saldo restante: $ ${convert(debt.remainingBs, budget.incomeRate).display()}", style = BudgetTypography.bodySmall)
                                Text("Inicial: $ ${convert(debt.openingBs, budget.incomeRate).display()}  ·  Pagado: $ ${convert(debt.paymentBs, budget.incomeRate).display()}", style = BudgetTypography.bodySmall, color = BudgetTheme.colors.onSurfaceVariant)
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
    var opening by remember { mutableStateOf(budget.openingBalanceBs.toPlainString()) }
    var overrideRate by remember { mutableStateOf(budget.incomeRate.toPlainString()) }
    var isAdditive by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }
    var showOpening by remember { mutableStateOf(false) }
    var invalid by remember { mutableStateOf(false) }

    AlertDialog(onDismissRequest = onDismiss, title = { Text("Ingresos") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MoneyField(if (isAdditive) "Monto a añadir" else "Ingreso total en Bs", income) { income = it }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = isAdditive, onCheckedChange = { isAdditive = it })
                Text("Sumar al ingreso actual (Bs ${budget.incomeBs.display()})", style = BudgetTypography.bodyMedium)
            }
            
            TextButton(onClick = { showAdvanced = !showAdvanced }) {
                Text(if (showAdvanced) "Ocultar opciones avanzadas" else "Ver opciones avanzadas")
            }
            
            if (showAdvanced) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = showOpening, onCheckedChange = { showOpening = it })
                    Text("Editar saldo inicial", style = BudgetTypography.bodyMedium)
                }
                if (showOpening) {
                    MoneyField("Saldo inicial en Bs", opening) { opening = it }
                }
                MoneyField("Tasa BCV (Sobrescribir)", overrideRate) { overrideRate = it }
                Text(
                    "Tasa BCV actual del mes: ${budget.incomeRate.display()} Bs/USD",
                    style = BudgetTypography.bodySmall,
                    color = BudgetTheme.colors.onSurfaceVariant
                )
            }
            
            if (invalid) Text("Usa números válidos y valores no negativos.", color = BudgetTheme.colors.error)
        }
    }, confirmButton = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
            Button(onClick = {
                val parsedIncome = income.toBigDecimalOrNull() ?: if (isAdditive) BigDecimal.ZERO else budget.incomeBs
                val parsedOpening = opening.toBigDecimalOrNull()
                val parsedRate = overrideRate.toBigDecimalOrNull()

                if (parsedOpening != null && parsedRate != null && parsedRate.signum() >= 0 && parsedIncome.signum() >= 0) {
                    val finalIncome = if (isAdditive) budget.incomeBs + parsedIncome else parsedIncome
                    onSave(budget.copy(incomeBs = finalIncome, incomeRate = parsedRate, openingBalanceBs = parsedOpening))
                } else invalid = true
            }) { Text("Guardar") }
        }
    })
}

@Composable
private fun ExpenseEditorDialog(category: String, expense: Expense?, onDismiss: () -> Unit, onSave: (Expense) -> Unit) {
    var label by remember { mutableStateOf(expense?.label.orEmpty()) }
    var amount by remember { mutableStateOf(expense?.amountBs?.toPlainString().orEmpty()) }
    var timestamp by remember { mutableStateOf(expense?.timestamp ?: System.currentTimeMillis()) }
    var timestampText by remember {
        mutableStateOf(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp)))
    }
    var invalid by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        BudgetCalendar(
            monthId = java.time.YearMonth.from(java.time.Instant.ofEpochMilli(timestamp).atZone(java.time.ZoneId.systemDefault()).toLocalDate()).toString(),
            activeDay = timestamp,
            onDismiss = { showDatePicker = false },
            onSelect = { _, _ -> },
            headerTitle = "Elegir fecha",
            monthActionEnabled = false,
            onPickDay = { pickedDay ->
                val current = parseExpenseTimestamp(timestampText)
                timestamp = if (current != null) {
                    val keep = JavaCalendar.getInstance().apply { timeInMillis = current }
                    val picked = JavaCalendar.getInstance().apply { timeInMillis = pickedDay }
                    keep.set(picked.get(JavaCalendar.YEAR), picked.get(JavaCalendar.MONTH), picked.get(JavaCalendar.DAY_OF_MONTH))
                    keep.timeInMillis
                } else {
                    pickedDay
                }
                timestampText = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
                showDatePicker = false
            }
        )
    }

    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (expense == null) "Nuevo gasto" else "Editar gasto") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(category, style = BudgetTypography.labelLarge, color = BudgetTheme.colors.primary)
            OutlinedTextField(label = { Text("Descripción") }, value = label, onValueChange = { label = it }, singleLine = true)
            MoneyField("Monto en Bs", amount) { amount = it }
            Text(
                "Tasa BCV automática",
                style = BudgetTypography.bodySmall,
                color = BudgetTheme.colors.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(timestamp)),
                    style = BudgetTypography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { showDatePicker = true }) { Text("Elegir") }
                TextButton(onClick = { 
                    val now = System.currentTimeMillis()
                    timestamp = now
                    timestampText = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(now))
                }) { Text("Hoy") }
            }
            if (invalid) Text("Completa la descripción y usa valores válidos.", color = BudgetTheme.colors.error)
        }
    }, confirmButton = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
            Button(onClick = {
                val parsedAmount = amount.toBigDecimalOrNull()
                val parsedTimestamp = parseExpenseTimestamp(timestampText)
                // Phase 4.3: conversions follow the BCV context rate; new rows store rate 0.
                if (label.isNotBlank() && parsedAmount != null && parsedAmount.signum() >= 0 && parsedTimestamp != null && isPastOrPresentTimestamp(parsedTimestamp)) {
                    onSave(Expense(expense?.id ?: UUID.randomUUID().toString(), label.trim(), parsedAmount, expense?.rate ?: BigDecimal.ZERO, parsedTimestamp))
                } else invalid = true
            }) { Text("Guardar") }
        }
    })
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
                if (invalid) Text("Usa valores válidos y no negativos.", color = LocalBudgetColors.current.error)
            }
        },
        confirmButton = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("Cancelar") }
                Button(onClick = {
                    val openingValue = opening.toBigDecimalOrNull()
                    val paymentValue = payment.toBigDecimalOrNull()
                    if (label.isNotBlank() && openingValue != null && paymentValue != null && openingValue.signum() >= 0 && paymentValue.signum() >= 0) {
                        onSave(Debt(label.trim(), openingValue, paymentValue))
                    } else {
                        invalid = true
                    }
                }) { Text("Guardar") }
            }
        }
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
