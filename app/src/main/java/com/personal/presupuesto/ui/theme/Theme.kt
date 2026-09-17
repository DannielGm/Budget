package com.personal.presupuesto.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFFD96525),
    onPrimary = Color.White,
    secondary = Color(0xFFE89A52),
    tertiary = Color(0xFFFFD9BD),
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFFFF9F4),
    surfaceVariant = Color(0xFFFFEEE3),
    onSurface = Color(0xFF1E1A17),
    onSurfaceVariant = Color(0xFF6A5349),
    outline = Color(0xFFE9B998)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF1B1178),
    onPrimary = Color(0xFFF3EDFF),
    secondary = Color(0xFF9B92E8),
    tertiary = Color(0xFF7EE7D2),
    background = Color(0xFF0D0D12),
    surface = Color(0xFF17171D),
    surfaceVariant = Color(0xFF211F2A),
    onSurface = Color(0xFFEDEBFF),
    onSurfaceVariant = Color(0xFFCFC7FF),
    outline = Color(0xFF39327A)
)

@Composable
fun PresupuestoTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
