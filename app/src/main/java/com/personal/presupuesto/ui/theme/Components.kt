package com.personal.presupuesto.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.composeunstyled.DialogPanel
import com.composeunstyled.Scrim
import com.composeunstyled.UnstyledButton
import com.composeunstyled.UnstyledDialog

// App-owned styling; Unstyled supplies keyboard activation and button semantics.
@Composable
fun BudgetButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    outlined: Boolean = false,
    quiet: Boolean = false,
    border: BorderStroke? = null,
    content: @Composable RowScope.() -> Unit
) {
    val colors = LocalBudgetColors.current
    val shape = RoundedCornerShape(12.dp)
    val background = if (outlined || quiet) Color.Transparent else colors.primary
    val foreground = if (outlined || quiet) colors.primary else colors.onPrimary
    UnstyledButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp).clip(shape)
            .background(background.copy(alpha = if (enabled) background.alpha else background.alpha * 0.35f))
            .then(if (outlined) Modifier.border(border ?: BorderStroke(1.dp, colors.outline), shape) else Modifier),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        CompositionLocalProvider(LocalBudgetContentColor provides foreground.copy(alpha = if (enabled) 1f else 0.38f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, content = content)
        }
    }
}

@Composable
fun BudgetTextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) =
    BudgetButton(onClick, modifier, enabled, quiet = true, content = content)

@Composable
fun BudgetOutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, border: BorderStroke? = null, content: @Composable RowScope.() -> Unit) =
    BudgetButton(onClick, modifier, enabled, outlined = true, border = border, content = content)

@Composable
fun BudgetIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    UnstyledButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(10.dp),
        modifier = modifier.sizeIn(minWidth = 44.dp, minHeight = 44.dp)
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
fun BudgetIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalBudgetContentColor.current.takeOrElse { LocalBudgetColors.current.onSurface }
) {
    val painter = rememberVectorPainter(imageVector)
    val effective = if (modifier === Modifier) Modifier.size(24.dp) else modifier
    Canvas(
        modifier = effective.semantics {
            if (contentDescription != null) this.contentDescription = contentDescription
        }
    ) {
        with(painter) { draw(size = size, colorFilter = ColorFilter.tint(tint)) }
    }
}

@Composable
fun BudgetText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalBudgetTextStyle.current,
    color: Color = LocalBudgetContentColor.current.takeOrElse { LocalBudgetColors.current.onSurface },
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE
) {
    var resolved: TextStyle = style.copy(color = color)
    if (fontWeight != null) resolved = resolved.copy(fontWeight = fontWeight)
    if (textAlign != null) resolved = resolved.copy(textAlign = textAlign)
    BasicText(text = text, modifier = modifier, style = resolved, maxLines = maxLines)
}

// ---------- Cards and dividers ----------

@Composable
fun BudgetCard(
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
    content: @Composable () -> Unit
) {
    val colors = LocalBudgetColors.current
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .clip(shape)
            .background(containerColor ?: colors.surface)
            .border(1.dp, colors.outline.copy(alpha = 0.4f), shape)
    ) {
        content()
    }
}

@Composable
fun BudgetDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(LocalBudgetColors.current.outline.copy(alpha = 0.5f))
    )
}

// ---------- Fields ----------

@Composable
fun BudgetTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: (@Composable () -> Unit)? = null,
    singleLine: Boolean = false
) {
    val colors = LocalBudgetColors.current
    val shape = RoundedCornerShape(12.dp)
    Column(modifier) {
        label?.let { Box(Modifier.padding(bottom = 4.dp)) { it() } }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = BudgetTypography.bodyLarge.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.primary),
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(colors.surface)
                .border(1.dp, colors.outline, shape)
                .heightIn(min = 48.dp)
                .padding(horizontal = 12.dp, vertical = 13.dp)
        )
    }
}

// ---------- Checkbox and chips ----------

