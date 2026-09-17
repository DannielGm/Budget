package com.personal.presupuesto.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.takeOrElse

// App-owned design tokens. No Material theme: components read these directly.
data class BudgetColors(
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val tertiary: Color,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val error: Color
)

val LocalBudgetColors = staticCompositionLocalOf {
    LightBudgetColors
}

// Components with a Text-less default read this so containers can restyle their children.
val LocalBudgetTextStyle = staticCompositionLocalOf { BudgetTypography.bodyLarge }

// Current content color inside styled containers (buttons set onPrimary, etc.).
val LocalBudgetContentColor = staticCompositionLocalOf { Color.Unspecified }

object BudgetTypography {
    val headlineLarge = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Normal)
    val headlineSmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Normal)
    val titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Normal)
    val titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium)
    val bodyLarge = TextStyle(fontSize = 16.sp)
    val bodyMedium = TextStyle(fontSize = 14.sp)
    val bodySmall = TextStyle(fontSize = 12.sp)
    val labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium)
    val labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium)
    val labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium)
}

// Single access point so screen code reads tokens without MaterialTheme.
object BudgetTheme {
    val colors: BudgetColors
        @Composable get() = LocalBudgetColors.current
    val typography: BudgetTypography
        get() = BudgetTypography
}

private val LightBudgetColors = BudgetColors(
    primary = Color(0xFFFF8A3D),
    onPrimary = Color.White,
    secondary = Color(0xFFFFC27A),
    tertiary = Color(0xFFFFE5D0),
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFFFF9F4),
    surfaceVariant = Color(0xFFFFF1E7),
    onSurface = Color(0xFF1E1A17),
    onSurfaceVariant = Color(0xFF6A5349),
    outline = Color(0xFFFFD7B8),
    error = Color(0xFFB3261E)
)

private val DarkBudgetColors = BudgetColors(
    primary = Color(0xFF9B7BFF),
    onPrimary = Color(0xFF120D22),
    secondary = Color(0xFFB98BFF),
    tertiary = Color(0xFF7EE7D2),
    background = Color(0xFF0D0D12),
    surface = Color(0xFF17171D),
    surfaceVariant = Color(0xFF242430),
    onSurface = Color(0xFFEDEBFF),
    onSurfaceVariant = Color(0xFFCFC7FF),
    outline = Color(0xFF413B5A),
    error = Color(0xFFF2B8B5)
)

@Composable
fun PresupuestoTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkBudgetColors else LightBudgetColors
    CompositionLocalProvider(
        LocalBudgetColors provides colors,
        LocalBudgetContentColor provides colors.onSurface
    ) {
        Box(Modifier.fillMaxSize().background(colors.background)) {
            content()
        }
    }
}

