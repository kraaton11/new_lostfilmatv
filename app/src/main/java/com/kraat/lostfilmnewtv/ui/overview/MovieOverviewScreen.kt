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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.kraat.lostfilmnewtv.data.model.ReleaseDetails
import com.kraat.lostfilmnewtv.ui.components.ShimmerSkeletonBox
import com.kraat.lostfilmnewtv.ui.components.rememberShimmerSkeletonBrush
import com.kraat.lostfilmnewtv.ui.theme.BackgroundPrimary
import com.kraat.lostfilmnewtv.ui.theme.DetailsAccentGold
import com.kraat.lostfilmnewtv.ui.theme.DetailsBackgroundMid
import com.kraat.lostfilmnewtv.ui.theme.DetailsBackgroundTop
import com.kraat.lostfilmnewtv.ui.theme.DetailsBorderDefault
import com.kraat.lostfilmnewtv.ui.theme.DetailsSurfaceReadable
import com.kraat.lostfilmnewtv.ui.theme.DetailsSurfaceSoft
import com.kraat.lostfilmnewtv.ui.theme.DetailsTextSecondary
import com.kraat.lostfilmnewtv.ui.theme.TextPrimary

private data class MovieOverviewBlock(
    val key: String,
    val body: String,
    val tag: String,
)

@Composable
fun MovieOverviewScreen(
    state: MovieOverviewUiState,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(movieOverviewBackgroundBrush()),
    ) {
        MovieOverviewBackgroundPoster(state.details)

        when {
            state.isLoading && state.details == null -> MovieOverviewLoadingState()
            state.errorMessage != null && state.details == null -> MovieOverviewErrorState(
                message = state.errorMessage,
                onRetry = onRetry,
            )
            state.details == null -> MovieOverviewEmptyState()
            else -> MovieOverviewContent(details = state.details)
        }
    }
}

