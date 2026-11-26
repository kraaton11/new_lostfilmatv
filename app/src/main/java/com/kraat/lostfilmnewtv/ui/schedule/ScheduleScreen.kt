package com.kraat.lostfilmnewtv.ui.schedule

import android.view.KeyEvent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.request.ImageRequest
import com.kraat.lostfilmnewtv.ui.components.resolveArtworkForDisplay
import com.kraat.lostfilmnewtv.ui.components.PosterImage
import com.kraat.lostfilmnewtv.ui.components.TvColorKeyLabel
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import com.kraat.lostfilmnewtv.data.model.ScheduleDay
import com.kraat.lostfilmnewtv.data.model.ScheduleItem
import com.kraat.lostfilmnewtv.ui.components.ShimmerSkeletonBox
import com.kraat.lostfilmnewtv.ui.components.rememberShimmerSkeletonBrush
import com.kraat.lostfilmnewtv.ui.home.requestFocusWhenReady
import com.kraat.lostfilmnewtv.ui.theme.BackgroundPrimary
import com.kraat.lostfilmnewtv.ui.theme.DetailsAccentGold
import com.kraat.lostfilmnewtv.ui.theme.DetailsAccentGoldFocus
import com.kraat.lostfilmnewtv.ui.theme.DetailsBackgroundMid
import com.kraat.lostfilmnewtv.ui.theme.DetailsBackgroundTop
import com.kraat.lostfilmnewtv.ui.theme.DetailsBorderDefault
import com.kraat.lostfilmnewtv.ui.theme.DetailsSurfaceReadable
import com.kraat.lostfilmnewtv.ui.theme.DetailsTextSecondary
import com.kraat.lostfilmnewtv.ui.theme.HomePanelBorder
import com.kraat.lostfilmnewtv.ui.theme.TextPrimary
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val fullDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
private val ScheduleRowShape = RoundedCornerShape(8.dp)
private val ScheduleRowHeight = 74.dp
private val ScheduleArtworkSize = 54.dp
private val SchedulePreviewArtworkSize = 104.dp
private val ScheduleColumnSpacing = 22.dp
private val SchedulePreviewShape = RoundedCornerShape(16.dp)

private data class ScheduleSelection(val item: ScheduleItem, val date: LocalDate)

