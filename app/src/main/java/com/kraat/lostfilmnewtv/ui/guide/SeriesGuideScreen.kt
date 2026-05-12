package com.kraat.lostfilmnewtv.ui.guide

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kraat.lostfilmnewtv.data.model.SeriesGuideEpisode
import com.kraat.lostfilmnewtv.data.model.SeriesGuideSeason
import com.kraat.lostfilmnewtv.ui.components.ShimmerSkeletonBox
import com.kraat.lostfilmnewtv.ui.components.rememberShimmerSkeletonBrush
import com.kraat.lostfilmnewtv.ui.theme.BackgroundPrimary
import com.kraat.lostfilmnewtv.ui.theme.DetailsAccentBlue
import com.kraat.lostfilmnewtv.ui.theme.DetailsAccentGold
import com.kraat.lostfilmnewtv.ui.theme.DetailsAccentGoldFocus
import com.kraat.lostfilmnewtv.ui.theme.DetailsBackgroundMid
import com.kraat.lostfilmnewtv.ui.theme.DetailsBackgroundTop
import com.kraat.lostfilmnewtv.ui.theme.DetailsBorderDefault
import com.kraat.lostfilmnewtv.ui.theme.DetailsSurfaceFocused
import com.kraat.lostfilmnewtv.ui.theme.DetailsSurfaceReadable
import com.kraat.lostfilmnewtv.ui.theme.DetailsSurfaceSoft
import com.kraat.lostfilmnewtv.ui.theme.DetailsTextMuted
import com.kraat.lostfilmnewtv.ui.theme.DetailsTextSecondary
import com.kraat.lostfilmnewtv.ui.theme.HomePanelBorder
import com.kraat.lostfilmnewtv.ui.theme.HomePanelSurface
import com.kraat.lostfilmnewtv.ui.theme.TextPrimary
import kotlin.math.roundToInt

@Composable
fun SeriesGuideScreen(
    state: SeriesGuideUiState,
    onRetry: () -> Unit,
    onSeasonSelected: (Int) -> Unit,
    onEpisodeClick: (String) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(guideBackgroundBrush()),
    ) {
        when {
            state.isLoading && state.seasons.isEmpty() -> {
                GuideLoadingState()
            }

            state.errorMessage != null -> {
                GuideErrorState(
                    message = state.errorMessage,
                    onRetry = onRetry,
                )
            }

            state.seasons.isEmpty() -> {
                GuideEmptyState(onRetry = onRetry)
            }

            else -> {
                GuideContent(
                    title = state.title,
                    posterUrl = state.posterUrl,
                    seasons = state.seasons,
                    selectedSeasonIndex = state.selectedSeasonIndex,
                    selectedEpisodeDetailsUrl = state.selectedEpisodeDetailsUrl,
                    onSeasonSelected = onSeasonSelected,
                    onEpisodeClick = onEpisodeClick,
                )
            }
        }
    }
}

@Composable
private fun GuideLoadingState() {
    val shimmerBrush = rememberShimmerSkeletonBrush(
        label = "guideSkeleton",
        baseColor = DetailsSurfaceSoft,
        highlightColor = DetailsBorderDefault,
        baseAlpha = 0.56f,
        highlightAlpha = 0.66f,
        startOffset = -560f,
        endOffset = 1_300f,
        shimmerWidth = 520f,
        verticalOffset = 240f,
        durationMillis = 1_400,
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 48.dp, top = 24.dp, end = 48.dp, bottom = 20.dp)
            .testTag("series-guide-loading"),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShimmerSkeletonBox(
                brush = shimmerBrush,
                modifier = Modifier.size(width = 120.dp, height = 172.dp),
                shape = RoundedCornerShape(20.dp),
                borderColor = DetailsBorderDefault.copy(alpha = 0.34f),
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ShimmerSkeletonBox(
                    brush = shimmerBrush,
                    modifier = Modifier.size(width = 420.dp, height = 42.dp),
                    shape = RoundedCornerShape(12.dp),
                )
                ShimmerSkeletonBox(
                    brush = shimmerBrush,
                    modifier = Modifier.size(width = 220.dp, height = 20.dp),
                    shape = RoundedCornerShape(10.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(3) { index ->
                        ShimmerSkeletonBox(
                            brush = shimmerBrush,
                            modifier = Modifier.size(width = listOf(74.dp, 82.dp, 68.dp)[index], height = 28.dp),
                            shape = RoundedCornerShape(999.dp),
                            borderColor = DetailsBorderDefault.copy(alpha = 0.28f),
                        )
                    }
                }
            }
        }

        repeat(2) {
            ShimmerSkeletonBox(
                brush = shimmerBrush,
                modifier = Modifier.size(width = 160.dp, height = 24.dp),
                shape = RoundedCornerShape(10.dp),
            )
            repeat(4) {
                ShimmerSkeletonBox(
                    brush = shimmerBrush,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                    shape = RoundedCornerShape(18.dp),
                    borderColor = DetailsBorderDefault.copy(alpha = 0.24f),
                )
            }
        }
    }
}

