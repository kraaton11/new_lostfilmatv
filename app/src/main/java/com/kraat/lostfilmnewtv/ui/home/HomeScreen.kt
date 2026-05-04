package com.kraat.lostfilmnewtv.ui.home
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kraat.lostfilmnewtv.data.model.ReleaseSummary
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import com.kraat.lostfilmnewtv.updates.SavedAppUpdate
import com.kraat.lostfilmnewtv.ui.theme.BackgroundPrimary
import com.kraat.lostfilmnewtv.ui.theme.HomeAccentBlue
import com.kraat.lostfilmnewtv.ui.theme.HomeAccentGold
import com.kraat.lostfilmnewtv.ui.theme.HomePanelBorder
import com.kraat.lostfilmnewtv.ui.theme.HomePanelSurface
import com.kraat.lostfilmnewtv.ui.theme.HomePanelSurfaceStrong
import com.kraat.lostfilmnewtv.ui.theme.HomeStatusError
import com.kraat.lostfilmnewtv.ui.theme.HomeTextSecondary
import com.kraat.lostfilmnewtv.ui.theme.TextPrimary
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun HomeScreen(
    state: HomeUiState = demoHomeUiState(),
    onItemFocused: (String) -> Unit = {},
    onModeSelected: (HomeFeedMode) -> Unit = {},
    onOpenDetails: (String) -> Unit = {},
    onOpenSeriesOverview: (String) -> Unit = onOpenDetails,
    onEndReached: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onUpdateClick: () -> Unit = {},
    onAuthClick: () -> Unit = {},
    onRetry: () -> Unit = {},
    onPagingRetry: () -> Unit = {},
    isAuthenticated: Boolean = false,
    savedAppUpdate: SavedAppUpdate? = null,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val activeModeState = when (state.selectedMode) {
        HomeFeedMode.AllNew -> state.allNewModeState
        HomeFeedMode.Favorites -> state.favoritesModeState
        HomeFeedMode.Movies -> state.moviesModeState
        HomeFeedMode.Series -> state.seriesModeState
    }
    val isAllNewMode = state.selectedMode == HomeFeedMode.AllNew
    val activeItems = state.itemsForMode(state.selectedMode)
    val activeRailId = when (state.selectedMode) {
        HomeFeedMode.AllNew -> HOME_RAIL_ALL_NEW
        HomeFeedMode.Favorites -> HOME_RAIL_FAVORITES
        HomeFeedMode.Movies -> HOME_RAIL_MOVIES
        HomeFeedMode.Series -> HOME_RAIL_SERIES
    }
    val itemKeys = remember(activeItems) { activeItems.map { it.detailsUrl } }
    var focusedItemKey by rememberSaveable(state.selectedMode, itemKeys) {
        mutableStateOf(
            state.selectedItemKey ?: itemKeys.firstOrNull(),
        )
    }
    var lastSyncedKey by remember { mutableStateOf<String?>(null) }
    var startupContentFocusPending by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(state.selectedItemKey, state.selectedMode, itemKeys) {
        val preferredKey = state.selectedItemKey ?: itemKeys.firstOrNull()
        if (preferredKey != null && preferredKey != lastSyncedKey) {
            focusedItemKey = preferredKey
            lastSyncedKey = preferredKey
        }
    }

    BackHandler(enabled = state.selectedMode != HomeFeedMode.AllNew) {
        startupContentFocusPending = true
        onModeSelected(HomeFeedMode.AllNew)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                startupContentFocusPending = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val modeToggleRequester = remember { FocusRequester() }
    val searchRequester = remember { FocusRequester() }
    val settingsRequester = remember { FocusRequester() }
    val updateRequester = remember { FocusRequester() }
    val loginActionRequester = remember { FocusRequester() }
    val retryActionRequester = remember { FocusRequester() }
    val contentEntryRequester = remember(activeRailId) { FocusRequester() }
    val cardFocusRequesters = remember(activeRailId) {
        linkedMapOf<String, FocusRequester>()
    }
    val focusScope = rememberCoroutineScope()
    val activeItemFocusKeys = itemKeys.map { detailsUrl -> homeItemKey(activeRailId, detailsUrl) }
    cardFocusRequesters.keys.retainAll(activeItemFocusKeys.toSet())
    activeItemFocusKeys.forEach { itemKey ->
        cardFocusRequesters.getOrPut(itemKey) { FocusRequester() }
    }
    val activeContentDetailsUrl = focusedItemKey?.takeIf(itemKeys::contains) ?: itemKeys.firstOrNull()
    val activeContentRequester = activeContentDetailsUrl?.let { detailsUrl ->
        cardFocusRequesters[homeItemKey(activeRailId, detailsUrl)]
    }
    val headerDownTarget = when (activeModeState) {
        is HomeModeContentState.Content -> activeContentRequester ?: contentEntryRequester
        is HomeModeContentState.Error -> retryActionRequester
        is HomeModeContentState.LoginRequired -> loginActionRequester
        else -> null
    }
    val railItems = (activeModeState as? HomeModeContentState.Content)?.items ?: activeItems
    val focusedItem = railItems.firstOrNull { it.detailsUrl == focusedItemKey }
        ?: state.selectedItem
        ?: railItems.firstOrNull()

    val startupAuxFocusTarget = when (activeModeState) {
        is HomeModeContentState.Error -> retryActionRequester
        is HomeModeContentState.LoginRequired -> loginActionRequester
        else -> null
    }

    LaunchedEffect(startupContentFocusPending, activeModeState, startupAuxFocusTarget) {
        if (!startupContentFocusPending || startupAuxFocusTarget == null) {
            return@LaunchedEffect
        }
        if (activeModeState == HomeModeContentState.Loading) {
            return@LaunchedEffect
        }
        requestFocusWhenReady(startupAuxFocusTarget)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Color(0xFF14293B),
                    0.32f to Color(0xFF0B1520),
                    1f to BackgroundPrimary,
                ),
            ),
    ) {
        HomeBackdrop(item = focusedItem)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 48.dp, top = 12.dp, end = 48.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val headerPrimaryRequester = when {
                savedAppUpdate != null -> updateRequester
                state.availableModes.size > 1 -> modeToggleRequester
                else -> searchRequester
            }

            HomeHeader(
                selectedMode = state.selectedMode,
                availableModes = state.availableModes,
                onModeActivated = { mode ->
                    startupContentFocusPending = true
                    onModeSelected(mode)
                },
                onHeaderInteraction = { startupContentFocusPending = false },
                onBackToContent = {
                    if (headerDownTarget == null) {
                        false
                    } else {
                        focusScope.launch {
                            requestFocusWhenReady(headerDownTarget)
                        }
                        true
                    }
                },
                onSearchClick = onSearchClick,
                onSettingsClick = onSettingsClick,
                onUpdateClick = onUpdateClick,
                updateVersionText = savedAppUpdate?.latestVersion,
                modeToggleFocusRequester = if (state.availableModes.size > 1) modeToggleRequester else null,
                searchFocusRequester = searchRequester,
                updateFocusRequester = updateRequester,
                settingsFocusRequester = settingsRequester,
                downTarget = headerDownTarget,
            )

            if (state.showStaleBanner) {
                HomeStatusPanel(
                    tag = "home-stale-status",
                    text = "Данные показаны из кэша и могут быть устаревшими",
                    isError = false,
                )
            }

            if (isAllNewMode && state.fullScreenErrorMessage != null && state.items.isNotEmpty()) {
                HomeStatusPanel(
                    tag = "home-error-status",
                    text = state.fullScreenErrorMessage,
                    isError = true,
                    actionLabel = "Повторить",
                    onAction = onRetry,
                )
            }

            when {
                isAllNewMode && state.isInitialLoading && state.items.isEmpty() && state.fullScreenErrorMessage == null -> {
                    HomeLoadingSkeleton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                }
                isAllNewMode && state.items.isEmpty() && state.fullScreenErrorMessage != null -> {
                    HomeActionPanel(
                        message = state.fullScreenErrorMessage,
                        actionLabel = "Повторить",
                        onAction = onRetry,
                        modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 8.dp),
                    )
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                        .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        when (activeModeState) {
                            is HomeModeContentState.Content -> {
                                HomeRailSectionHeader(selectedMode = state.selectedMode)
                                HomeRail(
                                    railId = activeRailId,
                                    items = activeModeState.items,
                                    focusedItemKey = focusedItemKey,
                                    entryFocusRequester = contentEntryRequester,
                                    cardFocusRequesters = cardFocusRequesters,
                                    shouldRequestFocus = startupContentFocusPending,
                                    upTargetRequester = headerPrimaryRequester,
                                    downTargetRequester = null,
                                    isPaging = when (state.selectedMode) {
                                        HomeFeedMode.AllNew -> state.isPaging
                                        HomeFeedMode.Movies -> state.isMoviesPaging
                                        HomeFeedMode.Favorites -> false
                                        HomeFeedMode.Series -> state.isSeriesPaging
                                    },
                                    onItemFocused = { detailsUrl ->
                                        startupContentFocusPending = false
                                        focusedItemKey = detailsUrl
                                        onItemFocused(detailsUrl)
                                    },
                                    onOpenDetails = if (state.selectedMode == HomeFeedMode.Series) {
                                        onOpenSeriesOverview
                                    } else {
                                        onOpenDetails
                                    },
                                    onEndReached = if (
                                        state.selectedMode != HomeFeedMode.Favorites
                                    ) {
                                        onEndReached
                                    } else {
                                        {}
                                    },
                                )
                            }
                            HomeModeContentState.Empty -> {
                                HomeActionPanel(
                                    message = when (state.selectedMode) {
                                        HomeFeedMode.Favorites -> "Пока нет новых релизов в избранном"
                                        HomeFeedMode.Movies -> "Пока нет фильмов"
                                        HomeFeedMode.AllNew -> "Пока нет новых релизов"
                                        HomeFeedMode.Series -> "Пока нет сериалов"
                                    },
                                    modifier = Modifier.fillMaxWidth().weight(1f),
                                )
                            }
                            is HomeModeContentState.Error -> {
                                HomeActionPanel(
                                    message = activeModeState.message,
                                    actionLabel = "Повторить",
                                    actionFocusRequester = retryActionRequester,
                                    actionTestTag = "home-mode-retry-action",
                                    onAction = onRetry,
                                    modifier = Modifier.fillMaxWidth().weight(1f),
                                )
                            }
                            is HomeModeContentState.LoginRequired -> {
                                HomeActionPanel(
                                    message = activeModeState.message,
                                    actionLabel = "Войти",
                                    actionFocusRequester = loginActionRequester,
                                    actionTestTag = "home-mode-login-action",
                                    onAction = onAuthClick,
                                    modifier = Modifier.fillMaxWidth().weight(1f),
                                )
                            }
                            HomeModeContentState.Loading -> {
                                HomeLoadingSkeleton(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                )
                            }
                        }
                        val activePagingErrorMessage = when (state.selectedMode) {
                            HomeFeedMode.AllNew -> state.pagingErrorMessage
                            HomeFeedMode.Movies -> state.moviesPagingErrorMessage
                            HomeFeedMode.Favorites -> null
                            HomeFeedMode.Series -> state.seriesPagingErrorMessage
                        }
                        if (activePagingErrorMessage != null) {
                            HomeStatusPanel(
                                tag = "home-paging-status",
                                text = activePagingErrorMessage,
                                isError = true,
                                actionLabel = "Повторить",
                                onAction = onPagingRetry,
                            )
                        }
                        if (activeModeState is HomeModeContentState.Content) {
                            HomeBottomStage(
                                item = focusedItem,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeRailSectionHeader(selectedMode: HomeFeedMode) {
    val title = when (selectedMode) {
        HomeFeedMode.AllNew -> "Свежие релизы"
        HomeFeedMode.Favorites -> "Избранное"
        HomeFeedMode.Movies -> "Фильмы"
        HomeFeedMode.Series -> "Сериалы"
    }

    Text(
        text = title,
        color = HomeTextSecondary,
        fontSize = 15.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        modifier = Modifier.padding(start = 14.dp),
    )
}

@Composable
private fun HomeBackdrop(item: ReleaseSummary?) {
    val context = LocalContext.current
    val posterUrl = item?.posterUrl?.takeIf { it.isNotBlank() }

    Crossfade(
        targetState = posterUrl,
        label = "homeBackdrop",
    ) { targetPosterUrl ->
        if (targetPosterUrl != null) {
            val request = remember(context, targetPosterUrl) {
                ImageRequest.Builder(context)
                    .data(targetPosterUrl)
                    .crossfade(true)
                    .build()
            }
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(1_120.dp)
                        .blur(8.dp),
                )
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x88101620)),
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(
                    0f to BackgroundPrimary.copy(alpha = 0.96f),
                    0.32f to BackgroundPrimary.copy(alpha = 0.82f),
                    0.6f to BackgroundPrimary.copy(alpha = 0.22f),
                    1f to BackgroundPrimary.copy(alpha = 0.56f),
                ),
            ),
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Color(0x4414293B),
                    0.42f to Color.Transparent,
                    1f to BackgroundPrimary.copy(alpha = 0.9f),
                ),
            ),
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to BackgroundPrimary.copy(alpha = 0.82f),
                    0.18f to BackgroundPrimary.copy(alpha = 0.54f),
                    0.34f to Color.Transparent,
                ),
            ),
    )
}

