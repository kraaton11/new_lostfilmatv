package com.kraat.lostfilmnewtv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kraat.lostfilmnewtv.data.model.ReleaseSummary

@Composable
fun PosterCard(
    item: ReleaseSummary,
    isFocused: Boolean,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1f,
        label = "posterScale",
    )
    val borderColor = if (isFocused) Color(0xFFE7F0FF) else Color(0x33E7F0FF)

    Box(
        modifier = modifier
            .size(width = 176.dp, height = 264.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF162130))
            .border(width = 2.dp, color = borderColor, shape = RoundedCornerShape(20.dp)),
    ) {
        AsyncImage(
            model = item.posterUrl,
            contentDescription = item.titleRu,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