@Composable
private fun GuideErrorState(
    message: String,
    onRetry: () -> Unit,
) {
    val retryRequester = remember { FocusRequester() }

    LaunchedEffect(message) {
        withFrameNanos { }
        runCatching { retryRequester.requestFocus() }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        GuideCenteredStatePanel {
            Text(
                text = message,
                color = DetailsTextSecondary,
                fontSize = 18.sp,
            )
            GuideActionButton(
                label = "Повторить",
                subtitle = "Повторить загрузку",
                onClick = onRetry,
                modifier = Modifier.focusRequester(retryRequester),
                isPrimary = true,
            )
        }
    }
}

@Composable
private fun GuideEmptyState(onRetry: () -> Unit) {
    val retryRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { retryRequester.requestFocus() }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        GuideCenteredStatePanel {
            Text(
                text = "Список серий пока недоступен",
                color = TextPrimary,
                fontSize = 18.sp,
            )
            GuideActionButton(
                label = "Повторить",
                subtitle = "Повторить загрузку",
                onClick = onRetry,
                modifier = Modifier.focusRequester(retryRequester),
                isPrimary = true,
            )
        }
    }
}

@Composable
private fun GuideCenteredStatePanel(
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)

    Column(
        modifier = Modifier
            .background(DetailsSurfaceReadable, shape)
            .border(1.dp, DetailsBorderDefault, shape)
            .padding(horizontal = 28.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        content()
    }
}

@Composable
private fun GuideContent(
    title: String,
    posterUrl: String?,
    seasons: List<SeriesGuideSeason>,
    selectedSeasonIndex: Int,
    selectedEpisodeDetailsUrl: String?,
    onSeasonSelected: (Int) -> Unit,
    onEpisodeClick: (String) -> Unit,
) {
    val activeSeasonIndex = selectedSeasonIndex.coerceIn(seasons.indices)
    val activeSeason = seasons[activeSeasonIndex]
    val activeEpisodeUrls = remember(activeSeason) {
        activeSeason.episodes.map { episode -> episode.detailsUrl }.toSet()
    }
    val episodes = remember(seasons) {
        seasons.flatMap { season -> season.episodes }
    }
    val episodeFocusRequesters = remember(episodes.map { it.detailsUrl }) {
        episodes.associate { episode -> episode.detailsUrl to FocusRequester() }
    }

    LaunchedEffect(selectedEpisodeDetailsUrl, activeEpisodeUrls, episodeFocusRequesters.keys) {
        val selectedUrl = selectedEpisodeDetailsUrl ?: return@LaunchedEffect
        if (selectedUrl !in activeEpisodeUrls) return@LaunchedEffect
        val selectedRequester = episodeFocusRequesters[selectedUrl] ?: return@LaunchedEffect
        withFrameNanos { }
        selectedRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 48.dp, top = 24.dp, end = 48.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        GuideHeroSection(title = title, posterUrl = posterUrl, seasons = seasons)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            LazyColumn(
                modifier = Modifier
                    .width(130.dp)
                    .fillMaxHeight()
                    .background(DetailsBackgroundMid)
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(
                    items = seasons,
                    key = { _, season -> "season-sidebar-${season.seasonNumber}" },
                ) { index, season ->
                    SeasonSidebarItem(
                        season = season,
                        isSelected = index == activeSeasonIndex,
                        onClick = { onSeasonSelected(index) },
                    )
                }
            }

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(DetailsBorderDefault),
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "season-progress-${activeSeason.seasonNumber}") {
                    SeasonProgressHeader(season = activeSeason)
                }

                itemsIndexed(
                    items = activeSeason.episodes,
                    key = { _, episode -> episode.detailsUrl },
                ) { index, episode ->
                    val focusRequester = episodeFocusRequesters[episode.detailsUrl]
                    val prevEpisode = if (index > 0) activeSeason.episodes[index - 1] else null
                    val nextEpisode = if (index < activeSeason.episodes.lastIndex) activeSeason.episodes[index + 1] else null

                    EpisodeRow(
                        episode = episode,
                        isSelected = episode.detailsUrl == selectedEpisodeDetailsUrl,
                        focusRequester = focusRequester,
                        prevEpisodeUrl = prevEpisode?.detailsUrl,
                        nextEpisodeUrl = nextEpisode?.detailsUrl,
                        focusRequesterForUrl = { url -> episodeFocusRequesters[url] },
                        onClick = { onEpisodeClick(episode.detailsUrl) },
                    )
                }
            }
        }
    }
}