@Composable
fun ScheduleScreen(
    state: ScheduleUiState,
    onRetry: () -> Unit,
    onOpenItem: (ScheduleItem) -> Unit,
    onModeSelected: (Boolean) -> Unit = {},
) {
    val firstItemRequester = remember { FocusRequester() }
    val retryRequester = remember { FocusRequester() }
    val modeRequester = remember { FocusRequester() }
    var isSwitchingMode by remember { mutableStateOf(false) }
    val days = state.schedule?.days.orEmpty()
    val hasScheduleItems = days.any { it.items.isNotEmpty() }

    LaunchedEffect(state.schedule, state.errorMessage, state.isLoading) {
        val target = when {
            hasScheduleItems -> firstItemRequester
            state.errorMessage != null -> retryRequester
            else -> modeRequester
        }
        requestFocusWhenReady(target)
        if (target != modeRequester) isSwitchingMode = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                val keyEvent = event.nativeKeyEvent
                val favoritesOnly = when (keyEvent.keyCode) {
                    KeyEvent.KEYCODE_PROG_RED -> false
                    KeyEvent.KEYCODE_PROG_GREEN -> true
                    else -> return@onPreviewKeyEvent false
                }
                if (keyEvent.action == KeyEvent.ACTION_DOWN && keyEvent.repeatCount == 0 &&
                    favoritesOnly != state.favoritesOnly
                ) {
                    isSwitchingMode = true
                    modeRequester.requestFocus()
                    onModeSelected(favoritesOnly)
                }
                true
            }
            .background(scheduleBackgroundBrush()),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 48.dp, top = 24.dp, end = 48.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Расписание",
                color = TextPrimary,
                fontSize = 30.sp,
                fontWeight = FontWeight.SemiBold,
            )

            Row(
                modifier = Modifier
                    .focusRequester(modeRequester)
                    .focusProperties { canFocus = isSwitchingMode || !hasScheduleItems && state.errorMessage == null }
                    .focusable(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TvColorKeyLabel("Все", Color(0xFFFF636B), selected = !state.favoritesOnly)
                TvColorKeyLabel("Избранные сериалы", Color(0xFF69DD93), selected = state.favoritesOnly)
            }

            when {
                state.isLoading && state.schedule == null -> {
                    ScheduleLoadingSkeleton()
                }

                state.errorMessage != null && state.schedule == null -> {
                    ScheduleCenteredStatePanel {
                        Text(
                            text = state.errorMessage,
                            color = DetailsTextSecondary,
                            fontSize = 18.sp,
                        )
                        Button(
                            onClick = onRetry,
                            modifier = Modifier
                                .focusRequester(retryRequester)
                                .testTag("schedule-retry"),
                            colors = ButtonDefaults.buttonColors(containerColor = DetailsAccentGold),
                        ) {
                            Text("Повторить", color = Color(0xFF17120D))
                        }
                    }
                }

                !hasScheduleItems -> {
                    ScheduleCenteredStatePanel {
                        Text(
                            text = if (state.favoritesOnly) {
                                "В расписании избранных сериалов пока нет релизов"
                            } else {
                                "В расписании пока нет релизов"
                            },
                            color = TextPrimary,
                            fontSize = 18.sp,
                        )
                    }
                }

                else -> {
                    val todayDate = days.firstOrNull { it.isToday }?.date ?: LocalDate.now()
                    val initialDayIndex = days.indexOfFirst { it.date >= todayDate && it.items.isNotEmpty() }
                        .takeIf { it >= 0 }
                        ?: days.indexOfFirst { it.items.isNotEmpty() }
                    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialDayIndex)
                    LaunchedEffect(days, initialDayIndex) {
                        if (days.isNotEmpty()) {
                            listState.scrollToItem(initialDayIndex)
                        }
                    }
                    if (state.isLoading) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                color = DetailsAccentGold,
                                strokeWidth = 2.dp,
                            )
                            Text(
                                text = "Обновляем расписание",
                                color = DetailsTextSecondary,
                                fontSize = 14.sp,
                            )
                        }
                    }
                    val initialDay = days[initialDayIndex]
                    var selection by remember(days) {
                        mutableStateOf(ScheduleSelection(initialDay.items.first(), initialDay.date))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(ScheduleColumnSpacing),
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.weight(0.58f).fillMaxHeight(),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            itemsIndexed(days, key = { _, day -> day.date.toString() }) { index, day ->
                                ScheduleDaySection(
                                    day = day,
                                    todayDate = todayDate,
                                    firstItemRequester = if (index == initialDayIndex) firstItemRequester else null,
                                    onItemFocused = { item -> selection = ScheduleSelection(item, day.date) },
                                    onOpenItem = onOpenItem,
                                )
                            }
                        }
                        ScheduleReleasePreview(selection, Modifier.weight(0.42f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.ScheduleLoadingSkeleton() {
    val brush = rememberShimmerSkeletonBrush(
        label = "schedule-loading",
        baseColor = DetailsSurfaceReadable,
        highlightColor = Color.White,
        baseAlpha = 0.72f,
        highlightAlpha = 0.10f,
        startOffset = -560f,
        endOffset = 1_360f,
        shimmerWidth = 520f,
        verticalOffset = 180f,
        durationMillis = 1_350,
    )
    Row(
        modifier = Modifier.fillMaxWidth().weight(1f).testTag("schedule-loading"),
        horizontalArrangement = Arrangement.spacedBy(ScheduleColumnSpacing),
    ) {
        LazyColumn(
            modifier = Modifier.weight(0.58f).fillMaxHeight(),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(4) { index ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ShimmerSkeletonBox(brush, Modifier.width(108.dp).height(24.dp), RoundedCornerShape(8.dp))
                        ShimmerSkeletonBox(brush, Modifier.width(80.dp).height(15.dp), RoundedCornerShape(6.dp))
                    }
                    repeat(if (index == 0) 4 else 2) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(ScheduleRowHeight)
                                .clip(ScheduleRowShape)
                                .background(DetailsSurfaceReadable)
                                .border(1.dp, HomePanelBorder, ScheduleRowShape)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ShimmerSkeletonBox(
                                brush,
                                Modifier.width(ScheduleArtworkSize).height(ScheduleArtworkSize),
                                RoundedCornerShape(6.dp),
                                baseColor = Color(0xFF172636),
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                ShimmerSkeletonBox(brush, Modifier.width(270.dp).height(22.dp), RoundedCornerShape(8.dp))
                                ShimmerSkeletonBox(brush, Modifier.width(185.dp).height(16.dp), RoundedCornerShape(7.dp))
                            }
                        }
                    }
                }
            }
        }
        Column(
            modifier = Modifier
                .weight(0.42f)
                .background(DetailsSurfaceReadable, SchedulePreviewShape)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ShimmerSkeletonBox(
                brush,
                Modifier.width(SchedulePreviewArtworkSize).height(SchedulePreviewArtworkSize),
                RoundedCornerShape(8.dp),
            )
            ShimmerSkeletonBox(brush, Modifier.fillMaxWidth(0.85f).height(30.dp), RoundedCornerShape(8.dp))
            ShimmerSkeletonBox(brush, Modifier.fillMaxWidth(0.65f).height(18.dp), RoundedCornerShape(6.dp))
            ShimmerSkeletonBox(brush, Modifier.width(110.dp).height(16.dp), RoundedCornerShape(6.dp))
        }
    }
}