@Composable
private fun HomeLoadingSkeleton(modifier: Modifier = Modifier) {
    val shimmerBrush = rememberHomeSkeletonBrush()

    Column(
        modifier = modifier
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            repeat(6) {
                HomeSkeletonBox(
                    brush = shimmerBrush,
                    modifier = Modifier.size(width = 176.dp, height = 264.dp),
                    shape = RoundedCornerShape(22.dp),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HomeSkeletonBox(
                brush = shimmerBrush,
                modifier = Modifier
                    .width(4.dp)
                    .height(78.dp),
                shape = RoundedCornerShape(2.dp),
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HomeSkeletonBox(
                    brush = shimmerBrush,
                    modifier = Modifier.size(width = 190.dp, height = 16.dp),
                    shape = RoundedCornerShape(8.dp),
                )
                HomeSkeletonBox(
                    brush = shimmerBrush,
                    modifier = Modifier.size(width = 360.dp, height = 34.dp),
                    shape = RoundedCornerShape(10.dp),
                )
                HomeSkeletonBox(
                    brush = shimmerBrush,
                    modifier = Modifier.size(width = 300.dp, height = 18.dp),
                    shape = RoundedCornerShape(9.dp),
                )
            }
        }
    }
}

@Composable
private fun rememberHomeSkeletonBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "homeSkeleton")
    val xOffset by transition.animateFloat(
        initialValue = -420f,
        targetValue = 980f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_350, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "homeSkeletonOffset",
    )
    return Brush.linearGradient(
        colors = listOf(
            HomePanelSurfaceStrong.copy(alpha = 0.54f),
            HomePanelBorder.copy(alpha = 0.46f),
            HomePanelSurfaceStrong.copy(alpha = 0.54f),
        ),
        start = Offset(xOffset, 0f),
        end = Offset(xOffset + 420f, 260f),
    )
}

