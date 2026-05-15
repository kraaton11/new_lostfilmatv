package com.kraat.lostfilmnewtv.ui.schedule

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import com.kraat.lostfilmnewtv.data.model.ScheduleDay
import com.kraat.lostfilmnewtv.data.model.ScheduleItem
import com.kraat.lostfilmnewtv.ui.components.ShimmerSkeletonBox
import com.kraat.lostfilmnewtv.ui.components.rememberShimmerSkeletonBrush
import com.kraat.lostfilmnewtv.ui.theme.BackgroundPrimary
import com.kraat.lostfilmnewtv.ui.theme.DetailsAccentGold
import com.kraat.lostfilmnewtv.ui.theme.DetailsAccentGoldFocus
import com.kraat.lostfilmnewtv.ui.theme.DetailsBackgroundMid
import com.kraat.lostfilmnewtv.ui.theme.DetailsBackgroundTop
import com.kraat.lostfilmnewtv.ui.theme.DetailsBorderDefault
import com.kraat.lostfilmnewtv.ui.theme.DetailsSurfaceFocused
import com.kraat.lostfilmnewtv.ui.theme.DetailsSurfaceReadable
import com.kraat.lostfilmnewtv.ui.theme.DetailsTextSecondary
import com.kraat.lostfilmnewtv.ui.theme.HomePanelBorder
import com.kraat.lostfilmnewtv.ui.theme.TextPrimary
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val fullDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
private val ScheduleRowShape = RoundedCornerShape(8.dp)
private val EpisodeChipShape = RoundedCornerShape(6.dp)

@Composable
fun ScheduleScreen(
    state: ScheduleUiState,
    onRetry: () -> Unit,
    onOpenItem: (ScheduleItem) -> Unit,
) {
    val firstItemRequester = remember { FocusRequester() }
    val retryRequester = remember { FocusRequester() }

    LaunchedEffect(state.schedule, state.errorMessage) {
        withFrameNanos { }
        when {
            state.schedule?.days?.any { it.items.isNotEmpty() } == true -> firstItemRequester.requestFocus()
            state.errorMessage != null -> retryRequester.requestFocus()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scheduleBackgroundBrush()),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 48.dp, top = 24.dp, end = 48.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Расписание",
                    color = TextPrimary,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = state.schedule?.title ?: "Даты выхода серий и фильмов LostFilm",
                    color = DetailsTextSecondary,
                    fontSize = 14.sp,
                )
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

                state.schedule?.days.isNullOrEmpty() -> {
                    ScheduleCenteredStatePanel {
                        Text(
                            text = "В расписании пока нет релизов",
                            color = TextPrimary,
                            fontSize = 18.sp,
                        )
                    }
                }

                else -> {
                    val days = state.schedule?.days.orEmpty()
                    val todayDate = days.firstOrNull { it.isToday }?.date ?: LocalDate.now()
                    val initialDayIndex = days.indexOfFirst { it.date >= todayDate }
                        .takeIf { it >= 0 }
                        ?: 0
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
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        itemsIndexed(days, key = { _, day -> day.date.toString() }) { index, day ->
                            ScheduleDaySection(
                                day = day,
                                todayDate = todayDate,
                                firstItemRequester = if (index == initialDayIndex) firstItemRequester else null,
                                onOpenItem = onOpenItem,
                            )
                        }
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
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .testTag("schedule-loading"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(5) { index ->
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ShimmerSkeletonBox(
                        brush = brush,
                        modifier = Modifier
                            .width(92.dp)
                            .height(22.dp),
                        shape = RoundedCornerShape(8.dp),
                    )
                    ShimmerSkeletonBox(
                        brush = brush,
                        modifier = Modifier
                            .width(if (index == 0) 62.dp else 76.dp)
                            .height(16.dp),
                        shape = RoundedCornerShape(6.dp),
                    )
                }
                repeat(if (index == 0) 3 else 2) { rowIndex ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(if (rowIndex == 1) 0.96f else 1f)
                            .height(82.dp)
                            .clip(ScheduleRowShape)
                            .background(DetailsSurfaceReadable)
                            .border(1.dp, HomePanelBorder, ScheduleRowShape)
                            .padding(start = 8.dp, end = 22.dp, top = 6.dp, bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(22.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ShimmerSkeletonBox(
                            brush = brush,
                            modifier = Modifier
                                .width(224.dp)
                                .fillMaxHeight(),
                            shape = RoundedCornerShape(6.dp),
                            baseColor = Color(0xFF172636),
                        )
                        ShimmerSkeletonBox(
                            brush = brush,
                            modifier = Modifier
                                .width(72.dp)
                                .height(36.dp),
                            shape = EpisodeChipShape,
                            baseColor = Color(0xFF3B277E),
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            ShimmerSkeletonBox(
                                brush = brush,
                                modifier = Modifier
                                    .fillMaxWidth(if (rowIndex == 0) 0.54f else 0.46f)
                                    .height(25.dp),
                                shape = RoundedCornerShape(8.dp),
                            )
                            ShimmerSkeletonBox(
                                brush = brush,
                                modifier = Modifier
                                    .padding(top = 5.dp)
                                    .width(72.dp)
                                    .height(18.dp),
                                shape = RoundedCornerShape(7.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleDaySection(
    day: ScheduleDay,
    todayDate: LocalDate,
    firstItemRequester: FocusRequester?,
    onOpenItem: (ScheduleItem) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = day.label,
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = day.date.format(fullDateFormatter),
                color = DetailsTextSecondary,
                fontSize = 13.sp,
            )
            day.relativeDayLabel(todayDate)?.let { relativeLabel ->
                Text(
                    text = relativeLabel,
                    color = DetailsAccentGold,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            day.items.forEachIndexed { index, item ->
                ScheduleItemRow(
                    item = item,
                    onClick = { onOpenItem(item) },
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
    todayDate -> "Сегодня"
    todayDate.plusDays(1) -> "Завтра"
    else -> null
}

@Composable
private fun ScheduleItemRow(
    item: ScheduleItem,
    onClick: () -> Unit,
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
    val kindLabel = when (item.kind) {
        ReleaseKind.SERIES -> "Сериал"
        ReleaseKind.MOVIE -> "Фильм"
    }

    Button(
        onClick = onClick,
        shape = ScheduleRowShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isFocused) DetailsSurfaceFocused else DetailsSurfaceReadable,
        ),
        contentPadding = PaddingValues(0.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(82.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .border(
                width = if (isFocused) 1.5.dp else 1.dp,
                color = if (isFocused) DetailsAccentGoldFocus else HomePanelBorder,
                shape = ScheduleRowShape,
            )
            .onFocusChanged { isFocused = it.isFocused },
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 8.dp, end = 22.dp, top = 6.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ScheduleItemImage(
                item = item,
                modifier = Modifier
                    .width(224.dp)
                    .fillMaxHeight(),
            )
            Box(
                modifier = Modifier
                    .width(72.dp)
                    .height(36.dp)
                    .background(Color(0xFF3B277E), EpisodeChipShape)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = item.episodeLabel?.takeIf { it.isNotBlank() } ?: kindLabel,
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = item.title,
                    color = TextPrimary,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = kindLabel,
                    color = DetailsTextSecondary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ScheduleItemImage(
    item: ScheduleItem,
    modifier: Modifier = Modifier,
) {
    val posterUrl = item.posterUrl?.takeIf { it.isNotBlank() }
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
            AsyncImage(
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
