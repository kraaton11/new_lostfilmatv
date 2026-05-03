package com.kraat.lostfilmnewtv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import com.kraat.lostfilmnewtv.data.model.ReleaseSummary
import com.kraat.lostfilmnewtv.ui.theme.FocusBorder
import com.kraat.lostfilmnewtv.ui.theme.FocusBackground
import com.kraat.lostfilmnewtv.ui.theme.HomeAccentGold
import com.kraat.lostfilmnewtv.ui.theme.HomeAccentGoldGlow
import com.kraat.lostfilmnewtv.ui.theme.HomePanelBorder
import com.kraat.lostfilmnewtv.ui.theme.HomePanelSurface
import com.kraat.lostfilmnewtv.ui.theme.HomePanelSurfaceStrong
import kotlin.math.roundToInt

private val POSTER_CARD_WIDTH = 176.dp
private val POSTER_CARD_HEIGHT = 264.dp
private const val POSTER_FOCUSED_SCALE = 1.10f

@Composable
fun PosterCard(
    item: ReleaseSummary,
    isFocused: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(22.dp)
    val scale by animateFloatAsState(
        targetValue = if (isFocused) POSTER_FOCUSED_SCALE else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "posterScale",
    )
    val borderColor = if (isFocused) FocusBorder else HomePanelBorder
    val overlayColor = if (isFocused) FocusBackground else HomePanelSurface
    val watchedBadgeColor = if (isFocused) HomeAccentGoldGlow else HomeAccentGold
    val posterRequest = rememberPosterImageRequest(item.posterUrl)

    Box(
        modifier = modifier
            .size(width = POSTER_CARD_WIDTH, height = POSTER_CARD_HEIGHT)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (isFocused) 32.dp else 8.dp,
                shape = shape,
            )
            .clip(shape)
            .background(HomePanelSurfaceStrong)
            .border(width = 3.5.dp, color = borderColor, shape = shape),
    ) {
        AsyncImage(
            model = posterRequest,
            contentDescription = item.titleRu,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.52f to Color.Transparent,
                        1f to overlayColor,
                    ),
                ),
        )

        seasonEpisodeLabel(item)?.let { label ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(overlayColor)
                    .testTag("poster-meta:${item.detailsUrl}")
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    text = label,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        if (item.isWatched) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp),
                shape = RoundedCornerShape(10.dp),
                color = watchedBadgeColor,
            ) {
                Text(
                    text = "Просмотрено",
                    color = Color(0xFF1B1408),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun rememberPosterImageRequest(posterUrl: String): ImageRequest {
    val context = LocalContext.current
    val density = LocalDensity.current
    val targetWidthPx = with(density) { (POSTER_CARD_WIDTH.toPx() * POSTER_FOCUSED_SCALE).roundToInt().coerceAtLeast(1) }
    val targetHeightPx = with(density) { (POSTER_CARD_HEIGHT.toPx() * POSTER_FOCUSED_SCALE).roundToInt().coerceAtLeast(1) }

    return remember(context, posterUrl, targetWidthPx, targetHeightPx) {
        ImageRequest.Builder(context)
            .data(posterUrl)
            // Decode near the rendered card size instead of the full remote poster.
            .size(targetWidthPx, targetHeightPx)
            .build()
    }
}

private fun seasonEpisodeLabel(item: ReleaseSummary): String? {
    if (item.kind != ReleaseKind.SERIES) return null
    val seasonNumber = item.seasonNumber ?: return null
    val episodeNumber = item.episodeNumber ?: return null
    return "Сезон $seasonNumber, серия $episodeNumber"
}
