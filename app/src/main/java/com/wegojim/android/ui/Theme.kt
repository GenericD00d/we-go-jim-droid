package com.wegojim.android.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
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
import java.time.LocalDate
import java.time.Month

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

/** A light/dark color pair, written as 0xRRGGBB like the iOS palette. */
private data class Dyn(val light: Long, val dark: Long) {
    fun pick(dark: Boolean) = Color(0xFF000000 or if (dark) this.dark else light)
}

/** Port of WGJAppTheme and its palettes. */
enum class AppTheme(val title: String, val subtitle: String) {
    original("WGJ Original", "The classic. Cool blues, strong foundations."),
    mintCondition("Mint Condition", "Fresh mint. Fresh set. Same heavy weights."),
    wheyTooPurple("Whey Too Purple", "A little extra on the lavender gains."),
    sunsOutGunsOut("Sun’s Out, Guns Out", "Golden-hour energy for every rep."),
    electricStrength("Electric Strength", "Carbon. Chalk. Electric lime. Built for your next best."),
    christmas("Sleigh Day", "Deck the halls. Lift the weights. Only in December.");

    val isAvailable: Boolean get() = this != christmas || LocalDate.now().month == Month.DECEMBER

    companion object {
        fun of(name: String?): AppTheme = entries.firstOrNull { it.name == name }?.takeIf { it.isAvailable } ?: original
    }
}

private class Palette(
    val base: Dyn, val surface: Dyn, val raised: Dyn,
    val primary: Dyn, val secondary: Dyn, val tertiary: Dyn,
    val text: Dyn = Dyn(0x0D1520, 0xF5F7FA),
    val textSecondary: Dyn = Dyn(0x566476, 0xA4AFBC),
    val gold: Dyn = Dyn(0xB48A2D, 0xE0C56B),
    val card: Dyn = Dyn(0xFFFFFF, surface.dark),
    val field: Dyn = Dyn(surface.light, base.dark),
)

private val originalPalette = Palette(
    base = Dyn(0xF4F7FA, 0x0A1016), surface = Dyn(0xEEF4F9, 0x16202A), raised = Dyn(0xE6EDF4, 0x22303D),
    primary = Dyn(0x1E86FF, 0x6DB5FF), secondary = Dyn(0x2CB8D9, 0x7CE3EF), tertiary = Dyn(0xA4B5FF, 0x9FB0FF),
    card = Dyn(0xFFFFFF, 0x16202A), field = Dyn(0xEEF3F8, 0x111A23),
)

private fun palette(theme: AppTheme): Palette = when (theme) {
    AppTheme.original -> originalPalette
    AppTheme.mintCondition -> Palette(
        Dyn(0xF0F8F5, 0x091713), Dyn(0xE1F0E9, 0x142A23), Dyn(0xD3E8DF, 0x203C32),
        Dyn(0x087B60, 0x80E3BC), Dyn(0x147E83, 0x94DBD8), Dyn(0x779749, 0xC5DCA0),
    )
    AppTheme.wheyTooPurple -> Palette(
        Dyn(0xF7F3FC, 0x151020), Dyn(0xEDE4F6, 0x261D36), Dyn(0xE1D6EF, 0x372A48),
        Dyn(0x7751BD, 0xC9ACFF), Dyn(0x92508B, 0xEABCE6), Dyn(0x6466AD, 0xB5BBFA),
    )
    AppTheme.sunsOutGunsOut -> Palette(
        Dyn(0xFCF5EF, 0x1B120F), Dyn(0xF5E7DA, 0x30221C), Dyn(0xEED8C5, 0x453126),
        Dyn(0xAB552C, 0xFFB58C), Dyn(0x94651D, 0xF1CF8C), Dyn(0xAE6370, 0xE9ABB5),
    )
    AppTheme.electricStrength -> Palette(
        Dyn(0xF3F6EF, 0x101510), Dyn(0xE8EDE2, 0x1C241B), Dyn(0xDCE3D5, 0x2A3427),
        Dyn(0x4E6810, 0xD2FF5A), Dyn(0x536A2D, 0xB6D97A), Dyn(0x5C6B52, 0xA7B1A3),
        text = Dyn(0x101510, 0xF3F6EF), textSecondary = Dyn(0x505D49, 0xA7B1A3),
    )
    AppTheme.christmas -> Palette(
        Dyn(0xFBF6E9, 0x06180F), Dyn(0xE9EDDE, 0x102C1C), Dyn(0xDCE5CF, 0x1D3D28),
        Dyn(0x825719, 0xF3CA6C), Dyn(0x825719, 0xFFE5A3), Dyn(0xA31D32, 0xFF6675),
        text = Dyn(0x162C23, 0xFFF8E7), textSecondary = Dyn(0x4D6055, 0xB7CDBE), gold = Dyn(0x826019, 0xF5D58C),
    )
}

private fun scheme(p: Palette, dark: Boolean): ColorScheme {
    val onAccent = if (dark) p.base.pick(true) else Color.White
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = p.primary.pick(dark),
        onPrimary = onAccent,
        primaryContainer = p.raised.pick(dark),
        onPrimaryContainer = p.text.pick(dark),
        secondary = p.secondary.pick(dark),
        onSecondary = onAccent,
        secondaryContainer = p.raised.pick(dark),
        onSecondaryContainer = p.text.pick(dark),
        tertiary = p.tertiary.pick(dark),
        error = Dyn(0xD65A5A, 0xFF8484).pick(dark),
        errorContainer = Dyn(0xFBEDEE, 0x2C181B).pick(dark),
        background = p.base.pick(dark),
        onBackground = p.text.pick(dark),
        surface = p.base.pick(dark),
        onSurface = p.text.pick(dark),
        onSurfaceVariant = p.textSecondary.pick(dark),
        surfaceVariant = p.raised.pick(dark),
        surfaceContainerLowest = p.base.pick(dark),
        surfaceContainerLow = p.card.pick(dark),
        surfaceContainer = p.card.pick(dark),
        surfaceContainerHigh = p.surface.pick(dark).takeIf { !dark } ?: p.raised.pick(true),
        surfaceContainerHighest = p.raised.pick(dark),
        outline = if (dark) Color(0xFF3A4856) else Color(0xFFB9C4D0),
        outlineVariant = p.raised.pick(dark),
    )
}

val LocalWgjColors = staticCompositionLocalOf {
    WgjExtraColors(Color(0xFF62D8A6), Color(0xFFE6BE73), Color(0xFFE0C56B), Color(0xFF7CE3EF), Color(0xFF7B8795), Color(0xFF111A23))
}

object Wgj {
    val colors: WgjExtraColors
        @Composable get() = LocalWgjColors.current
}

@Composable
fun WgjTheme(theme: AppTheme = AppTheme.original, darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val p = palette(theme)
    val extra = WgjExtraColors(
        success = Dyn(0x1A9D6F, 0x62D8A6).pick(darkTheme),
        warning = Dyn(0xC58A2E, 0xE6BE73).pick(darkTheme),
        gold = p.gold.pick(darkTheme),
        cyan = p.secondary.pick(darkTheme),
        textTertiary = Dyn(0x7F8A98, 0x7B8795).pick(darkTheme),
        field = p.field.pick(darkTheme),
    )
    CompositionLocalProvider(LocalWgjColors provides extra) {
        MaterialTheme(
            colorScheme = scheme(p, darkTheme),
            shapes = Shapes(
                small = RoundedCornerShape(12.dp),
                medium = RoundedCornerShape(14.dp),
                large = RoundedCornerShape(20.dp),
            ),
            content = content,
        )
    }
}
