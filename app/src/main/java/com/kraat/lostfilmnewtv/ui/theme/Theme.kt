package com.kraat.lostfilmnewtv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val LostFilmColorScheme = darkColorScheme(
    background = BackgroundPrimary,
    surface = BackgroundSurface,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
)

@Composable
fun LostFilmTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LostFilmColorScheme,
        content = content,
    )
}
