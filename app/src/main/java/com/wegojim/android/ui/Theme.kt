package com.wegojim.android.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Colors from WGJTheme.swift that have no Material slot. */
@Immutable
data class WgjExtraColors(
    val success: Color,
    val warning: Color,
    val gold: Color,
    val cyan: Color,
    val textTertiary: Color,
    val field: Color,
)

private val LightExtra = WgjExtraColors(
    success = Color(0xFF1A9D6F),
    warning = Color(0xFFC58A2E),
    gold = Color(0xFFB48A2D),
    cyan = Color(0xFF2CB8D9),
    textTertiary = Color(0xFF7F8A98),
    field = Color(0xFFEEF3F8),
)

private val DarkExtra = WgjExtraColors(
    success = Color(0xFF62D8A6),
    warning = Color(0xFFE6BE73),
    gold = Color(0xFFE0C56B),
    cyan = Color(0xFF7CE3EF),
    textTertiary = Color(0xFF7B8795),
    field = Color(0xFF111A23),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF1E86FF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E8FF),
    onPrimaryContainer = Color(0xFF0D1520),
    secondary = Color(0xFF2CB8D9),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDE7F0),
    onSecondaryContainer = Color(0xFF0D1520),
    tertiary = Color(0xFFB48A2D),
    error = Color(0xFFD65A5A),
    errorContainer = Color(0xFFFBEDEE),
    background = Color(0xFFF4F7FA),
    onBackground = Color(0xFF0D1520),
    surface = Color(0xFFF4F7FA),
    onSurface = Color(0xFF0D1520),
    onSurfaceVariant = Color(0xFF566476),
    surfaceVariant = Color(0xFFE6EDF4),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEEF4F9),
    surfaceContainerHighest = Color(0xFFE6EDF4),
    outline = Color(0xFFB9C4D0),
    outlineVariant = Color(0xFFDDE7F0),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF6DB5FF),
    onPrimary = Color(0xFF0B1016),
    primaryContainer = Color(0xFF1B3A5C),
    onPrimaryContainer = Color(0xFFF5F7FA),
    secondary = Color(0xFF7CE3EF),
    onSecondary = Color(0xFF0B1016),
    secondaryContainer = Color(0xFF22303D),
    onSecondaryContainer = Color(0xFFF5F7FA),
    tertiary = Color(0xFFE0C56B),
    error = Color(0xFFFF8484),
    errorContainer = Color(0xFF2C181B),
    background = Color(0xFF0A1016),
    onBackground = Color(0xFFF5F7FA),
    surface = Color(0xFF0A1016),
    onSurface = Color(0xFFF5F7FA),
    onSurfaceVariant = Color(0xFFA4AFBC),
    surfaceVariant = Color(0xFF18232E),
    surfaceContainerLowest = Color(0xFF0A1016),
    surfaceContainerLow = Color(0xFF111922),
    surfaceContainer = Color(0xFF16202A),
    surfaceContainerHigh = Color(0xFF1B2733),
    surfaceContainerHighest = Color(0xFF22303D),
    outline = Color(0xFF3A4856),
    outlineVariant = Color(0xFF22303D),
)

val LocalWgjColors = staticCompositionLocalOf { DarkExtra }

object Wgj {
    val colors: WgjExtraColors
        @Composable get() = LocalWgjColors.current
}

@Composable
fun WgjTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalWgjColors provides if (darkTheme) DarkExtra else LightExtra) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            shapes = Shapes(
                small = RoundedCornerShape(12.dp),
                medium = RoundedCornerShape(14.dp),
                large = RoundedCornerShape(20.dp),
            ),
            content = content,
        )
    }
}
