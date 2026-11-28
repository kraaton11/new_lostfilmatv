package com.kraat.lostfilmnewtv.ui.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kraat.lostfilmnewtv.ui.components.resolveArtworkForDisplay
import com.kraat.lostfilmnewtv.data.model.SeriesOverview
import com.kraat.lostfilmnewtv.ui.theme.BackgroundPrimary
import com.kraat.lostfilmnewtv.ui.theme.DetailsAccentGold
import com.kraat.lostfilmnewtv.ui.theme.DetailsBackgroundMid
import com.kraat.lostfilmnewtv.ui.theme.DetailsBackgroundTop
import com.kraat.lostfilmnewtv.ui.theme.DetailsBorderDefault
import com.kraat.lostfilmnewtv.ui.theme.DetailsSurfaceReadable
import com.kraat.lostfilmnewtv.ui.theme.DetailsTextSecondary
import com.kraat.lostfilmnewtv.ui.theme.LocalLostFilmColors
import com.kraat.lostfilmnewtv.ui.theme.TextPrimary

private val overviewParagraphSplitRegex = Regex("""\n\s*\n+""")
private val overviewSentenceSplitRegex = Regex("""(?<=[.!?…])\s+""")
private val overviewWhitespaceRegex = Regex("""\s+""")

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
    val context = LocalContext.current
    val imageUrl = resolveArtworkForDisplay(overview?.backdropUrl)
        ?: resolveArtworkForDisplay(overview?.posterUrl)
        ?: return
    val request = remember(context, imageUrl) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .size(1280, 720)
            .build()
    }

    AsyncImage(
        model = request,
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
    OverviewLoadingLayout(tag = "series-overview-loading", showSubtitle = true)
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
                .split(overviewParagraphSplitRegex)
                .map { it.trim() }
                .filter { it.isNotBlank() }
            val chunks = paragraphs.flatMap { it.chunkForTvReading(maxChars = 520) }
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
            OverviewHeader(
                title = safeOverview.titleRu,
                subtitle = safeOverview.titleEn,
                artwork = safeOverview.posterUrl,
                facts = listOfNotNull(
                    safeOverview.genresRu,
                    safeOverview.imdbRating?.let { "IMDb $it" },
                    safeOverview.premiereDateRu,
                    safeOverview.channelCountryRu,
                    safeOverview.statusRu,
                    safeOverview.typesRu,
                ).filter { it.isNotBlank() },
                modifier = Modifier
                    .focusRequester(heroRequester)
                    .focusable()
                    .testTag("series-overview-hero"),
            )
        }

        items(
            items = sectionBlocks,
            key = { section -> section.key },
        ) { section ->
            OverviewReadingBlock(section.title, section.body, section.tag)
        }
    }
}

private fun String.chunkForTvReading(maxChars: Int): List<String> {
    val normalized = trim()
    if (normalized.length <= maxChars) return listOf(normalized)

    val chunks = mutableListOf<String>()
    val sentences = normalized
        .split(overviewSentenceSplitRegex)
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
    split(overviewWhitespaceRegex).filter { it.isNotBlank() }.forEach { word ->
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
private fun OverviewActionButton(
    label: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = DetailsAccentGold,
            contentColor = LocalLostFilmColors.current.onAccent,
        ),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = label, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, fontSize = 12.sp)
        }
    }
}

@Composable
private fun overviewBackgroundBrush(): Brush {
    return Brush.verticalGradient(
        0f to DetailsBackgroundTop,
        0.35f to DetailsBackgroundMid,
        1f to BackgroundPrimary,
    )
}
