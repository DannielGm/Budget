package com.personal.presupuesto.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B6B59),
    onPrimary = Color.White,
    secondary = Color(0xFFB65F33),
    background = Color(0xFFF7F8F4),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE8EEE8),
    onSurface = Color(0xFF1A211D),
    onSurfaceVariant = Color(0xFF59635D)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF75D5B8),
    secondary = Color(0xFFFFB58D),
    background = Color(0xFF101512),
    surface = Color(0xFF19201C),
    surfaceVariant = Color(0xFF303A34)
)

@Composable
fun PresupuestoTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
