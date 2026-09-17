package com.personal.presupuesto.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.composeunstyled.DialogPanel
import com.composeunstyled.Scrim
import com.composeunstyled.UnstyledDialog
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun BudgetCalendar(
    monthId: String,
    activeDay: Long?,
    onDismiss: () -> Unit,
    onSelect: (String, Long?) -> Unit,
    headerTitle: String = "Calendario",
    quickSelect: ((Long) -> Unit)? = null,
    monthActionEnabled: Boolean = true,
    onPickDay: ((Long) -> Unit)? = null
) {
    val today = LocalDate.now()
    var month by remember { mutableStateOf(YearMonth.parse(monthId)) }
    var day by remember { mutableStateOf(activeDay?.let { java.time.Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() } ?: month.atDay(1)) }
    var yearText by remember { mutableStateOf(month.year.toString()) }
    val colors = LocalBudgetColors.current
    fun browse(next: YearMonth) {
        month = next
        yearText = next.year.toString()
        day = next.atDay(1)
    }
    UnstyledDialog(visible = true, onDismissRequest = onDismiss, overlay = { Scrim() }) {
        Box(Modifier.fillMaxSize().systemBarsPadding().imePadding(), contentAlignment = Alignment.Center) {
            DialogPanel(
                modifier = Modifier.padding(12.dp).widthIn(max = 440.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp)).background(colors.surface),
                paneTitle = headerTitle
            ) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // DialogPanel requests focus on its first focusable descendant.
                    // Keep initial focus off the year editor so opening the calendar does not show the IME.
                    BudgetText(headerTitle, Modifier.focusable(), style = BudgetTypography.headlineSmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BudgetTextButton(onClick = { browse(month.minusMonths(1)) }, enabled = month > YearMonth.of(1, 1)) { BudgetText("‹") }
                        BudgetText(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.forLanguageTag("es"))), Modifier.weight(1f))
                        BudgetTextButton(onClick = { browse(month.plusMonths(1)) }, enabled = month < YearMonth.from(today)) { BudgetText("›") }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BudgetTextField(value = yearText, onValueChange = { yearText = it }, label = { BudgetText("Año") }, singleLine = true, modifier = Modifier.weight(1f))
                        val year = yearText.toIntOrNull()
                        BudgetTextButton(onClick = { year?.let { browse(YearMonth.of(it, month.monthValue).coerceAtMost(YearMonth.from(today))) } }, enabled = year != null && year in 1..today.year) { BudgetText("Ir") }
                    }
                    Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                    Row(Modifier.fillMaxWidth()) {
                        listOf("L", "M", "X", "J", "V", "S", "D").forEach { BudgetText(it, Modifier.weight(1f), textAlign = TextAlign.Center) }
                    }
                    val offset = month.atDay(1).dayOfWeek.value - 1
                    val weeks = (offset + month.lengthOfMonth() + 6) / 7
                    repeat(weeks) { week ->
                        Row(Modifier.fillMaxWidth()) {
                            repeat(7) { weekday ->
                                val number = week * 7 + weekday - offset + 1
                                if (number !in 1..month.lengthOfMonth()) Spacer(Modifier.weight(1f))
                                else {
                                    val date = month.atDay(number)
                                    BudgetButton(
                                        onClick = {
                                            day = date
                                            quickSelect?.invoke(date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
                                        },
                                        enabled = date <= today,
                                        quiet = date != day,
                                        modifier = Modifier.weight(1f).semantics {
                                            selected = date == day
                                            contentDescription = date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("es")))
                                        }
                                        ) { BudgetText(number.toString()) }
                                }
                            }
                        }
                    }
                    }
                    BudgetText(day.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("es"))), style = BudgetTypography.bodyMedium)
                    if (onPickDay != null) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            BudgetTextButton(onClick = onDismiss) { BudgetText("Cancelar") }
                            BudgetButton(
                                onClick = { onPickDay(day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()) },
                                enabled = day <= today
                            ) { BudgetText("Elegir fecha") }
                        }
                    } else if (monthActionEnabled) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            BudgetTextButton(onClick = onDismiss) { BudgetText("Cancelar") }
                            BudgetTextButton(onClick = { onSelect(month.toString(), day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()) }, enabled = day <= today) { BudgetText("Ver día") }
                            BudgetButton(onClick = { onSelect(month.toString(), null) }) { BudgetText("Ver mes") }
                        }
                    }
                }
            }
        }
    }
}
