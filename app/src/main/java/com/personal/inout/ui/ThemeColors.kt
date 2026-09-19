package com.personal.inout.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

enum class AppThemeMode {
    AMBER_OCHRE,
    OLIVE_MATCHA,
    NORDIC_SLATE
}

data class ThemeColors(
    val bg: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val accent: Color,
    val textBright: Color,
    val textMuted: Color,
    val mildGreen: Color,
    val mildRed: Color
)

val AmberTheme = ThemeColors(
    bg = Color(0xFF11100E),
    surface = Color(0xFF1A1815),
    surfaceAlt = Color(0xFF26231E),
    accent = Color(0xFFE2A755),
    textBright = Color(0xFFF2EFE9),
    textMuted = Color(0xFF918A80),
    mildGreen = Color(0xFF5CB881),
    mildRed = Color(0xFFD9534F)
)

val OliveMatchaTheme = ThemeColors(
    bg = Color(0xFF0F1411),
    surface = Color(0xFF161E1A),
    surfaceAlt = Color(0xFF202A24),
    accent = Color(0xFF88C999),
    textBright = Color(0xFFEFF5F0),
    textMuted = Color(0xFF7E9485),
    mildGreen = Color(0xFF67B982),
    mildRed = Color(0xFFCF5C56)
)

val NordicSlateTheme = ThemeColors(
    bg = Color(0xFF0E131A),
    surface = Color(0xFF161E28),
    surfaceAlt = Color(0xFF212B38),
    accent = Color(0xFF5BA4E6),
    textBright = Color(0xFFF0F4FA),
    textMuted = Color(0xFF7F91A8),
    mildGreen = Color(0xFF48BF91),
    mildRed = Color(0xFFE05D67)
)

val LocalThemeColors = compositionLocalOf { AmberTheme }
