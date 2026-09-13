package com.personal.inout.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

enum class AppThemeMode {
    AMBER_OCHRE,
    OLIVE_MATCHA,
    SAND_DUNE
}

data class ThemeColors(
    val bg: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val accent: Color,
    val accentDim: Color,
    val mildGreen: Color,
    val mildRed: Color,
    val textMuted: Color,
    val textBright: Color
)

val AmberTheme = ThemeColors(
    bg = Color(0xFF161412),
    surface = Color(0xFF221F1B),
    surfaceAlt = Color(0xFF2E2A25),
    accent = Color(0xFFDEAC64),
    accentDim = Color(0xFF9E7C48),
    mildGreen = Color(0xFF81C784),
    mildRed = Color(0xFFE57373),
    textMuted = Color(0xFFA69E94),
    textBright = Color(0xFFF3EFEB)
)

val OliveMatchaTheme = ThemeColors(
    bg = Color(0xFF131714),
    surface = Color(0xFF1C221D),
    surfaceAlt = Color(0xFF262E28),
    accent = Color(0xFFA3C9A8),
    accentDim = Color(0xFF6E8E73),
    mildGreen = Color(0xFF81C784),
    mildRed = Color(0xFFE57373),
    textMuted = Color(0xFF97A398),
    textBright = Color(0xFFEDF2EE)
)

val SandDuneTheme = ThemeColors(
    bg = Color(0xFF181715),
    surface = Color(0xFF252420),
    surfaceAlt = Color(0xFF33312B),
    accent = Color(0xFFD4B982),
    accentDim = Color(0xFF968257),
    mildGreen = Color(0xFF81C784),
    mildRed = Color(0xFFE57373),
    textMuted = Color(0xFFA39F97),
    textBright = Color(0xFFF5F3EF)
)

val LocalThemeColors = compositionLocalOf { AmberTheme }
