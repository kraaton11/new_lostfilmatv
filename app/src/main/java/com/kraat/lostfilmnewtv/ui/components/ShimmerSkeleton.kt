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
import com.kraat.lostfilmnewtv.ui.theme.DetailsBorderDefault
import com.kraat.lostfilmnewtv.ui.theme.DetailsSurfaceSoft

@Composable
fun rememberShimmerSkeletonBrush(
    label: String,
    baseColor: Color = DetailsSurfaceSoft,
    highlightColor: Color = DetailsBorderDefault,
    baseAlpha: Float = 0.58f,
    highlightAlpha: Float = 0.62f,
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
        colors = listOf(
            baseColor.copy(alpha = baseAlpha),
            highlightColor.copy(alpha = highlightAlpha),
            baseColor.copy(alpha = baseAlpha),
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
    borderColor: Color = Color.Transparent,
) {
    Box(
        modifier = modifier
            .clip(shape)
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