@Composable
private fun ScheduleDaySection(
    day: ScheduleDay,
    todayDate: LocalDate,
    firstItemRequester: FocusRequester?,
    onItemFocused: (ScheduleItem) -> Unit,
    onOpenItem: (ScheduleItem) -> Unit,
) {
    val groupDate = day.date.format(fullDateFormatter)
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = day.relativeDayLabel(todayDate) ?: day.label,
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = groupDate,
                color = DetailsTextSecondary,
                fontSize = 13.sp,
            )

        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            day.items.forEachIndexed { index, item ->
                ScheduleItemRow(
                    item = item,
                    groupDate = groupDate,
                    onClick = { onOpenItem(item) },
                    onFocused = { onItemFocused(item) },
                    modifier = if (index == 0 && firstItemRequester != null) {
                        Modifier.focusRequester(firstItemRequester)
                    } else {
                        Modifier
                    },
                )
            }
        }
    }
}

private fun ScheduleDay.relativeDayLabel(todayDate: LocalDate): String? = when (date) {
    todayDate -> "Сегодня".takeUnless { dayHasSchedulePeriodLabel() }
    todayDate.plusDays(1) -> "Завтра".takeUnless { dayHasSchedulePeriodLabel() }
    else -> null
}

private fun ScheduleDay.dayHasSchedulePeriodLabel(): Boolean {
    val normalized = label.lowercase()
    return normalized == "сегодня" ||
        normalized == "на этой неделе" ||
        normalized == "на следующей неделе" ||
        normalized == "позже"
}

