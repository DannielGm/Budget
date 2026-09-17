package com.personal.presupuesto.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFFFF8A3D),
    onPrimary = Color.White,
    secondary = Color(0xFFFFC27A),
    tertiary = Color(0xFFFFE5D0),
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFFFF9F4),
    surfaceVariant = Color(0xFFFFF1E7),
    onSurface = Color(0xFF1E1A17),
    onSurfaceVariant = Color(0xFF6A5349),
    outline = Color(0xFFFFD7B8)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9B7BFF),
    onPrimary = Color(0xFF120D22),
    secondary = Color(0xFFB98BFF),
    tertiary = Color(0xFF7EE7D2),
    background = Color(0xFF0D0D12),
    surface = Color(0xFF17171D),
    surfaceVariant = Color(0xFF242430),
    onSurface = Color(0xFFEDEBFF),
    onSurfaceVariant = Color(0xFFCFC7FF),
    outline = Color(0xFF413B5A)
)

@Composable
fun PresupuestoTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
