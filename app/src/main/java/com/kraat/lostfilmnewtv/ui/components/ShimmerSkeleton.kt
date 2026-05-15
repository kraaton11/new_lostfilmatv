package com.kraat.lostfilmnewtv.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kraat.lostfilmnewtv.ui.theme.BackgroundSurface
import com.kraat.lostfilmnewtv.ui.theme.DetailsBorderDefault
import com.kraat.lostfilmnewtv.ui.theme.DetailsSurfaceSoft

@Composable
fun rememberShimmerSkeletonBrush(
    label: String,
    baseColor: Color = DetailsSurfaceSoft,
    highlightColor: Color = Color.White,
    baseAlpha: Float = 0.72f,
    highlightAlpha: Float = 0.10f,
    startOffset: Float = -560f,
    endOffset: Float = 1_320f,
    shimmerWidth: Float = 520f,
    verticalOffset: Float = 260f,
    durationMillis: Int = 1_350,
): Brush {
    val transition = rememberInfiniteTransition(label = label)
    val xOffset by transition.animateFloat(
        initialValue = startOffset,
        targetValue = endOffset,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "$label-offset",
    )

    return Brush.linearGradient(
        colorStops = arrayOf(
            0f to baseColor.copy(alpha = baseAlpha),
            0.38f to baseColor.copy(alpha = baseAlpha),
            0.5f to highlightColor.copy(alpha = highlightAlpha),
            0.62f to baseColor.copy(alpha = baseAlpha),
            1f to baseColor.copy(alpha = baseAlpha),
        ),
        start = Offset(xOffset, 0f),
        end = Offset(xOffset + shimmerWidth, verticalOffset),
    )
}

@Composable
fun ShimmerSkeletonBox(
    brush: Brush,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(12.dp),
    baseColor: Color = BackgroundSurface.copy(alpha = 0.46f),
    borderColor: Color = Color.Transparent,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(baseColor)
            .background(brush)
            .then(
                if (borderColor == Color.Transparent) {
                    Modifier
                } else {
                    Modifier.border(1.dp, borderColor, shape)
                },
            ),
    )
}
