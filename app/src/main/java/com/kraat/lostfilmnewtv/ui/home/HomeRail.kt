package com.kraat.lostfilmnewtv.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kraat.lostfilmnewtv.data.model.ReleaseSummary
import com.kraat.lostfilmnewtv.ui.components.PosterCard
import com.kraat.lostfilmnewtv.ui.theme.HomePanelBorder
import com.kraat.lostfilmnewtv.ui.theme.HomePanelSurfaceStrong

@Composable
fun HomeRail(
    railId: String,
    items: List<ReleaseSummary>,
    focusedItemKey: String?,
    entryFocusRequester: FocusRequester,
    cardFocusRequesters: Map<String, FocusRequester>,
    shouldRequestFocus: Boolean,
    returnFocusRequestVersion: Int,
    upTargetRequester: FocusRequester?,
    downTargetRequester: FocusRequester?,
    isPaging: Boolean,
    onItemFocused: (String) -> Unit,
    onOpenDetails: (String) -> Unit,
    onEndReached: () -> Unit,
) {
    val itemKeys = items.map { it.detailsUrl }
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

    LaunchedEffect(shouldRequestFocus, returnFocusRequestVersion, targetIndex, targetRequester) {
        val hasReturnFocusRequest = returnFocusRequestVersion != handledReturnFocusRequestVersion
        if ((!shouldRequestFocus && !hasReturnFocusRequest) || targetRequester == null || targetIndex < 0) {
            return@LaunchedEffect
        }

        handledReturnFocusRequestVersion = returnFocusRequestVersion
        listState.scrollToItem(targetIndex)
        requestFocusWhenReady(targetRequester)
    }

    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(entryFocusRequester)
            .focusProperties {
                onEnter = {
                    if (shouldRequestFocus && targetRequester != null) {
                        targetRequester.requestFocus()
                    }
                }
            }
            .focusable()
            .height(284.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 10.dp),
    ) {
        itemsIndexed(items = items, key = { _, item -> item.detailsUrl }) { index, item ->
            val itemKey = homeItemKey(railId, item.detailsUrl)
            PosterCard(
                item = item,
                isFocused = focusedCardKey == item.detailsUrl,
                modifier = Modifier
                    .focusRequester(cardFocusRequesters.getValue(itemKey))
                    .focusProperties {
                        if (upTargetRequester != null) {
                            up = upTargetRequester
                        }
                        if (downTargetRequester != null) {
                            down = downTargetRequester
                        }
                        if (isPaging && index == items.lastIndex) {
                            right = FocusRequester.Cancel
                        }
                    }
                    .testTag(posterTag(railId, item.detailsUrl))
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            focusedCardKey = item.detailsUrl
                            onItemFocused(item.detailsUrl)
                            if (index == items.lastIndex) {
                                onEndReached()
                            }
                        } else if (focusedCardKey == item.detailsUrl) {
                            focusedCardKey = null
                        }
                    }
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key.isPosterActivationKey()) {
                            onOpenDetails(item.detailsUrl)
                            true
                        } else {
                            false
                        }
                    }
                    .focusable()
                    .clickable { onOpenDetails(item.detailsUrl) },
            )
        }

        if (isPaging) {
            items(2) { skeletonIndex ->
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    HomePagingPosterSkeleton(
                        modifier = Modifier
                            .focusable(false)
                            .testTag("home-paging-skeleton-$skeletonIndex"),
                    )
                }
            }
        }
    }
}

internal fun posterTag(detailsUrl: String): String = posterTag(HOME_RAIL_ALL_NEW, detailsUrl)

internal fun posterTag(railId: String, detailsUrl: String): String = "poster:$railId:$detailsUrl"

@Composable
private fun HomePagingPosterSkeleton(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(22.dp)
    val transition = rememberInfiniteTransition(label = "homePagingSkeleton")
    val xOffset by transition.animateFloat(
        initialValue = -220f,
        targetValue = 420f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_150, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "homePagingSkeletonOffset",
    )
    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            HomePanelSurfaceStrong.copy(alpha = 0.72f),
            Color.White.copy(alpha = 0.10f),
            HomePanelSurfaceStrong.copy(alpha = 0.72f),
        ),
        start = Offset(xOffset, 0f),
        end = Offset(xOffset + 180f, 264f),
    )

    Box(
        modifier = modifier
            .size(width = 176.dp, height = 264.dp)
            .clip(shape)
            .background(shimmerBrush)
            .background(HomePanelBorder.copy(alpha = 0.18f), shape),
    )
}

private fun Key.isPosterActivationKey(): Boolean {
    return when (this) {
        Key.DirectionCenter,
        Key.Enter,
        Key.NumPadEnter,
        -> true

        else -> false
    }
}