@Composable
private fun MovieOverviewBackgroundPoster(details: ReleaseDetails?) {
    val imageUrl = details?.backdropUrl?.takeIf { !it.isNullOrBlank() }
        ?: details?.posterUrl
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
private fun MovieOverviewLoadingState() {
    val brush = rememberShimmerSkeletonBrush(
        label = "movieOverviewSkeleton",
        baseColor = DetailsSurfaceSoft,
        highlightColor = DetailsBorderDefault,
        baseAlpha = 0.56f,
        highlightAlpha = 0.66f,
        startOffset = -560f,
        endOffset = 1_360f,
        shimmerWidth = 520f,
        durationMillis = 1_400,
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 48.dp, top = 22.dp, end = 48.dp, bottom = 22.dp)
            .testTag("movie-overview-loading"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            ShimmerSkeletonBox(
                brush = brush,
                modifier = Modifier.size(width = 175.dp, height = 252.dp),
                shape = RoundedCornerShape(24.dp),
                borderColor = DetailsBorderDefault.copy(alpha = 0.34f),
            )
            Column(
                modifier = Modifier.padding(top = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ShimmerSkeletonBox(
                    brush = brush,
                    modifier = Modifier.size(width = 130.dp, height = 18.dp),
                    shape = RoundedCornerShape(9.dp),
                )
                ShimmerSkeletonBox(
                    brush = brush,
                    modifier = Modifier.size(width = 620.dp, height = 54.dp),
                    shape = RoundedCornerShape(14.dp),
                )
                ShimmerSkeletonBox(
                    brush = brush,
                    modifier = Modifier.size(width = 280.dp, height = 28.dp),
                    shape = RoundedCornerShape(12.dp),
                )
                ShimmerSkeletonBox(
                    brush = brush,
                    modifier = Modifier.size(width = 118.dp, height = 26.dp),
                    shape = RoundedCornerShape(12.dp),
                )
            }
        }

        ShimmerSkeletonBox(
            brush = brush,
            modifier = Modifier.size(width = 120.dp, height = 18.dp),
            shape = RoundedCornerShape(9.dp),
        )
        repeat(5) { index ->
            ShimmerSkeletonBox(
                brush = brush,
                modifier = Modifier
                    .fillMaxWidth(if (index == 4) 0.64f else 1f)
                    .height(18.dp),
                shape = RoundedCornerShape(9.dp),
            )
        }
        repeat(3) { index ->
            ShimmerSkeletonBox(
                brush = brush,
                modifier = Modifier
                    .fillMaxWidth(listOf(0.96f, 0.88f, 0.72f)[index])
                    .height(18.dp),
                shape = RoundedCornerShape(9.dp),
            )
        }
    }
}

@Composable
private fun MovieOverviewErrorState(
    message: String,
    onRetry: () -> Unit,
) {
    MovieOverviewCenteredStatePanel {
        Text(
            text = message,
            color = DetailsTextSecondary,
            fontSize = 18.sp,
        )
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(
                containerColor = DetailsAccentGold,
                contentColor = Color.Black,
            ),
        ) {
            Text(text = "Повторить", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun MovieOverviewEmptyState() {
    MovieOverviewCenteredStatePanel {
        Text(
            text = "Описание фильма пока недоступно",
            color = TextPrimary,
            fontSize = 18.sp,
        )
    }
}

@Composable
private fun MovieOverviewCenteredStatePanel(
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
private fun MovieOverviewContent(details: ReleaseDetails?) {
    val safeDetails = details ?: return
    val description = safeDetails.episodeOverviewRu
        ?.replace(Regex("""\s+"""), " ")
        ?.trim()
        .orEmpty()
    val blocks = remember(description) {
        description.chunkForMovieReading(maxChars = 1200)
            .mapIndexed { index, chunk ->
                MovieOverviewBlock(
                    key = "description-$index",
                    body = chunk,
                    tag = if (index == 0) "movie-overview-description" else "movie-overview-description-$index",
                )
            }
    }
    val heroRequester = remember { FocusRequester() }

    LaunchedEffect(safeDetails.detailsUrl) {
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
                    .testTag("movie-overview-hero"),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 175.dp, height = 252.dp)
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
                    if (safeDetails.posterUrl.isNotBlank()) {
                        AsyncImage(
                            model = safeDetails.posterUrl,
                            contentDescription = safeDetails.titleRu,
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
                        text = "Описание фильма",
                        color = DetailsAccentGold,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = safeDetails.titleRu,
                        color = TextPrimary,
                        fontSize = 50.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 54.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    safeDetails.releaseDateRu.takeIf { it.isNotBlank() }?.let { releaseDate ->
                        Text(
                            text = releaseDate,
                            color = DetailsTextSecondary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 28.sp,
                        )
                    }
                    safeDetails.tmdbRating?.takeIf { it.isNotBlank() }?.let { rating ->
                        Text(
                            text = "TMDB $rating",
                            color = DetailsTextSecondary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 26.sp,
                        )
                    }
                }
            }
        }

        if (blocks.isEmpty()) {
            item(key = "empty-description") {
                Text(
                    text = "Описание фильма пока недоступно",
                    color = DetailsTextSecondary,
                    fontSize = 18.sp,
                    modifier = Modifier.testTag("movie-overview-description-empty"),
                )
            }
        }

        items(
            items = blocks,
            key = { block -> block.key },
        ) { block ->
            var isFocused by remember { mutableStateOf(false) }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { isFocused = it.isFocused }
                    .focusable()
                    .testTag(block.tag)
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = if (block.key == "description-0") "Описание" else "",
                    color = DetailsAccentGold,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = block.body,
                    color = TextPrimary.copy(alpha = if (isFocused) 1f else 0.85f),
                    fontSize = 18.sp,
                    lineHeight = 27.sp,
                )
            }
        }
    }
}

private fun String.chunkForMovieReading(maxChars: Int): List<String> {
    val normalized = trim()
    if (normalized.isBlank()) return emptyList()
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
        if (current.length + sentence.length + 1 > maxChars && current.isNotBlank()) {
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

private fun movieOverviewBackgroundBrush(): Brush {
    return Brush.verticalGradient(
        0f to DetailsBackgroundTop,
        0.35f to DetailsBackgroundMid,
        1f to BackgroundPrimary,
    )
}
