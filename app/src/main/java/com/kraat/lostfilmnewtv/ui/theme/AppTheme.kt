package com.kraat.lostfilmnewtv.ui.theme

import androidx.compose.ui.graphics.Color

enum class AppTheme(val storageValue: String) {
    CLASSIC("classic"),
    AMOLED("amoled"),
    BLUE("blue"),
    NEON("neon");

    companion object {
        fun fromStorageValue(value: String?): AppTheme =
            entries.firstOrNull { it.storageValue == value } ?: CLASSIC
    }
}

internal fun AppTheme.colors(): LostFilmColors = when (this) {
    AppTheme.CLASSIC -> ClassicColors
    AppTheme.AMOLED -> AmoledColors
    AppTheme.BLUE -> BlueColors
    AppTheme.NEON -> NeonColors
}

private val AmoledColors = ClassicColors.copy(
    backgroundPrimary = Color(0xFF000000),
    backgroundSurface = Color(0xFF101010),
    panelSurface = Color(0xB3101010),
    panelSurfaceStrong = Color(0xE61A1A1A),
    focusBackground = Color(0xFF282828),
    detailsBackgroundTop = Color(0xFF0B0B0B),
    detailsBackgroundMid = Color(0xFF050505),
    scrim = Color(0xFF000000),
    scheduleSurface = Color(0xFF242424),
    scheduleSurfaceEnd = Color(0xFF101010),
    homeBackgroundTop = Color(0xFF080808),
    homeBackgroundMid = Color(0xFF030303),
    headerSurfaceTop = Color(0xFF1A1A1A),
    headerSurfaceEnd = Color(0xFF080808),
    posterPlaceholderTop = Color(0xFF454545),
    posterPlaceholderMid = Color(0xFF252525),
    posterPlaceholderEnd = Color(0xFF101010),
)

private val BlueColors = ClassicColors.copy(
    backgroundPrimary = Color(0xFF071121),
    backgroundSurface = Color(0xFF10223A),
    textPrimary = Color(0xFFEEF6FF),
    textSecondary = Color(0xFFC7D8EB),
    textMuted = Color(0xFF94ADCA),
    panelSurface = Color(0xB310223A),
    panelSurfaceStrong = Color(0xE619304D),
    panelBorderFocus = Color(0x8086BFFF),
    focusBorder = Color(0xFFB6D9FF),
    focusBackground = Color(0xFF203E60),
    accent = Color(0xFF83B9FF),
    accentGlow = Color(0xFFC0DFFF),
    accentSecondary = Color(0xFF83DBE3),
    onAccent = Color(0xFF09182C),
    onAccentMuted = Color(0xFF203D62),
    onAccentSecondary = Color(0xFF092125),
    detailsBackgroundTop = Color(0xFF132941),
    detailsBackgroundMid = Color(0xFF0B192C),
    scrim = Color(0xFF071121),
    highlight = Color(0xFFEBF5FF),
    scheduleSurface = Color(0xFF203C5C),
    scheduleSurfaceEnd = Color(0xFF12233A),
    homeBackgroundTop = Color(0xFF10243C),
    homeBackgroundMid = Color(0xFF0A1A2D),
    headerSurfaceTop = Color(0xFF16314D),
    headerSurfaceEnd = Color(0xFF0B192C),
    posterPlaceholderTop = Color(0xFF3C608A),
    posterPlaceholderMid = Color(0xFF244266),
    posterPlaceholderEnd = Color(0xFF102139),
)

private val NeonColors = ClassicColors.copy(
    backgroundPrimary = Color(0xFF0D0E15),
    backgroundSurface = Color(0xFF181624),
    textPrimary = Color(0xFFF3F0FF),
    textSecondary = Color(0xFFDAD4EE),
    textMuted = Color(0xFFA6A4BF),
    panelSurface = Color(0xB3181624),
    panelSurfaceStrong = Color(0xE6211D31),
    panelBorder = Color(0x34BDA6F5),
    panelBorderFocus = Color(0xA046FFDC),
    focusBorder = Color(0xFFA3FFEF),
    focusBackground = Color(0xFF302747),
    accent = Color(0xFF46FFDC),
    accentGlow = Color(0xFFA3FFEF),
    accentSecondary = Color(0xFFCE9BFF),
    onAccent = Color(0xFF08201D),
    onAccentMuted = Color(0xFF19453D),
    onAccentSecondary = Color(0xFF210F35),
    detailsBackgroundTop = Color(0xFF201832),
    detailsBackgroundMid = Color(0xFF141221),
    scrim = Color(0xFF0D0E15),
    highlight = Color(0xFFE8D8FF),
    scheduleSurface = Color(0xFF2D2442),
    scheduleSurfaceEnd = Color(0xFF191526),
    homeBackgroundTop = Color(0xFF1C172A),
    homeBackgroundMid = Color(0xFF14131F),
    headerSurfaceTop = Color(0xFF251C38),
    headerSurfaceEnd = Color(0xFF14121E),
    posterPlaceholderTop = Color(0xFF594272),
    posterPlaceholderMid = Color(0xFF322843),
    posterPlaceholderEnd = Color(0xFF181525),
)
