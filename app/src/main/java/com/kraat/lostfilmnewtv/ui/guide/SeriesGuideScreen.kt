package com.kraat.lostfilmnewtv.ui.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kraat.lostfilmnewtv.data.model.SeriesGuideEpisode
import com.kraat.lostfilmnewtv.data.model.SeriesGuideSeason
import com.kraat.lostfilmnewtv.ui.theme.BackgroundPrimary
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
            .background(BackgroundPrimary)
            .padding(horizontal = 32.dp, vertical = 24.dp),
    ) {
        when {
            state.isLoading && state.seasons.isEmpty() -> {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .testTag("series-guide-loading"),
                )
            }

            state.errorMessage != null -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(text = state.errorMessage, color = TextPrimary)
                    Button(onClick = onRetry) {
                        Text("Повторить")
                    }
                }
            }

            state.seasons.isEmpty() -> {
                Text(
                    text = "Список серий пока недоступен",
                    modifier = Modifier.align(Alignment.Center),
                    color = TextPrimary,
                )
            }

            else -> {
                GuideContent(
                    title = state.title,
                    seasons = state.seasons,
                    selectedEpisodeDetailsUrl = state.selectedEpisodeDetailsUrl,
                    onEpisodeClick = onEpisodeClick,
                )
            }
        }
    }
}

@Composable
private fun GuideContent(
    title: String,
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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item(key = "series-title") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 34.sp,
                )
                Text(
                    text = "Гид по сериям",
                    color = TextPrimary.copy(alpha = 0.72f),
                    fontSize = 16.sp,
                )
            }
        }

        items(
            items = seasons,
            key = { season -> "season-${season.seasonNumber}" },
        ) { season ->
            SeasonSection(
                season = season,
                selectedEpisodeDetailsUrl = selectedEpisodeDetailsUrl,
                focusRequesterForEpisode = { detailsUrl -> episodeFocusRequesters[detailsUrl] },
                onEpisodeClick = onEpisodeClick,
            )
        }
    }
}

@Composable
private fun SeasonSection(
    season: SeriesGuideSeason,
    selectedEpisodeDetailsUrl: String?,
    focusRequesterForEpisode: (String) -> FocusRequester?,
    onEpisodeClick: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Сезон ${season.seasonNumber}",
            color = TextPrimary,
            fontSize = 22.sp,
        )
        season.episodes.forEach { episode ->
            EpisodeRow(
                episode = episode,
                isSelected = episode.detailsUrl == selectedEpisodeDetailsUrl,
                focusRequester = focusRequesterForEpisode(episode.detailsUrl),
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
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (focusRequester != null) {
                    Modifier.focusRequester(focusRequester)
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
                color = TextPrimary,
            )
            Text(
                text = episode.episodeTitleRu.orEmpty(),
                color = TextPrimary,
            )
            Text(
                text = buildString {
                    append(episode.releaseDateRu)
                    if (episode.isWatched) {
                        append(" • Просмотрено")
                    }
                },
                color = TextPrimary.copy(alpha = 0.72f),
            )
        }
    }
}
