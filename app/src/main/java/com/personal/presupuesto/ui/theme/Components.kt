package com.personal.presupuesto.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.composeunstyled.UnstyledButton

// App-owned styling; Unstyled supplies keyboard activation and button semantics.
@Composable
fun BudgetButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    outlined: Boolean = false,
    quiet: Boolean = false,
    border: androidx.compose.foundation.BorderStroke? = null,
    content: @Composable RowScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    val background = if (outlined || quiet) Color.Transparent else colors.primary
    val foreground = if (outlined || quiet) colors.primary else colors.onPrimary
    UnstyledButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp).clip(shape)
            .background(background.copy(alpha = if (enabled) background.alpha else background.alpha * 0.35f))
            .then(if (outlined) Modifier.border(border ?: androidx.compose.foundation.BorderStroke(1.dp, colors.outline), shape) else Modifier),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        CompositionLocalProvider(LocalContentColor provides foreground.copy(alpha = if (enabled) 1f else 0.38f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, content = content)
        }
    }
}

@Composable
fun BudgetTextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) =
    BudgetButton(onClick, modifier, enabled, quiet = true, content = content)

@Composable
fun BudgetOutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, border: androidx.compose.foundation.BorderStroke? = null, content: @Composable RowScope.() -> Unit) =
    BudgetButton(onClick, modifier, enabled, outlined = true, border = border, content = content)
