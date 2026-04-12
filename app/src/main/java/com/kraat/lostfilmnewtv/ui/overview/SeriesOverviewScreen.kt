package com.kraat.lostfilmnewtv.ui.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kraat.lostfilmnewtv.data.model.SeriesOverview
import com.kraat.lostfilmnewtv.ui.theme.BackgroundPrimary
import com.kraat.lostfilmnewtv.ui.theme.DetailsAccentGold
import com.kraat.lostfilmnewtv.ui.theme.DetailsAccentGoldFocus
import com.kraat.lostfilmnewtv.ui.theme.DetailsBackgroundMid
import com.kraat.lostfilmnewtv.ui.theme.DetailsBackgroundTop
import com.kraat.lostfilmnewtv.ui.theme.DetailsBorderDefault
import com.kraat.lostfilmnewtv.ui.theme.DetailsSurfaceFocused
import com.kraat.lostfilmnewtv.ui.theme.DetailsSurfaceReadable
import com.kraat.lostfilmnewtv.ui.theme.DetailsSurfaceSoft
import com.kraat.lostfilmnewtv.ui.theme.DetailsTextSecondary
import com.kraat.lostfilmnewtv.ui.theme.TextPrimary

private data class OverviewSection(
    val key: String,
    val title: String,
    val body: String,
    val tag: String,
)

@Composable
fun SeriesOverviewScreen(
    state: SeriesOverviewUiState,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(overviewBackgroundBrush()),
    ) {
        OverviewBackgroundPoster(state.overview)

        when {
            state.isLoading && state.overview == null -> OverviewLoadingState()
            state.errorMessage != null && state.overview == null -> OverviewErrorState(
                message = state.errorMessage,
                onRetry = onRetry,
            )
            state.overview == null -> OverviewEmptyState()
            else -> OverviewContent(overview = state.overview)
        }
    }
}

@Composable
private fun OverviewBackgroundPoster(overview: SeriesOverview?) {
    val imageUrl = overview?.backdropUrl?.takeIf { !it.isNullOrBlank() }
        ?: overview?.posterUrl
        ?: return

    AsyncImage(
        model = imageUrl,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
        alpha = 0.14f,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.3f to DetailsBackgroundMid.copy(alpha = 0.74f),
                    1f to BackgroundPrimary,
                ),
            ),
    )
}

@Composable
private fun OverviewLoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.testTag("series-overview-loading"),
            color = DetailsAccentGold,
        )
    }
}

@Composable
private fun OverviewErrorState(
    message: String,
    onRetry: () -> Unit,
) {
    OverviewCenteredStatePanel {
        Text(
            text = message,
            color = DetailsTextSecondary,
            fontSize = 18.sp,
        )
        OverviewActionButton(
            label = "Повторить",
            subtitle = "Повторить загрузку",
            onClick = onRetry,
        )
    }
}

@Composable
private fun OverviewEmptyState() {
    OverviewCenteredStatePanel {
        Text(
            text = "Обзор сериала пока недоступен",
            color = TextPrimary,
            fontSize = 18.sp,
        )
    }
}

@Composable
private fun OverviewCenteredStatePanel(
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .background(DetailsSurfaceReadable, RoundedCornerShape(28.dp))
                .border(1.dp, DetailsBorderDefault, RoundedCornerShape(28.dp))
                .padding(horizontal = 28.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}

@Composable
private fun OverviewContent(overview: SeriesOverview?) {
    val safeOverview = overview ?: return
    val infoRows = remember(safeOverview) {
        listOfNotNull(
            "Премьера" to safeOverview.premiereDateRu,
            "Канал, страна" to safeOverview.channelCountryRu,
            "IMDb" to safeOverview.imdbRating,
            "Жанр" to safeOverview.genresRu,
            "Тип" to safeOverview.typesRu,
            "Официальный сайт" to safeOverview.officialSiteUrl,
        ).filter { !it.second.isNullOrBlank() }
    }
    val sections = remember(safeOverview) {
        buildList {
            if (!safeOverview.descriptionRu.isNullOrBlank()) {
                add(
                    OverviewSection(
                        key = "description",
                        title = "Описание",
                        body = safeOverview.descriptionRu,
                        tag = "series-overview-description",
                    ),
                )
            }
            if (!safeOverview.plotRu.isNullOrBlank()) {
                add(
                    OverviewSection(
                        key = "plot",
                        title = "Сюжет",
                        body = safeOverview.plotRu,
                        tag = "series-overview-plot",
                    ),
                )
            }
        }
    }

    val heroRequester = remember { FocusRequester() }

    LaunchedEffect(safeOverview.seriesUrl) {
        withFrameNanos { }
        heroRequester.requestFocus()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 48.dp, top = 24.dp, end = 48.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "hero") {
            OverviewSurfaceCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(heroRequester)
                    .testTag("series-overview-hero"),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 220.dp, height = 316.dp)
                            .background(DetailsSurfaceSoft, RoundedCornerShape(24.dp))
                            .border(1.dp, DetailsBorderDefault, RoundedCornerShape(24.dp)),
                    ) {
                        if (!safeOverview.posterUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = safeOverview.posterUrl,
                                contentDescription = safeOverview.titleRu,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.width(980.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = safeOverview.titleRu,
                            color = TextPrimary,
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 48.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        safeOverview.titleEn?.takeIf { it.isNotBlank() }?.let { titleEn ->
                            Text(
                                text = titleEn,
                                color = DetailsTextSecondary,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 28.sp,
                            )
                        }
                        safeOverview.statusRu?.takeIf { it.isNotBlank() }?.let { status ->
                            Text(
                                text = "Статус: $status",
                                color = TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 24.sp,
                            )
                        }
                        Text(
                            text = "Обзор сериала",
                            color = DetailsTextSecondary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }

        if (infoRows.isNotEmpty()) {
            item(key = "meta") {
                OverviewSurfaceCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("series-overview-meta"),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(
                            text = "Информация",
                            color = TextPrimary,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        infoRows.forEach { (label, value) ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = label,
                                    color = DetailsTextSecondary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    text = value.orEmpty(),
                                    color = TextPrimary,
                                    fontSize = 18.sp,
                                    lineHeight = 24.sp,
                                )
                            }
                        }
                    }
                }
            }
        }

        items(
            items = sections,
            key = { section -> section.key },
        ) { section ->
            OverviewSurfaceCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(section.tag),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = section.title,
                        color = TextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = section.body,
                        color = TextPrimary,
                        fontSize = 18.sp,
                        lineHeight = 28.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun OverviewSurfaceCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(28.dp)

    Column(
        modifier = modifier
            .background(
                color = if (isFocused) DetailsSurfaceFocused else DetailsSurfaceReadable,
                shape = shape,
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) DetailsAccentGoldFocus else DetailsBorderDefault,
                shape = shape,
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .padding(horizontal = 28.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
private fun OverviewActionButton(
    label: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = DetailsAccentGold,
            contentColor = Color.Black,
        ),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = label, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, fontSize = 12.sp)
        }
    }
}

private fun overviewBackgroundBrush(): Brush {
    return Brush.verticalGradient(
        0f to DetailsBackgroundTop,
        0.35f to DetailsBackgroundMid,
        1f to BackgroundPrimary,
    )
}