@Composable
private fun HomeSkeletonBox(
    brush: Brush,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(brush),
    )
}

@Composable
private fun HomeStatusPanel(
    text: String,
    isError: Boolean,
    modifier: Modifier = Modifier,
    tag: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(24.dp)
    val background = if (isError) Color(0xCC2A0E10) else HomePanelSurfaceStrong
    val borderColor = if (isError) HomeStatusError.copy(alpha = 0.42f) else HomeAccentBlue.copy(alpha = 0.35f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .background(background, shape)
            .border(BorderStroke(1.5.dp, borderColor), shape)
            .padding(horizontal = 22.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = if (isError) TextPrimary else HomeTextSecondary,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null && onAction != null) {
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isError) HomeAccentGold else HomePanelSurface,
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isError) HomeAccentGold else HomePanelBorder,
                ),
            ) {
                Text(
                    text = actionLabel,
                    color = if (isError) Color(0xFF17120D) else TextPrimary,
                )
            }
        }
    }
}

@Composable
private fun HomeActionPanel(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    actionFocusRequester: FocusRequester? = null,
    actionTestTag: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .testTag("home-centered-panel")
                .background(HomePanelSurfaceStrong, shape)
                .border(BorderStroke(1.dp, HomePanelBorder), shape)
                .padding(horizontal = 28.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = message,
                color = TextPrimary,
                fontSize = 18.sp,
            )
            if (actionLabel != null && onAction != null) {
                Button(
                    onClick = onAction,
                    modifier = Modifier
                        .then(if (actionFocusRequester != null) Modifier.focusRequester(actionFocusRequester) else Modifier)
                        .then(if (actionTestTag != null) Modifier.testTag(actionTestTag) else Modifier),
                    colors = ButtonDefaults.buttonColors(containerColor = HomeAccentGold),
                ) {
                    Text(actionLabel, color = Color(0xFF17120D))
                }
            }
        }
    }
}

