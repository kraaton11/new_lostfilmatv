package com.kraat.lostfilmnewtv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LostFilmColorScheme = darkColorScheme(
    primary = HomeAccentGold,
    onPrimary = Color(0xFF17110A),
    secondary = HomeAccentBlue,
    onSecondary = Color(0xFF07111B),
    background = BackgroundPrimary,
    surface = BackgroundSurface,
    surfaceVariant = HomePanelSurfaceStrong,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    outline = HomePanelBorder,
)

@Composable
fun LostFilmTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LostFilmColorScheme,
        content = content,
    )
}
