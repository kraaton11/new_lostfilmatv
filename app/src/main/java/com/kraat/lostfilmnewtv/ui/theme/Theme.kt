package com.kraat.lostfilmnewtv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.darkColorScheme as tvDarkColorScheme

@Composable
fun LostFilmTheme(theme: AppTheme = AppTheme.CLASSIC, content: @Composable () -> Unit) {
    val colors = theme.colors()
    val materialScheme = remember(colors) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            secondary = colors.accentSecondary,
            onSecondary = colors.onAccentSecondary,
            background = colors.backgroundPrimary,
            surface = colors.backgroundSurface,
            surfaceVariant = colors.panelSurfaceStrong,
            onBackground = colors.textPrimary,
            onSurface = colors.textPrimary,
            outline = colors.panelBorder,
            error = colors.statusError,
        )
    }
    val tvScheme = remember(colors) {
        tvDarkColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            secondary = colors.accentSecondary,
            onSecondary = colors.onAccentSecondary,
            background = colors.backgroundPrimary,
            surface = colors.backgroundSurface,
            surfaceVariant = colors.panelSurfaceStrong,
            onBackground = colors.textPrimary,
            onSurface = colors.textPrimary,
            onSurfaceVariant = colors.textSecondary,
            inverseOnSurface = colors.backgroundPrimary,
            error = colors.statusError,
        )
    }
    CompositionLocalProvider(LocalLostFilmColors provides colors) {
        TvMaterialTheme(colorScheme = tvScheme) {
            MaterialTheme(colorScheme = materialScheme, content = content)
        }
    }
}
