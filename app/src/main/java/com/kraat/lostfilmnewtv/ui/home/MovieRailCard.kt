package com.kraat.lostfilmnewtv.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import com.kraat.lostfilmnewtv.data.model.ReleaseSummary
import com.kraat.lostfilmnewtv.ui.components.PosterImage
import com.kraat.lostfilmnewtv.ui.components.resolveArtworkForDisplay
import com.kraat.lostfilmnewtv.ui.theme.HomePanelSurfaceStrong

@Composable
internal fun MovieRailCard(
    item: ReleaseSummary,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val emphasis by animateFloatAsState(if (selected) 1f else 0f, label = "Movie card focus")
    val artwork = resolveArtworkForDisplay(item.backdropUrl)
        ?: resolveArtworkForDisplay(item.posterUrl)

    Card(
        onClick = onClick,
        modifier = modifier
            .zIndex(if (selected) 1f else 0f)
            .graphicsLayer {
                scaleX = 0.82f + 0.18f * emphasis
                scaleY = scaleX
            },
        shape = CardDefaults.shape(RoundedCornerShape(14.dp)),
        scale = CardDefaults.scale(focusedScale = 1f),
        colors = CardDefaults.colors(
            containerColor = HomePanelSurfaceStrong,
            focusedContainerColor = HomePanelSurfaceStrong,
        ),
    ) {
        Box(Modifier.fillMaxSize()) {
            PosterImage(model = artwork, contentDescription = null, modifier = Modifier.fillMaxSize())
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0.25f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.90f),
                    ),
                ),
            )
            Column(
                modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 12.dp, vertical = 9.dp),
            ) {
                Text(
                    text = item.titleRu,
                    color = Color.White,
                    fontSize = 16.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                item.originalReleaseYear?.let { year ->
                    Text(text = year.toString(), color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                }
            }
            if (emphasis < 1f) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = (1f - emphasis) * 0.38f)))
            }
        }
    }
}
