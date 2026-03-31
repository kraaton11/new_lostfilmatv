package com.kraat.lostfilmnewtv.ui.guide

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kraat.lostfilmnewtv.data.model.SeriesGuideEpisode
import com.kraat.lostfilmnewtv.data.model.SeriesGuideSeason
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

@Composable
fun SeriesGuideScreen(
    state: SeriesGuideUiState,
    onRetry: () -> Unit,
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
                GuideEmptyState()
            }

            else -> {
                GuideContent(
                    title = state.title,
                    posterUrl = state.posterUrl,
                    seasons = state.seasons,
                    selectedEpisodeDetailsUrl = state.selectedEpisodeDetailsUrl,
                    onEpisodeClick = onEpisodeClick,
                )
            }
        }
    }
}

@Composable
private fun GuideLoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        GuideCenteredStatePanel {
            CircularProgressIndicator(
                modifier = Modifier.testTag("series-guide-loading"),
                color = DetailsAccentGold,
            )
        }
    }
}

@Composable
private fun GuideErrorState(
    message: String,
    onRetry: () -> Unit,
) {
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
                isPrimary = true,
            )
        }
    }
}

@Composable
private fun GuideEmptyState() {
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
    selectedEpisodeDetailsUrl: String?,
    onEpisodeClick: (String) -> Unit,
) {
    val episodes = remember(seasons) {
        seasons.flatMap { season -> season.episodes }
    }
    val episodeFocusRequesters = remember(episodes.map { it.detailsUrl }) {
        episodes.associate { episode -> episode.detailsUrl to FocusRequester() }
    }

    LaunchedEffect(selectedEpisodeDetailsUrl, episodeFocusRequesters.keys) {
        val selectedRequester = selectedEpisodeDetailsUrl
            ?.let { episodeFocusRequesters[it] }
            ?: return@LaunchedEffect
        withFrameNanos { }
        selectedRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 48.dp, top = 24.dp, end = 48.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        GuideHeroSection(title = title, posterUrl = posterUrl)

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(
                items = seasons,
                key = { season -> "season-${season.seasonNumber}" },
            ) { season ->
                SeasonSection(
                    season = season,
                    selectedEpisodeDetailsUrl = selectedEpisodeDetailsUrl,
                    episodeFocusRequesters = episodeFocusRequesters,
                    onEpisodeClick = onEpisodeClick,
                )
            }
        }
    }
}

@Composable
private fun GuideHeroSection(
    title: String,
    posterUrl: String?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 120.dp, height = 172.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF3F586E), Color(0xFF172734), Color(0xFF0A131B)),
                    ),
                    RoundedCornerShape(20.dp),
                )
                .border(1.dp, DetailsBorderDefault, RoundedCornerShape(20.dp)),
        ) {
            if (!posterUrl.isNullOrBlank()) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    alpha = 0.6f,
                )
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 38.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 42.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Гид по сериям",
                color = DetailsTextSecondary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun SeasonSection(
    season: SeriesGuideSeason,
    selectedEpisodeDetailsUrl: String?,
    episodeFocusRequesters: Map<String, FocusRequester>,
    onEpisodeClick: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Сезон ${season.seasonNumber}",
            color = TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 2.dp),
        )

        season.episodes.forEachIndexed { index, episode ->
            val focusRequester = episodeFocusRequesters[episode.detailsUrl]
            val prevEpisode = if (index > 0) season.episodes[index - 1] else null
            val nextEpisode = if (index < season.episodes.lastIndex) season.episodes[index + 1] else null

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
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.03f else 1f,
        animationSpec = tween(durationMillis = 110),
        label = "episodeRowScale",
    )

    val shape = RoundedCornerShape(22.dp)

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
        colors = ButtonDefaults.buttonColors(containerColor = background),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .border(1.5.dp, borderColor, shape)
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
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Сезон ${episode.seasonNumber} • Серия ${episode.episodeNumber}",
                color = if (isSelected) DetailsAccentGold else TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (!episode.episodeTitleRu.isNullOrBlank()) {
                Text(
                    text = episode.episodeTitleRu,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = buildString {
                    append(episode.releaseDateRu)
                    if (episode.isWatched) {
                        append(" • Просмотрено")
                    }
                },
                color = DetailsTextMuted,
                fontSize = 12.sp,
            )
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
        animationSpec = tween(durationMillis = 110),
        label = "guideActionScale",
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