@Composable
private fun GuideHeroSection(
    title: String,
    posterUrl: String?,
    seasons: List<SeriesGuideSeason>,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val posterWidthPx = with(density) { 104.dp.toPx().roundToInt().coerceAtLeast(1) }
    val posterHeightPx = with(density) { 150.dp.toPx().roundToInt().coerceAtLeast(1) }
    val episodesCount = remember(seasons) { seasons.sumOf { it.episodes.size } }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 104.dp, height = 150.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF3F586E), Color(0xFF172734), Color(0xFF0A131B)),
                    ),
                    RoundedCornerShape(18.dp),
                )
                .border(1.dp, DetailsBorderDefault.copy(alpha = 0.72f), RoundedCornerShape(18.dp)),
        ) {
            if (!posterUrl.isNullOrBlank()) {
                val request = remember(context, posterUrl, posterWidthPx, posterHeightPx) {
                    ImageRequest.Builder(context)
                        .data(posterUrl)
                        .size(posterWidthPx, posterHeightPx)
                        .build()
                }
                AsyncImage(
                    model = request,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    alpha = 0.94f,
                )
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 38.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GuideInfoChip("Гид по сериям")
                GuideInfoChip("${seasons.size} сезонов")
                GuideInfoChip("$episodesCount серий")
            }
        }
    }
}

@Composable
private fun GuideInfoChip(text: String) {
    Box(
        modifier = Modifier
            .background(DetailsSurfaceSoft.copy(alpha = 0.72f), RoundedCornerShape(999.dp))
            .border(1.dp, DetailsBorderDefault.copy(alpha = 0.54f), RoundedCornerShape(999.dp))
            .padding(horizontal = 13.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = DetailsTextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SeasonProgressHeader(
    season: SeriesGuideSeason,
) {
    val watchedCount = remember(season) { season.episodes.count { it.isWatched } }
    val totalCount = season.episodes.size
    val progress = if (totalCount == 0) 0f else watchedCount / totalCount.toFloat()
    val percentage = (progress * 100).roundToInt()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DetailsSurfaceReadable.copy(alpha = 0.74f), RoundedCornerShape(16.dp))
            .border(1.dp, DetailsBorderDefault.copy(alpha = 0.48f), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Сезон ${season.seasonNumber} · просмотрено $watchedCount из $totalCount",
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "$percentage%",
                color = DetailsAccentGold,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        GuideProgressBar(
            progress = progress,
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp),
        )
    }
}

@Composable
private fun SeasonSidebarItem(
    season: SeriesGuideSeason,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val watchedCount = remember(season) { season.episodes.count { it.isWatched } }
    val totalCount = season.episodes.size
    val progress = if (totalCount == 0) 0f else watchedCount / totalCount.toFloat()
    val shape = RoundedCornerShape(12.dp)
    val textColor = if (isSelected) TextPrimary else DetailsTextSecondary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) DetailsSurfaceSoft else Color.Transparent, shape)
            .drawBehind {
                if (isSelected) {
                    drawRect(
                        color = DetailsAccentGold,
                        size = size.copy(width = 2.dp.toPx()),
                    )
                }
            },
    ) {
        Button(
            onClick = onClick,
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = textColor,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = "Сезон ${season.seasonNumber}",
                    color = if (isSelected) DetailsAccentGold else textColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "$watchedCount / $totalCount серий",
                    color = textColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                GuideProgressBar(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                )
            }
        }
    }
}

