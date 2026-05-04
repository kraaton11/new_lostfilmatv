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
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import com.kraat.lostfilmnewtv.ui.theme.DetailsBackgroundMid
import com.kraat.lostfilmnewtv.ui.theme.DetailsBackgroundTop
import com.kraat.lostfilmnewtv.ui.theme.DetailsBorderDefault
import com.kraat.lostfilmnewtv.ui.theme.DetailsSurfaceReadable
import com.kraat.lostfilmnewtv.ui.theme.DetailsSurfaceSoft
import com.kraat.lostfilmnewtv.ui.theme.DetailsTextMuted
import com.kraat.lostfilmnewtv.ui.theme.DetailsTextSecondary
import com.kraat.lostfilmnewtv.ui.theme.TextPrimary

private data class OverviewSection(
    val key: String,
    val title: String,
    val body: String,
    val tag: String,
)

private data class OverviewSectionBlock(
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
    val brush = Brush.linearGradient(
        listOf(
            DetailsSurfaceSoft.copy(alpha = 0.55f),
            DetailsBorderDefault.copy(alpha = 0.45f),
            DetailsSurfaceSoft.copy(alpha = 0.55f),
        ),
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 48.dp, top = 28.dp, end = 48.dp, bottom = 28.dp)
            .testTag("series-overview-loading"),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Box(
                modifier = Modifier
                    .size(width = 250.dp, height = 360.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(brush),
            )
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(modifier = Modifier.size(width = 620.dp, height = 52.dp).clip(RoundedCornerShape(12.dp)).background(brush))
                Box(modifier = Modifier.size(width = 430.dp, height = 28.dp).clip(RoundedCornerShape(10.dp)).background(brush))
                Box(modifier = Modifier.size(width = 760.dp, height = 126.dp).clip(RoundedCornerShape(18.dp)).background(brush))
            }
        }
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
    val sectionBlocks = remember(sections) {
        sections.flatMap { section ->
            val paragraphs = section.body
                .split(Regex("""\n\s*\n+"""))
                .map { it.trim() }
                .filter { it.isNotBlank() }
            val chunks = if (paragraphs.size > 1) {
                paragraphs
            } else {
                section.body.chunkForTvReading(maxChars = 1200)
            }
            chunks.mapIndexed { index, chunk ->
                OverviewSectionBlock(
                    key = if (index == 0) section.key else "${section.key}-$index",
                    title = if (index == 0) section.title else "",
                    body = chunk,
                    tag = if (index == 0) section.tag else "${section.tag}-$index",
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
            .padding(start = 48.dp, top = 22.dp, end = 48.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "hero") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(heroRequester)
                    .focusable()
                    .testTag("series-overview-hero"),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 250.dp, height = 360.dp)
                        .shadow(
                            elevation = 34.dp,
                            spotColor = Color.Black.copy(alpha = 0.55f),
                            ambientColor = Color.Black.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(24.dp),
                        )
                        .clip(RoundedCornerShape(24.dp))
                        .background(DetailsSurfaceSoft)
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
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Обзор сериала",
                        color = DetailsAccentGold,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = safeOverview.titleRu,
                        color = TextPrimary,
                        fontSize = 50.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 54.sp,
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
                    OverviewChipRow(safeOverview)
                    safeOverview.descriptionRu?.takeIf { it.isNotBlank() }?.let { description ->
                        Text(
                            text = description,
                            color = TextPrimary.copy(alpha = 0.92f),
                            fontSize = 19.sp,
                            lineHeight = 28.sp,
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
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Информация",
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            infoRows.take(4).forEach { (label, value) ->
                                OverviewMetaBlock(
                                    label = label,
                                    value = value.orEmpty(),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }

        items(
            items = sectionBlocks,
            key = { section -> section.key },
        ) { section ->
            var isFocused by remember { mutableStateOf(false) }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { isFocused = it.isFocused }
                    .focusable()
                    .testTag(section.tag)
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (section.title.isNotBlank()) {
                    Text(
                        text = section.title,
                        color = DetailsAccentGold,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = section.body,
                    color = TextPrimary.copy(alpha = if (isFocused) 1f else 0.85f),
                    fontSize = 18.sp,
                    lineHeight = 27.sp,
                )
            }
        }
    }
}

private fun String.chunkForTvReading(maxChars: Int): List<String> {
    val normalized = trim()
    if (normalized.length <= maxChars) return listOf(normalized)

    val chunks = mutableListOf<String>()
    val sentences = normalized
        .split(Regex("""(?<=[.!?…])\s+"""))
        .filter { it.isNotBlank() }
    var current = StringBuilder()

    fun flushCurrent() {
        val value = current.toString().trim()
        if (value.isNotBlank()) chunks += value
        current = StringBuilder()
    }

    sentences.forEach { sentence ->
        val candidateLength = current.length + sentence.length + 1
        if (candidateLength > maxChars && current.isNotBlank()) {
            flushCurrent()
        }
        if (sentence.length > maxChars) {
            sentence.splitByWords(maxChars).forEach { part ->
                if (current.isNotBlank()) flushCurrent()
                chunks += part
            }
        } else {
            if (current.isNotBlank()) current.append(' ')
            current.append(sentence)
        }
    }
    flushCurrent()

    return chunks.ifEmpty { listOf(normalized) }
}

private fun String.splitByWords(maxChars: Int): List<String> {
    val parts = mutableListOf<String>()
    var current = StringBuilder()
    split(Regex("""\s+""")).filter { it.isNotBlank() }.forEach { word ->
        if (current.length + word.length + 1 > maxChars && current.isNotBlank()) {
            parts += current.toString().trim()
            current = StringBuilder()
        }
        if (current.isNotBlank()) current.append(' ')
        current.append(word)
    }
    val tail = current.toString().trim()
    if (tail.isNotBlank()) parts += tail
    return parts
}

@Composable
private fun OverviewChipRow(overview: SeriesOverview) {
    val chips = listOfNotNull(
        overview.statusRu?.takeIf { it.isNotBlank() },
        overview.premiereDateRu?.takeIf { it.isNotBlank() },
    )
    if (chips.isEmpty()) return

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        chips.forEach { chip ->
            Text(
                text = chip,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .border(1.dp, DetailsBorderDefault, RoundedCornerShape(999.dp))
                    .background(DetailsSurfaceSoft.copy(alpha = 0.72f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun OverviewMetaBlock(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .height(82.dp)
            .background(DetailsSurfaceSoft.copy(alpha = 0.58f), RoundedCornerShape(16.dp))
            .border(1.dp, DetailsBorderDefault.copy(alpha = 0.75f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            text = label,
            color = DetailsTextMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 15.sp,
            lineHeight = 19.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun OverviewSurfaceCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)

    Column(
        modifier = modifier
            .background(
                color = DetailsSurfaceReadable,
                shape = shape,
            )
            .border(
                width = 1.dp,
                color = DetailsBorderDefault,
                shape = shape,
            )
            .padding(horizontal = 22.dp, vertical = 18.dp),
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
