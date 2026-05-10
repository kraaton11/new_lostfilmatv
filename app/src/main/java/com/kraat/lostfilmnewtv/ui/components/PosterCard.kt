package com.kraat.lostfilmnewtv.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
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

private val POSTER_CARD_WIDTH = 112.dp
private val POSTER_CARD_HEIGHT = 172.dp
private const val POSTER_FOCUSED_SCALE = 1.045f

@Composable
fun PosterCard(
    item: ReleaseSummary,
    isFocused: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    val density = LocalDensity.current
    val scale by animateFloatAsState(
        targetValue = if (isFocused) POSTER_FOCUSED_SCALE else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "buttonScale",
    )
    val liftPx = remember(isFocused, density) {
        if (isFocused) with(density) { (-6).dp.toPx() } else 0f
    }
    val borderColor = if (isFocused) FocusBorder.copy(alpha = 0.96f) else HomePanelBorder.copy(alpha = 0.30f)
    val overlayColor = if (isFocused) FocusBackground.copy(alpha = 0.88f) else HomePanelSurface.copy(alpha = 0.76f)
    val watchedBadgeColor = if (isFocused) HomeAccentGoldGlow.copy(alpha = 0.94f) else HomeAccentGold.copy(alpha = 0.9f)
    val posterRequest = rememberPosterImageRequest(item.posterUrl)

    Box(
        modifier = modifier
            .size(width = POSTER_CARD_WIDTH, height = POSTER_CARD_HEIGHT)
            .zIndex(if (isFocused) 1f else 0f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = liftPx
            }
            .shadow(
                elevation = if (isFocused) 34.dp else 2.dp,
                spotColor = if (isFocused) HomeAccentGoldGlow.copy(alpha = 0.52f) else Color.Black,
                ambientColor = if (isFocused) HomeAccentGold.copy(alpha = 0.28f) else Color.Black,
                shape = shape,
            )
            .clip(shape)
            .background(HomePanelSurfaceStrong)
            .border(width = if (isFocused) 1.4.dp else 0.5.dp, color = borderColor, shape = shape),
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
                        0.42f to Color.Transparent,
                        1f to overlayColor,
                    ),
                ),
        )

        if (isFocused) seasonEpisodeLabel(item)?.let { label ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .testTag("poster-meta:${item.detailsUrl}")
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            1f to overlayColor,
                        ),
                    )
                    .padding(horizontal = 13.dp, vertical = 12.dp),
            ) {
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = 13.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        item.availabilityLabel?.takeIf { it.isNotBlank() }?.let { label ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(7.dp)
                    .background(HomeAccentGold.copy(alpha = 0.94f), RoundedCornerShape(999.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = Color(0xFF1B1408),
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }

        if (item.isWatched) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(7.dp)
                    .background(watchedBadgeColor, RoundedCornerShape(999.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "✓",
                    color = Color(0xFF1B1408),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
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