@Composable
private fun GuideProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val clampedProgress = progress.coerceIn(0f, 1f)
    val shape = RoundedCornerShape(999.dp)

    Box(
        modifier = modifier
            .background(DetailsBorderDefault.copy(alpha = 0.52f), shape),
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize(),
        ) {
            drawRoundRect(
                color = DetailsAccentGold,
                size = size.copy(width = size.width * clampedProgress),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f, size.height / 2f),
            )
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: SeriesGuideEpisode,
    isSelected: Boolean,
    focusRequester: FocusRequester?,
    prevEpisodeUrl: String?,
    nextEpisodeUrl: String?,
    focusRequesterForUrl: (String) -> FocusRequester?,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }

    val shape = RoundedCornerShape(18.dp)

    val background = when {
        isFocused -> DetailsSurfaceFocused
        isSelected -> DetailsSurfaceSoft
        else -> HomePanelSurface
    }
    val borderColor = when {
        isSelected && isFocused -> DetailsAccentGoldFocus
        isSelected -> DetailsAccentGold
        isFocused -> DetailsAccentBlue
        else -> HomePanelBorder
    }

    Button(
        onClick = onClick,
        shape = shape,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp, vertical = 10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = background),
        modifier = Modifier
            .fillMaxWidth()
            .border(if (isFocused || isSelected) 1.5.dp else 1.dp, borderColor, shape)
            .onFocusChanged { isFocused = it.isFocused }
            .then(
                if (focusRequester != null) {
                    Modifier
                        .focusRequester(focusRequester)
                        .focusProperties {
                            if (prevEpisodeUrl != null) {
                                up = focusRequesterForUrl(prevEpisodeUrl) ?: focusRequester
                            }
                            if (nextEpisodeUrl != null) {
                                down = focusRequesterForUrl(nextEpisodeUrl) ?: focusRequester
                            }
                        }
                } else {
                    Modifier
                },
            )
            .semantics { selected = isSelected }
            .testTag("series-guide-row-${episode.detailsUrl}"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 82.dp, height = 36.dp)
                    .background(
                        if (isSelected) DetailsAccentGold.copy(alpha = 0.16f) else DetailsSurfaceSoft.copy(alpha = 0.64f),
                        RoundedCornerShape(12.dp),
                    )
                    .border(
                        1.dp,
                        if (isSelected) DetailsAccentGold.copy(alpha = 0.72f) else DetailsBorderDefault.copy(alpha = 0.52f),
                        RoundedCornerShape(12.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "S${episode.seasonNumber.toString().padStart(2, '0')}E${episode.episodeNumber.toString().padStart(2, '0')}",
                    color = if (isSelected) DetailsAccentGold else DetailsTextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = episode.episodeTitleRu?.takeIf { it.isNotBlank() }
                        ?: "Сезон ${episode.seasonNumber} • Серия ${episode.episodeNumber}",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = episode.releaseDateRu,
                    color = DetailsTextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (episode.isWatched) {
                Box(
                    modifier = Modifier
                        .background(DetailsAccentGold.copy(alpha = 0.9f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "✓",
                        color = Color(0xFF17120D),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun GuideActionButton(
    label: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.03f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "buttonScale",
    )

    val shape = RoundedCornerShape(22.dp)
    val background = when {
        isPrimary -> DetailsAccentGold
        isFocused -> DetailsSurfaceFocused
        else -> DetailsSurfaceSoft
    }
    val borderColor = when {
        isPrimary && isFocused -> DetailsAccentGoldFocus
        isPrimary -> DetailsAccentGold
        isFocused -> DetailsAccentBlue
        else -> DetailsBorderDefault
    }
    val textColor = if (isPrimary) Color(0xFF17120D) else TextPrimary
    val subtitleColor = if (isPrimary) Color(0xFF473317) else DetailsTextMuted

    Button(
        onClick = onClick,
        shape = shape,
        colors = ButtonDefaults.buttonColors(containerColor = background),
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .border(1.5.dp, borderColor, shape)
            .onFocusChanged { isFocused = it.isFocused },
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                color = textColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                color = subtitleColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.6.sp,
            )
        }
    }
}

private fun guideBackgroundBrush(): Brush {
    return Brush.verticalGradient(
        0f to DetailsBackgroundTop,
        0.35f to DetailsBackgroundMid,
        1f to BackgroundPrimary,
    )
}