@Composable
fun BudgetCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val colors = LocalBudgetColors.current
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier
            .size(20.dp)
            .clip(shape)
            .background(if (checked) colors.primary else Color.Transparent)
            .border(1.dp, if (checked) colors.primary else colors.outline, shape)
            .toggleable(
                value = checked,
                enabled = onCheckedChange != null,
                role = Role.Checkbox
            ) { onCheckedChange?.invoke(!checked) },
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            BudgetText("✓", style = BudgetTypography.bodySmall, color = colors.onPrimary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun BudgetChip(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: @Composable () -> Unit
) {
    val colors = LocalBudgetColors.current
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier
            .heightIn(min = 32.dp)
            .clip(shape)
            .background(if (selected) colors.secondary.copy(alpha = 0.55f) else Color.Transparent)
            .border(1.dp, if (selected) colors.primary else colors.outline, shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) { label() }
    }
}

// ---------- Progress ----------

@Composable
fun BudgetSpinner(modifier: Modifier = Modifier, color: Color = LocalBudgetColors.current.primary, size: Dp = 40.dp) {
    val transition = rememberInfiniteTransition(label = "spinner")
    val angle = transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1000, easing = LinearEasing)),
        label = "spinner-angle"
    ).value
    Canvas(modifier.size(size)) {
        drawArc(
            color = color,
            startAngle = angle,
            sweepAngle = 270f,
            useCenter = false,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

// ---------- Dialog ----------

// UnstyledDialog + DialogPanel keep keyboard dismissal and pane semantics.
// The title is focusable so initial focus lands on the heading, not the first
// text field (which would open the IME immediately).
@Composable
fun BudgetDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    paneTitle: String = "Diálogo del presupuesto"
) {
    val colors = LocalBudgetColors.current
    UnstyledDialog(visible = true, onDismissRequest = onDismissRequest, overlay = { Scrim() }) {
        Box(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding(),
            contentAlignment = Alignment.Center
        ) {
            DialogPanel(
                modifier = modifier
                    .padding(20.dp)
                    .widthIn(max = 460.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.surface),
                paneTitle = paneTitle
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    title?.let {
                        CompositionLocalProvider(LocalBudgetTextStyle provides BudgetTypography.headlineSmall) {
                            Box(Modifier.focusable()) { it() }
                        }
                    }
                    if (text != null) {
                        CompositionLocalProvider(LocalBudgetTextStyle provides BudgetTypography.bodyMedium) {
                            // Long bodies (e.g. the category picker) scroll instead of clipping.
                            Box(
                                Modifier
                                    .weight(1f, fill = false)
                                    .heightIn(max = 420.dp)
                                    .verticalScroll(rememberScrollState())
                            ) { text() }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        dismissButton?.let {
                            it()
                            Spacer(Modifier.width(4.dp))
                        }
                        confirmButton()
                    }
                }
            }
        }
    }
}

// ---------- Overflow menu ----------

data class BudgetMenuItem(val label: String, val onClick: () -> Unit)

// Fullscreen focusable popup anchored to the window, not to its composition
// point: tapping the invisible scrim dismisses, back press dismisses through
// onDismissRequest, and the panel hangs below the top-end of the app window.
@Composable
fun BudgetOverflowMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    items: List<BudgetMenuItem>
) {
    if (!expanded) return
    val colors = LocalBudgetColors.current
    val fullscreen = remember {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset = IntOffset(0, 0)
        }
    }
    Popup(
        popupPositionProvider = fullscreen,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        val scrimInteraction = remember { MutableInteractionSource() }
        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = scrimInteraction,
                    indication = null
                ) { onDismiss() }
        ) {
            Column(
                Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 60.dp, end = 8.dp)
                    .width(230.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.outline, RoundedCornerShape(12.dp))
            ) {
                items.forEachIndexed { index, item ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                onDismiss()
                                item.onClick()
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        BudgetText(item.label, style = BudgetTypography.bodyLarge)
                    }
                    if (index != items.lastIndex) {
                        BudgetDivider()
                    }
                }
            }
        }
    }
}

// Scrollable content helper for calendar-style panels.
@Composable
fun BudgetScrollColumn(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
        content = content
    )
}
