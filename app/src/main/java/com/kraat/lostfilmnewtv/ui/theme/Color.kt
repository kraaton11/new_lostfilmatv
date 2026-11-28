package com.kraat.lostfilmnewtv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class LostFilmColors(
    val backgroundPrimary: Color,
    val backgroundSurface: Color,
    val textPrimary: Color,
    val panelSurface: Color,
    val panelSurfaceStrong: Color,
    val panelBorder: Color,
    val panelBorderFocus: Color,
    val focusBorder: Color,
    val focusBackground: Color,
    val accent: Color,
    val accentGlow: Color,
    val accentSecondary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val statusError: Color,
    val detailsBackgroundTop: Color,
    val detailsBackgroundMid: Color,
    val onAccent: Color,
    val onAccentMuted: Color,
    val onAccentSecondary: Color,
    val scrim: Color,
    val highlight: Color,
    val errorSurface: Color,
    val statusSuccess: Color,
    val statusDanger: Color,
    val scheduleSurface: Color,
    val scheduleSurfaceEnd: Color,
    val homeBackgroundTop: Color,
    val homeBackgroundMid: Color,
    val headerSurfaceTop: Color,
    val headerSurfaceEnd: Color,
    val posterPlaceholderTop: Color,
    val posterPlaceholderMid: Color,
    val posterPlaceholderEnd: Color,
)

internal val ClassicColors = LostFilmColors(
    backgroundPrimary = Color(0xFF040A11),
    backgroundSurface = Color(0xFF0B1521),
    textPrimary = Color(0xFFF7F1E8),
    panelSurface = Color(0xB308111A),
    panelSurfaceStrong = Color(0xE60D1825),
    panelBorder = Color(0x1FFFFFFF),
    panelBorderFocus = Color(0x80D9B46D),
    focusBorder = Color(0xFFE8C982),
    focusBackground = Color(0xFF172433),
    accent = Color(0xFFD7AA5D),
    accentGlow = Color(0xFFFFDFA0),
    accentSecondary = Color(0xFF8DB7D8),
    textSecondary = Color(0xFFD2D9E2),
    textMuted = Color(0xFF8793A3),
    statusError = Color(0xFFE07060),
    detailsBackgroundTop = Color(0xFF0B1825),
    detailsBackgroundMid = Color(0xFF050C14),
    onAccent = Color(0xFF17120D),
    onAccentMuted = Color(0xFF473317),
    onAccentSecondary = Color(0xFF07111B),
    scrim = Color(0xFF040A11),
    highlight = Color(0xFFFFFFFF),
    errorSurface = Color(0xCC2A0E10),
    statusSuccess = Color(0xFF59D58B),
    statusDanger = Color(0xFFFF636B),
    scheduleSurface = Color(0xFF172636),
    scheduleSurfaceEnd = Color(0xFF0B1320),
    homeBackgroundTop = Color(0xFF07111B),
    homeBackgroundMid = Color(0xFF061018),
    headerSurfaceTop = Color(0xFF0A1420),
    headerSurfaceEnd = Color(0xFF050B12),
    posterPlaceholderTop = Color(0xFF3F586E),
    posterPlaceholderMid = Color(0xFF172734),
    posterPlaceholderEnd = Color(0xFF0A131B),
)

val LocalLostFilmColors = staticCompositionLocalOf { ClassicColors }

val BackgroundPrimary: Color
    @Composable @ReadOnlyComposable get() = LocalLostFilmColors.current.backgroundPrimary

val BackgroundSurface: Color
    @Composable @ReadOnlyComposable get() = LocalLostFilmColors.current.backgroundSurface

val TextPrimary: Color
    @Composable @ReadOnlyComposable get() = LocalLostFilmColors.current.textPrimary

val HomePanelSurface: Color
    @Composable @ReadOnlyComposable get() = LocalLostFilmColors.current.panelSurface

val HomePanelSurfaceStrong: Color
    @Composable @ReadOnlyComposable get() = LocalLostFilmColors.current.panelSurfaceStrong

val HomePanelBorder: Color
    @Composable @ReadOnlyComposable get() = LocalLostFilmColors.current.panelBorder

val HomePanelBorderFocus: Color
    @Composable @ReadOnlyComposable get() = LocalLostFilmColors.current.panelBorderFocus

val FocusBorder: Color
    @Composable @ReadOnlyComposable get() = LocalLostFilmColors.current.focusBorder

val FocusBackground: Color
    @Composable @ReadOnlyComposable get() = LocalLostFilmColors.current.focusBackground

val HomeAccentGold: Color
    @Composable @ReadOnlyComposable get() = LocalLostFilmColors.current.accent

val HomeAccentGoldGlow: Color
    @Composable @ReadOnlyComposable get() = LocalLostFilmColors.current.accentGlow

val HomeAccentBlue: Color
    @Composable @ReadOnlyComposable get() = LocalLostFilmColors.current.accentSecondary

val HomeTextSecondary: Color
    @Composable @ReadOnlyComposable get() = LocalLostFilmColors.current.textSecondary

val HomeTextMuted: Color
    @Composable @ReadOnlyComposable get() = LocalLostFilmColors.current.textMuted

val HomeStatusError: Color
    @Composable @ReadOnlyComposable get() = LocalLostFilmColors.current.statusError

internal val DetailsBackgroundTop: Color
    @Composable @ReadOnlyComposable get() = LocalLostFilmColors.current.detailsBackgroundTop

internal val DetailsBackgroundMid: Color
    @Composable @ReadOnlyComposable get() = LocalLostFilmColors.current.detailsBackgroundMid

internal val DetailsAccentGold: Color
    @Composable @ReadOnlyComposable get() = LocalLostFilmColors.current.accent

internal val DetailsAccentGoldFocus: Color
    @Composable @ReadOnlyComposable get() = LocalLostFilmColors.current.accentGlow

internal val DetailsAccentBlue: Color
    @Composable @ReadOnlyComposable get() = LocalLostFilmColors.current.accentSecondary

internal val DetailsSurfaceCard: Color
    @Composable @ReadOnlyComposable get() = LocalLostFilmColors.current.panelSurface

internal val DetailsSurfaceSoft: Color
    @Composable @ReadOnlyComposable get() = LocalLostFilmColors.current.panelSurface

internal val DetailsSurfaceFocused: Color
    @Composable @ReadOnlyComposable get() = LocalLostFilmColors.current.panelSurfaceStrong

internal val DetailsSurfaceReadable: Color
    @Composable @ReadOnlyComposable get() = LocalLostFilmColors.current.panelSurfaceStrong

internal val DetailsBorderDefault: Color
    @Composable @ReadOnlyComposable get() = LocalLostFilmColors.current.panelBorder

internal val DetailsTextSecondary: Color
    @Composable @ReadOnlyComposable get() = LocalLostFilmColors.current.textSecondary

internal val DetailsTextMuted: Color
    @Composable @ReadOnlyComposable get() = LocalLostFilmColors.current.textMuted

internal val DetailsStatusError: Color
    @Composable @ReadOnlyComposable get() = LocalLostFilmColors.current.statusError
