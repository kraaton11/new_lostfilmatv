package com.kraat.lostfilmnewtv.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
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
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import com.kraat.lostfilmnewtv.data.model.ReleaseSummary
import com.kraat.lostfilmnewtv.ui.components.PosterImage
import com.kraat.lostfilmnewtv.ui.components.resolveArtworkForDisplay
import com.kraat.lostfilmnewtv.ui.theme.HomeAccentGold
import com.kraat.lostfilmnewtv.ui.theme.HomePanelSurfaceStrong

@Composable
internal fun HomeCarouselCard(
    item: ReleaseSummary,
    selected: Boolean,
    isFocused: Boolean,
    isSeries: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val emphasis by animateFloatAsState(if (selected) 1f else 0f, label = "Carousel card focus")
    val poster = resolveArtworkForDisplay(item.posterUrl)
    val backdrop = resolveArtworkForDisplay(item.backdropUrl)
    val artwork = if (isSeries) poster else backdrop ?: poster
    val shape = RoundedCornerShape(14.dp)

    Card(
        onClick = onClick,
        modifier = modifier
            .zIndex(if (selected) 1f else 0f)
            .graphicsLayer {
                scaleX = 0.82f + 0.18f * emphasis
                scaleY = scaleX
            },
        shape = CardDefaults.shape(shape),
        border = CardDefaults.border(focusedBorder = Border(BorderStroke(2.dp, HomeAccentGold), shape = shape)),
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
                        0.40f to Color.Transparent,
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
                    modifier = if (isFocused) Modifier.loopingTitle() else Modifier,
                    maxLines = 1,
                    softWrap = false,
                    overflow = if (isFocused) TextOverflow.Clip else TextOverflow.Ellipsis,
                )
                item.originalReleaseYear?.let { year ->
                    Text(text = year.toString(), color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                }
            }
            if (emphasis < 1f) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = (1f - emphasis) * 0.22f)))
            }
        }
    }
}
