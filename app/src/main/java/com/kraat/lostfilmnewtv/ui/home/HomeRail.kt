package com.kraat.lostfilmnewtv.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kraat.lostfilmnewtv.data.model.ReleaseSummary
import com.kraat.lostfilmnewtv.ui.components.ShimmerSkeletonBox
import com.kraat.lostfilmnewtv.ui.components.rememberShimmerSkeletonBrush
import com.kraat.lostfilmnewtv.ui.components.PosterCard
import com.kraat.lostfilmnewtv.ui.theme.HomePanelBorder
import com.kraat.lostfilmnewtv.ui.theme.HomePanelSurfaceStrong

private const val HOME_PAGING_SKELETON_COUNT = 4
private val HOME_RAIL_CARD_SPACING = 8.dp
private val HOME_RAIL_HORIZONTAL_PADDING = 12.dp
@OptIn(ExperimentalFoundationApi::class)
private val carouselCentering = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
        offset + (size - containerSize) / 2
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeRail(
    railId: String,
    items: List<ReleaseSummary>,
    focusedItemKey: String?,
    entryFocusRequester: FocusRequester,
    cardFocusRequesters: Map<String, FocusRequester>,
    shouldRequestFocus: Boolean,
    returnFocusRequestVersion: Int,
    leftTargetRequester: FocusRequester?,
    isPaging: Boolean,
    onItemFocused: (String) -> Unit,
    onRailFocusChanged: (Boolean) -> Unit,
    onOpenDetails: (String) -> Unit,
    onMoveToPreviousMode: (() -> Unit)? = null,
    onMoveToNextMode: (() -> Unit)? = null,
    onEndReached: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val itemKeys = remember(items) { items.map { it.detailsUrl } }
    val targetKey = if (focusedItemKey in itemKeys) {
        focusedItemKey
    } else {
        itemKeys.firstOrNull()
    }
    val targetIndex = targetKey?.let(itemKeys::indexOf) ?: -1
    val targetRequester = targetKey?.let { detailsUrl ->
        cardFocusRequesters[homeItemKey(railId, detailsUrl)]
    }
    val listState = remember(railId) { LazyListState() }
    var focusedCardKey by remember(railId) { mutableStateOf<String?>(null) }
    var handledReturnFocusRequestVersion by remember(railId) {
        mutableStateOf(returnFocusRequestVersion)
    }
    fun handleVerticalNavigation(key: Key): Boolean {
        return when {
            key == Key.DirectionUp && onMoveToPreviousMode != null -> {
                onMoveToPreviousMode()
                true
            }
            key == Key.DirectionDown && onMoveToNextMode != null -> {
                onMoveToNextMode()
                true
            }
            key == Key.DirectionUp || key == Key.DirectionDown -> true
            else -> false
        }
    }

    LaunchedEffect(shouldRequestFocus, returnFocusRequestVersion, targetIndex, targetRequester) {
        val hasReturnFocusRequest = returnFocusRequestVersion != handledReturnFocusRequestVersion
        if ((!shouldRequestFocus && !hasReturnFocusRequest) || targetRequester == null || targetIndex < 0) {
            return@LaunchedEffect
        }

        handledReturnFocusRequestVersion = returnFocusRequestVersion
        listState.scrollToItem(targetIndex)
        requestFocusWhenReady(targetRequester)
    }

    val isCarousel = railId == HOME_RAIL_MOVIES || railId == HOME_RAIL_SERIES
    val carouselTag = if (railId == HOME_RAIL_SERIES) "home-series-carousel" else "home-movie-carousel"
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = if (isCarousel) Alignment.BottomCenter else Alignment.Center,
    ) {
        val carouselCardWidth = homeCarouselCardWidth(maxWidth, maxHeight)
        val carouselCardSize = DpSize(carouselCardWidth, carouselCardWidth * (9f / 16f))
        val spacing = if (isCarousel) carouselCardWidth * -0.15f else HOME_RAIL_CARD_SPACING
        val carouselInsets = (maxWidth - carouselCardWidth) / 2
        CompositionLocalProvider(
            LocalBringIntoViewSpec provides if (isCarousel) carouselCentering else LocalBringIntoViewSpec.current,
        ) {
            LazyRow(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(entryFocusRequester)
                    .focusProperties {
                        up = FocusRequester.Cancel
                        down = FocusRequester.Cancel
                    }
                    .onPreviewKeyEvent { event ->
                        event.type == KeyEventType.KeyDown && handleVerticalNavigation(event.key)
                    }
                    .height(if (isCarousel) carouselCardSize.height else 218.dp)
                    .then(if (isCarousel) Modifier.testTag(carouselTag) else Modifier),
                horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalAlignment = if (isCarousel) Alignment.CenterVertically else Alignment.Top,
                contentPadding = if (isCarousel) PaddingValues(horizontal = carouselInsets) else PaddingValues(
                    start = HOME_RAIL_HORIZONTAL_PADDING,
                    top = 12.dp,
                    end = 10.dp,
                    bottom = 20.dp,
                ),
            ) {
                itemsIndexed(items = items, key = { _, item -> item.detailsUrl }) { index, item ->
                    val itemKey = homeItemKey(railId, item.detailsUrl)
                    val cardModifier = Modifier
                        .focusRequester(cardFocusRequesters.getValue(itemKey))
                        .focusProperties {
                            up = FocusRequester.Cancel
                            if (leftTargetRequester != null && index == 0) {
                                left = leftTargetRequester
                            }
                            down = FocusRequester.Cancel
                            if (isPaging && index == items.lastIndex) {
                                right = FocusRequester.Cancel
                            }
                        }
                        .testTag(posterTag(railId, item.detailsUrl))
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                onRailFocusChanged(true)
                                focusedCardKey = item.detailsUrl
                                onItemFocused(item.detailsUrl)
                                if (index == items.lastIndex) {
                                    onEndReached()
                                }
                            } else if (focusedCardKey == item.detailsUrl) {
                                onRailFocusChanged(false)
                                focusedCardKey = null
                            }
                        }
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) {
                                return@onPreviewKeyEvent false
                            }

                            when {
                                event.key == Key.Back && leftTargetRequester != null -> {
                                    leftTargetRequester.requestFocus()
                                    true
                                }
                                handleVerticalNavigation(event.key) -> true
                                else -> false
                            }
                        }
                    val open = { onOpenDetails(item.detailsUrl) }
                    if (isCarousel) {
                        HomeCarouselCard(
                            item = item,
                            selected = (focusedCardKey ?: targetKey) == item.detailsUrl,
                            onClick = open,
                            modifier = cardModifier.size(carouselCardSize),
                        )
                    } else {
                        PosterCard(
                            item = item,
                            onClick = open,
                            isFocused = focusedCardKey == item.detailsUrl,
                            modifier = cardModifier,
                        )
                    }
                }

                if (isPaging) {
                    items(
                        count = if (isCarousel) 1 else HOME_PAGING_SKELETON_COUNT,
                        key = { skeletonIndex -> "paging-skeleton-$skeletonIndex" },
                    ) { skeletonIndex ->
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(),
                            exit = fadeOut(),
                        ) {
                            HomePagingPosterSkeleton(
                                size = if (isCarousel) carouselCardSize * 0.82f else DpSize(112.dp, 172.dp),
                                modifier = Modifier
                                    .focusable(false)
                                    .testTag("home-paging-skeleton-$skeletonIndex"),
                            )
                        }
                    }
                }
            }
        }
    }
}

internal fun posterTag(detailsUrl: String): String = posterTag(HOME_RAIL_ALL_NEW, detailsUrl)

internal fun homeCarouselCardWidth(maxWidth: Dp, maxHeight: Dp): Dp =
    minOf(maxWidth * 0.32f, maxHeight * (16f / 9f), 360.dp)

internal fun posterTag(railId: String, detailsUrl: String): String = "poster:$railId:$detailsUrl"

@Composable
private fun HomePagingPosterSkeleton(size: DpSize, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    val shimmerBrush = rememberShimmerSkeletonBrush(
        label = "homePagingSkeleton",
        baseColor = HomePanelSurfaceStrong,
        highlightColor = Color.White,
        baseAlpha = 0.72f,
        highlightAlpha = 0.12f,
        startOffset = -220f,
        endOffset = 420f,
        shimmerWidth = 180f,
        verticalOffset = 264f,
        durationMillis = 1_150,
    )

    ShimmerSkeletonBox(
        brush = shimmerBrush,
        modifier = modifier.size(size),
        shape = shape,
        baseColor = HomePanelSurfaceStrong,
        borderColor = HomePanelBorder.copy(alpha = 0.20f),
    )
}
