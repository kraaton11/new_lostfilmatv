package com.kraat.lostfilmnewtv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Border
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
private const val POSTER_FOCUSED_SCALE = 1.065f

@Composable
fun PosterCard(
    item: ReleaseSummary,
    onClick: () -> Unit,
    isFocused: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    val borderColor = if (isFocused) FocusBorder.copy(alpha = 0.88f) else HomePanelBorder.copy(alpha = 0.14f)
    val overlayColor = if (isFocused) FocusBackground.copy(alpha = 0.82f) else HomePanelSurface.copy(alpha = 0.62f)
    val watchedBadgeColor = if (isFocused) HomeAccentGoldGlow.copy(alpha = 0.86f) else HomePanelSurfaceStrong.copy(alpha = 0.86f)
    val posterRequest = rememberPosterImageRequest(item.posterUrl)

    Card(
        onClick = onClick,
        modifier = modifier
            .size(width = POSTER_CARD_WIDTH, height = POSTER_CARD_HEIGHT),
        shape = CardDefaults.shape(shape),
        scale = CardDefaults.scale(focusedScale = POSTER_FOCUSED_SCALE),
        glow = CardDefaults.glow(
            focusedGlow = Glow(
                elevationColor = HomeAccentGoldGlow.copy(alpha = 0.42f),
                elevation = 20.dp
            )
        ),
        border = CardDefaults.border(
            border = Border(
                border = BorderStroke(0.5.dp, HomePanelBorder.copy(alpha = 0.14f)),
                shape = shape
            ),
            focusedBorder = Border(
                border = BorderStroke(1.2.dp, FocusBorder.copy(alpha = 0.88f)),
                shape = shape
            )
        ),
        colors = CardDefaults.colors(
            containerColor = HomePanelSurfaceStrong,
            focusedContainerColor = HomePanelSurfaceStrong
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
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
                        .padding(8.dp)
                        .background(HomeAccentGold.copy(alpha = 0.94f), RoundedCornerShape(999.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        color = Color(0xFF1B1408),
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    )
                }
            }

            if (item.isWatched) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(watchedBadgeColor, RoundedCornerShape(999.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "✓",
                        color = if (isFocused) Color(0xFF1B1408) else HomeAccentGoldGlow,
                        fontSize = 11.sp,
                        lineHeight = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                }
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
