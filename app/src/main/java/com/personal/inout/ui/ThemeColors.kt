package com.personal.inout.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

enum class AppThemeMode {
    PALE_AMBER,
    MATCHA_OLIVE,
    DESERT_SAND,
    NORDIC_SLATE
}

data class ThemeColors(
    val bg: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val surfaceHover: Color,
    val accent: Color,
    val accentDim: Color = accent.copy(alpha = 0.18f),
    val textBright: Color,
    val textMuted: Color,
    val mildGreen: Color,
    val mildRed: Color,
    val borderLight: Color
)

/**
 * Pale Amber / Warm Earth: Subdued, comfortable dark amber tones.
 */
val PaleAmberTheme = ThemeColors(
    bg = Color(0xFF141311),
    surface = Color(0xFF1D1B18),
    surfaceAlt = Color(0xFF282521),
    surfaceHover = Color(0xFF332F2A),
    accent = Color(0xFFDEC186),
    accentDim = Color(0xFFDEC186).copy(alpha = 0.18f),
    textBright = Color(0xFFF5F2EB),
    textMuted = Color(0xFFA39B8F),
    mildGreen = Color(0xFF7FA884),
    mildRed = Color(0xFFC77A73),
    borderLight = Color(0xFF36322C)
)

/**
 * Matcha Olive: Calming organic Japanese matcha and herbal olive greens.
 */
val MatchaOliveTheme = ThemeColors(
    bg = Color(0xFF111412),
    surface = Color(0xFF181E1A),
    surfaceAlt = Color(0xFF222B25),
    surfaceHover = Color(0xFF2D3831),
    accent = Color(0xFF98B893),
    accentDim = Color(0xFF98B893).copy(alpha = 0.18f),
    textBright = Color(0xFFEFF3EE),
    textMuted = Color(0xFF8D9C90),
    mildGreen = Color(0xFF88B38E),
    mildRed = Color(0xFFBE7872),
    borderLight = Color(0xFF2F3B33)
)

/**
 * Desert Sand: Warm clay, ochre, and soft parchment desert aesthetics.
 */
val DesertSandTheme = ThemeColors(
    bg = Color(0xFF151311),
    surface = Color(0xFF1F1C18),
    surfaceAlt = Color(0xFF2C2722),
    surfaceHover = Color(0xFF38332D),
    accent = Color(0xFFD4B08C),
    accentDim = Color(0xFFD4B08C).copy(alpha = 0.18f),
    textBright = Color(0xFFF7F3EE),
    textMuted = Color(0xFFA69C91),
    mildGreen = Color(0xFF86A88B),
    mildRed = Color(0xFFC77870),
    borderLight = Color(0xFF3D3730)
)

/**
 * Nordic Slate: Neutral cool stone, muted blue-grey slate with soft steel highlights.
 */
val NordicSlateTheme = ThemeColors(
    bg = Color(0xFF101317),
    surface = Color(0xFF181D24),
    surfaceAlt = Color(0xFF222933),
    surfaceHover = Color(0xFF2C3542),
    accent = Color(0xFF8BA7C7),
    accentDim = Color(0xFF8BA7C7).copy(alpha = 0.18f),
    textBright = Color(0xFFEDF2F7),
    textMuted = Color(0xFF8C98A8),
    mildGreen = Color(0xFF75A98D),
    mildRed = Color(0xFFC4767E),
    borderLight = Color(0xFF313B4A)
)

val LocalThemeColors = compositionLocalOf { PaleAmberTheme }
