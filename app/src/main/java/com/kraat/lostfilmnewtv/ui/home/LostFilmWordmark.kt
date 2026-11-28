package com.kraat.lostfilmnewtv.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kraat.lostfilmnewtv.ui.theme.LocalLostFilmColors

private val LostOutline = PathParser().parsePathString(
    // L
    "M0 0H5.4V15.9H13V21H0Z " +
        // O
        "M21 0H26Q32 0 32 6V15Q32 21 26 21H21Q15 21 15 15V6Q15 0 21 0Z " +
        "M22 5Q20.3 5 20.3 6.7V14.3Q20.3 16 22 16H25Q26.7 16 26.7 14.3V6.7Q26.7 5 25 5Z " +
        // S
        "M50 0V5H41Q39.2 5 39.2 6.6Q39.2 8.1 41 8.1H44Q50.4 8.1 50.4 14.5" +
        "Q50.4 21 44 21H34V16H43.6Q45.1 16 45.1 14.5Q45.1 13 43.6 13H40.6" +
        "Q34 13 34 6.6Q34 0 40.6 0Z " +
        // T
        "M52 0H67V5.1H62.2V21H56.8V5.1H52Z",
).toPath().apply { fillType = PathFillType.EvenOdd }

private val FilmOutline = PathParser().parsePathString(
    // F, with the diagonal cut at the tip of the upper arm.
    "M74 0H85L81.2 5H74.4V8.2H82.8V13.1H74.4V21H69V5Q69 0 74 0Z " +
        // I
        "M87 0H92.4V21H87Z " +
        // L
        "M94.5 0H99.9V15.9H107.5V21H94.5Z " +
        // M
        "M109.5 0H115L118.75 8.5L122.5 0H128V21H122.8V9.7L120 15.4H117.5L114.7 9.7V21H109.5Z",
).toPath()

@Composable
internal fun LostFilmWordmark() {
    val colors = LocalLostFilmColors.current
    Canvas(
        modifier = Modifier
            .size(width = 128.dp, height = 26.dp)
            .semantics { contentDescription = "LOSTFILM" },
    ) {
        val scale = minOf(size.width / 128f, size.height / 26f)
        scale(scale, scale, pivot = Offset.Zero) {
            drawPath(LostOutline, colors.textPrimary)
            drawPath(FilmOutline, colors.accent)
            drawRect(colors.accent, topLeft = Offset(69f, 24f), size = Size(59f, 2f))
        }
    }
}