@Composable
private fun ScheduleItemRow(
    item: ScheduleItem,
    groupDate: String,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.015f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "scheduleItemScale",
    )
    val episodeLabel = item.episodeLabel?.takeIf { it.isNotBlank() }?.replace('х', '×')
    val episodeTitle = item.episodeTitle?.takeIf { it.isNotBlank() && it != item.title }
    val subtitle = listOfNotNull(episodeLabel, episodeTitle).joinToString(" · ").ifBlank {
        if (item.kind == ReleaseKind.MOVIE) "Фильм" else "Сериал"
    }
    val releaseDate = item.releaseDateLabel?.takeIf { it.isNotBlank() && !it.contains(groupDate) }
    val relativeDate = item.relativeDateLabel?.takeIf { it.isNotBlank() }

    Button(
        onClick = onClick,
        shape = ScheduleRowShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isFocused) lerp(DetailsSurfaceReadable, Color.White, 0.08f) else DetailsSurfaceReadable,
        ),
        contentPadding = PaddingValues(0.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(ScheduleRowHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .border(
                width = if (isFocused) 1.5.dp else 1.dp,
                color = if (isFocused) DetailsAccentGoldFocus else HomePanelBorder,
                shape = ScheduleRowShape,
            )
            .onFocusChanged {
                if (it.isFocused && !isFocused) onFocused()
                isFocused = it.isFocused
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 10.dp, end = 18.dp, top = 6.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ScheduleItemImage(
                item = item,
                modifier = Modifier.width(ScheduleArtworkSize).height(ScheduleArtworkSize),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = item.title,
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    color = DetailsTextSecondary,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (releaseDate != null || relativeDate != null) {
                Column(horizontalAlignment = Alignment.End) {
                    releaseDate?.let {
                        Text(it, color = DetailsTextSecondary, fontSize = 13.sp, maxLines = 1)
                    }
                    relativeDate?.let {
                        Text(it, color = DetailsAccentGold, fontSize = 12.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleReleasePreview(selection: ScheduleSelection, modifier: Modifier = Modifier) {
    val item = selection.item
    val releaseDate = item.releaseDateLabel?.takeIf { it.isNotBlank() } ?: selection.date.format(fullDateFormatter)
    val episodeLabel = item.episodeLabel?.takeIf { it.isNotBlank() }?.replace('х', '×')
    val episodeTitle = item.episodeTitle?.takeIf { it.isNotBlank() && it != item.title }
    val episode = listOfNotNull(episodeLabel, episodeTitle).joinToString(" · ")

    Column(
        modifier = modifier
            .background(DetailsSurfaceReadable, SchedulePreviewShape)
            .border(1.dp, HomePanelBorder, SchedulePreviewShape)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScheduleItemImage(
            item = item,
            modifier = Modifier.width(SchedulePreviewArtworkSize).height(SchedulePreviewArtworkSize),
        )
        Text(
            text = item.title,
            color = TextPrimary,
            fontSize = 25.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.SemiBold,
        )
        if (episode.isNotEmpty()) {
            Text(episode, color = DetailsTextSecondary, fontSize = 17.sp)
        }
        Text(releaseDate, color = DetailsAccentGold, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        item.relativeDateLabel?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = DetailsTextSecondary, fontSize = 14.sp)
        }
    }
}

@Composable
private fun ScheduleItemImage(
    item: ScheduleItem,
    modifier: Modifier = Modifier,
) {
    val posterUrl = resolveArtworkForDisplay(item.posterUrl)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                Brush.horizontalGradient(
                    0f to Color(0xFF172636),
                    1f to Color(0xFF0B1320),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (posterUrl != null) {
            val request = rememberScheduleImageRequest(posterUrl)
            PosterImage(
                model = request,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = item.title,
                color = DetailsTextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 14.dp),
            )
        }
    }
}

@Composable
private fun rememberScheduleImageRequest(posterUrl: String): ImageRequest {
    val context = LocalContext.current
    return remember(context, posterUrl) {
        ImageRequest.Builder(context)
            .data(posterUrl)
            .crossfade(true)
            .build()
    }
}

@Composable
private fun ColumnScope.ScheduleCenteredStatePanel(
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
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

private fun scheduleBackgroundBrush(): Brush = Brush.verticalGradient(
    0f to DetailsBackgroundTop,
    0.32f to DetailsBackgroundMid,
    1f to BackgroundPrimary,
)
