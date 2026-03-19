package com.kraat.lostfilmnewtv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val LostFilmColorScheme = darkColorScheme(
    primary = AccentWarm,
    background = BackgroundPrimary,
    surface = BackgroundSurface,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onPrimary = BackgroundPrimary
)

@Composable
fun LostFilmTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LostFilmColorScheme,
        content = content,
    )
}