private fun demoHomeUiState(): HomeUiState {
    val demoItem = ReleaseSummary(
        id = "https://www.lostfilm.today/series/9-1-1/season_9/episode_13/",
        kind = ReleaseKind.SERIES,
        titleRu = "9-1-1",
        episodeTitleRu = "Маменькин сынок",
        seasonNumber = 9,
        episodeNumber = 13,
        releaseDateRu = "14.03.2026",
        posterUrl = "https://www.lostfilm.today/Static/Images/362/Posters/image_s9.jpg",
        detailsUrl = "https://www.lostfilm.today/series/9-1-1/season_9/episode_13/",
        pageNumber = 1,
        positionInPage = 0,
        fetchedAt = 0L,
    )

    return HomeUiState(
        items = listOf(demoItem),
        allNewModeState = HomeModeContentState.Content(listOf(demoItem)),
        selectedItem = demoItem,
        selectedItemKey = demoItem.detailsUrl,
        hasNextPage = true,
    )
}

internal suspend fun requestFocusWhenReady(requester: FocusRequester): Boolean {
    var focusMoved = false
    withTimeoutOrNull(1_000L) {
        while (!focusMoved) {
            withFrameNanos { }
            focusMoved = runCatching { requester.requestFocus() }.getOrDefault(false)
        }
    }
    return focusMoved
}
