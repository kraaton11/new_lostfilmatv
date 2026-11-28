package com.kraat.lostfilmnewtv.ui.home
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kraat.lostfilmnewtv.ui.components.resolveArtworkForDisplay
import com.kraat.lostfilmnewtv.data.model.CatalogSort
import com.kraat.lostfilmnewtv.data.model.ReleaseWatchFilter
import com.kraat.lostfilmnewtv.data.model.SeriesStatus
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import com.kraat.lostfilmnewtv.data.model.ReleaseSummary
import com.kraat.lostfilmnewtv.data.parser.extractYear
import com.kraat.lostfilmnewtv.updates.SavedAppUpdate
import com.kraat.lostfilmnewtv.ui.components.ShimmerSkeletonBox
import com.kraat.lostfilmnewtv.ui.components.rememberShimmerSkeletonBrush
import com.kraat.lostfilmnewtv.ui.theme.BackgroundPrimary
import com.kraat.lostfilmnewtv.ui.theme.HomeAccentBlue
import com.kraat.lostfilmnewtv.ui.theme.HomeAccentGold
import com.kraat.lostfilmnewtv.ui.theme.HomePanelBorder
import com.kraat.lostfilmnewtv.ui.theme.HomePanelSurface
import com.kraat.lostfilmnewtv.ui.theme.HomePanelSurfaceStrong
import com.kraat.lostfilmnewtv.ui.theme.HomeStatusError
import com.kraat.lostfilmnewtv.ui.theme.HomeTextSecondary
import com.kraat.lostfilmnewtv.ui.theme.LocalLostFilmColors
import com.kraat.lostfilmnewtv.ui.theme.TextPrimary
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import com.kraat.lostfilmnewtv.data.parser.LostFilmBaseUrl

private val ReleaseHeroHeight = 212.dp
private val FavoriteSeriesInfoHeight = 192.dp

@Composable
fun HomeScreen(
    state: HomeUiState = demoHomeUiState(),
    externalSelectedItemKey: String? = null,
    focusStateFlow: StateFlow<HomeFocusState>? = null,
    onItemFocused: (String) -> Unit = {},
    onModeSelected: (HomeFeedMode) -> Unit = {},
    onOpenDetails: (String) -> Unit = {},
    onOpenSeriesOverview: (String) -> Unit = onOpenDetails,
    onOpenSeriesGuide: (String) -> Unit = onOpenSeriesOverview,
    onEndReached: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onScheduleClick: () -> Unit = {},
    onHistoryClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onUpdateClick: () -> Unit = {},
    onHomeMenuLabelsVisibilitySelected: (Boolean) -> Unit = {},
    onAuthClick: () -> Unit = {},
    onRetry: () -> Unit = {},
    onPagingRetry: () -> Unit = {},
    onResume: () -> Unit = {},
    selectedNavItem: NavItem = NavItem.HOME,
    onNavItemSelected: (NavItem) -> Unit = {},
    onNavItemLongClick: (NavItem) -> Unit = {},
    savedAppUpdate: SavedAppUpdate? = null,
    isUpdateDownloading: Boolean = false,
    updateDownloadProgress: Int? = null,
    loadReleasePreview: suspend (String) -> HomeReleasePreview = { HomeReleasePreview() },
    onCatalogSortSelected: (CatalogSort) -> Unit = {},
    onReleaseFilterSelected: (ReleaseWatchFilter) -> Unit = {},
    onSeriesStatusSelected: (SeriesStatus) -> Unit = {},
) {
    val focusState by if (focusStateFlow != null) {
        focusStateFlow.collectAsStateWithLifecycle()
    } else {
        remember { mutableStateOf(HomeFocusState(selectedItemKey = externalSelectedItemKey)) }
    }
    val resolvedExternalSelectedItemKey = focusState.selectedItemKey

    val lifecycleOwner = LocalLifecycleOwner.current
    val activeModeData = state.modeData(state.selectedMode)
    val isSeriesCatalog = state.selectedMode == HomeFeedMode.Series
    val isCatalog = state.selectedMode == HomeFeedMode.Movies || isSeriesCatalog
    val isFavorites = state.selectedMode == HomeFeedMode.Favorites
    val hasCatalogSort = isCatalog || state.selectedMode == HomeFeedMode.FavoriteSeries
    val seriesStatus = activeModeData.seriesStatus
    val sortOptions = if (isSeriesCatalog && seriesStatus == SeriesStatus.Completed) {
        completedSeriesSortOptions
    } else {
        catalogSortOptions
    }
    val hasReleaseWatchFilter = isFavorites || state.selectedMode == HomeFeedMode.AllNew
    val releaseWatchFilter = if (isFavorites) state.favoriteReleaseFilter else state.newReleaseFilter
    val releaseFilterOptions = if (isFavorites) favoriteFilterOptions else newReleaseFilterOptions
    val isFilteredNewFeed = state.selectedMode == HomeFeedMode.AllNew && releaseWatchFilter != ReleaseWatchFilter.All
    val activeItems = if (isFilteredNewFeed) state.visibleNewItems else activeModeData.items
    val filteredPagingError = activeModeData.pagingErrorMessage.takeIf {
        isFilteredNewFeed && state.newFavoritesStatus == null && state.canPageNewFeed && activeItems.isEmpty()
    }
    val activeModeState = if (isFilteredNewFeed && state.newFavoritesStatus != null) {
        state.newFavoritesStatus
    } else if (isFilteredNewFeed && activeModeData.contentState is HomeModeContentState.Content) {
        when {
            activeItems.isNotEmpty() -> HomeModeContentState.Content(activeItems)
            filteredPagingError != null -> HomeModeContentState.Error(filteredPagingError)
            state.canPageNewFeed -> HomeModeContentState.Loading
            else -> HomeModeContentState.Empty
        }
    } else {
        activeModeData.contentState
    }
    val isEmptyRail = activeModeState == HomeModeContentState.Empty
    val isAllNewMode by remember(state.selectedMode) {
        derivedStateOf { state.selectedMode == HomeFeedMode.AllNew }
    }
    val modeSwitchOrder = remember(state.availableModes) {
        HomeFeedMode.entries.filter { it in state.availableModes }
    }
    val activeRailId = when (state.selectedMode) {
        HomeFeedMode.AllNew -> HOME_RAIL_ALL_NEW
        HomeFeedMode.Favorites -> HOME_RAIL_FAVORITES
        HomeFeedMode.FavoriteSeries -> HOME_RAIL_FAVORITE_SERIES
        HomeFeedMode.Movies -> HOME_RAIL_MOVIES
        HomeFeedMode.Series -> HOME_RAIL_SERIES
    }
    val itemKeys = remember(activeItems) { activeItems.map { it.detailsUrl } }
    var focusedItemKey by rememberSaveable(state.selectedMode, itemKeys) {
        mutableStateOf(
            resolvedExternalSelectedItemKey ?: state.selectedItemKey ?: itemKeys.firstOrNull(),
        )
    }
    var lastSyncedKey by remember { mutableStateOf<String?>(null) }
    var startupContentFocusPending by rememberSaveable { mutableStateOf(true) }
    var initialMenuFocusIgnored by remember { mutableStateOf(false) }
    var pendingRailMode by remember { mutableStateOf<HomeFeedMode?>(null) }
    val isSwitchingModeFromRail = pendingRailMode != null
    var contentReturnFocusRequestVersion by remember { mutableStateOf(0) }
    var isContentRailFocused by remember(activeRailId) { mutableStateOf(false) }
    var isFilterFocusPending by remember(activeRailId) { mutableStateOf(false) }
    var showSeriesStatuses by remember(activeRailId) { mutableStateOf(false) }
    var statusReturnRequester by remember(activeRailId) { mutableStateOf<FocusRequester?>(null) }
    var isHomeMenuFocused by rememberSaveable { mutableStateOf(true) }
    val shouldShowHomeMenuLabels = state.isHomeMenuLabelsEnabled || isHomeMenuFocused

    LaunchedEffect(resolvedExternalSelectedItemKey, state.selectedItemKey, state.selectedMode, itemKeys) {
        val preferredKey = resolvedExternalSelectedItemKey ?: state.selectedItemKey ?: itemKeys.firstOrNull()
        if (preferredKey != null && preferredKey != lastSyncedKey) {
            focusedItemKey = preferredKey
            lastSyncedKey = preferredKey
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                startupContentFocusPending = true
                initialMenuFocusIgnored = false
                pendingRailMode = null
                onResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val modeFocusRequesters = remember {
        HomeFeedMode.entries.associateWith { FocusRequester() }
    }
    val searchRequester = remember { FocusRequester() }
    val scheduleRequester = remember { FocusRequester() }
    val historyRequester = remember { FocusRequester() }
    val settingsRequester = remember { FocusRequester() }
    val updateRequester = remember { FocusRequester() }
    val menuLabelsRequester = remember { FocusRequester() }
    val loginActionRequester = remember { FocusRequester() }
    val retryActionRequester = remember { FocusRequester() }
    val contentEntryRequester = remember { FocusRequester() }
    val filterFocusRequester = remember { FocusRequester() }
    val cardFocusRequesters = remember(activeRailId) {
        linkedMapOf<String, FocusRequester>()
    }
    val focusScope = rememberCoroutineScope()
    val activeItemFocusKeys = remember(activeRailId, itemKeys) {
        itemKeys.map { detailsUrl -> homeItemKey(activeRailId, detailsUrl) }
    }
    val activeItemFocusKeySet = remember(activeItemFocusKeys) { activeItemFocusKeys.toSet() }
    cardFocusRequesters.keys.retainAll(activeItemFocusKeySet)
    activeItemFocusKeys.forEach { itemKey ->
        cardFocusRequesters.getOrPut(itemKey) { FocusRequester() }
    }
    val newestContentDetailsUrl = itemKeys.firstOrNull()
    val newestContentRequester = newestContentDetailsUrl?.let { detailsUrl ->
        cardFocusRequesters[homeItemKey(activeRailId, detailsUrl)]
    }
    val headerDownTarget = when (activeModeState) {
        is HomeModeContentState.Content -> newestContentRequester ?: contentEntryRequester
        is HomeModeContentState.Error -> retryActionRequester
        is HomeModeContentState.LoginRequired -> loginActionRequester
        HomeModeContentState.Empty -> filterFocusRequester.takeIf { isEmptyRail }
        else -> null
    }
    val railItems = (activeModeState as? HomeModeContentState.Content)?.items ?: activeItems
    val focusedItem = railItems.firstOrNull { it.detailsUrl == focusedItemKey }
        ?: state.selectedItem
        ?: railItems.firstOrNull()
    val focusedDetailsUrl = focusedItem?.detailsUrl
    val lostFilmHost by LostFilmBaseUrl.url.collectAsStateWithLifecycle()
    var releasePreview by remember(focusedDetailsUrl, lostFilmHost) { mutableStateOf(HomeReleasePreview()) }
    LaunchedEffect(focusedDetailsUrl, lostFilmHost) {
        if (focusedDetailsUrl != null) {
            delay(300)
            releasePreview = loadReleasePreview(focusedDetailsUrl)
        }
    }
    val heroItem = focusedItem?.let { item ->
        val overview = releasePreview.description
        when {
            overview == null -> item
            item.kind == ReleaseKind.MOVIE -> item.copy(movieOverviewRu = overview)
            else -> item.copy(episodeOverviewRu = overview)
        }
    }
    fun switchModeFromRail(offset: Int) {
        val currentIndex = modeSwitchOrder.indexOf(state.selectedMode)
        if (currentIndex < 0 || modeSwitchOrder.size < 2) return
        val nextIndex = (currentIndex + offset + modeSwitchOrder.size) % modeSwitchOrder.size
        val nextMode = modeSwitchOrder[nextIndex]
        pendingRailMode = nextMode
        onModeSelected(nextMode)
    }

    val startupAuxFocusTarget = when (activeModeState) {
        is HomeModeContentState.Error -> retryActionRequester
        is HomeModeContentState.LoginRequired -> loginActionRequester
        HomeModeContentState.Empty -> filterFocusRequester.takeIf { isEmptyRail }
        else -> null
    }

    val shouldFocusContent = if (isSwitchingModeFromRail) pendingRailMode == state.selectedMode else startupContentFocusPending
    LaunchedEffect(shouldFocusContent, activeModeState, startupAuxFocusTarget) {
        if (!shouldFocusContent || startupAuxFocusTarget == null) {
            return@LaunchedEffect
        }
        if (activeModeState == HomeModeContentState.Loading) {
            return@LaunchedEffect
        }
        if (requestFocusWhenReady(startupAuxFocusTarget)) {
            pendingRailMode = null
            startupContentFocusPending = false
            isFilterFocusPending = false
        }
    }

    fun focusFilterDuringReload() {
        isFilterFocusPending = true
        startupContentFocusPending = true
        filterFocusRequester.requestFocus()
        isHomeMenuFocused = false
        focusedItemKey = null
    }

    fun dismissSeriesStatuses() {
        showSeriesStatuses = false
        statusReturnRequester?.let { requester ->
            focusScope.launch { requestFocusWhenReady(requester) }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                val keyEvent = event.nativeKeyEvent
                val isInitialPress = keyEvent.action == KeyEvent.ACTION_DOWN && keyEvent.repeatCount == 0
                if (hasReleaseWatchFilter) {
                    val option = releaseFilterOptions.firstOrNull { it.keyCode == keyEvent.keyCode }
                        ?: return@onPreviewKeyEvent false
                    if (isInitialPress && option.filter != releaseWatchFilter) {
                        focusFilterDuringReload()
                        onReleaseFilterSelected(option.filter)
                    }
                    return@onPreviewKeyEvent true
                }
                if (!hasCatalogSort) return@onPreviewKeyEvent false
                if (isSeriesCatalog && keyEvent.keyCode == KeyEvent.KEYCODE_INFO) {
                    if (isInitialPress && !showSeriesStatuses) {
                        statusReturnRequester = if (isContentRailFocused) {
                            focusedItemKey?.let { cardFocusRequesters[homeItemKey(activeRailId, it)] }
                                ?: newestContentRequester
                        } else {
                            modeFocusRequesters[HomeFeedMode.Series]
                        }
                        showSeriesStatuses = true
                    }
                    return@onPreviewKeyEvent true
                }
                val statusStep = when (keyEvent.keyCode) {
                    KeyEvent.KEYCODE_CHANNEL_UP -> 1
                    KeyEvent.KEYCODE_CHANNEL_DOWN -> -1
                    else -> 0
                }
                if (isSeriesCatalog && statusStep != 0) {
                    if (isInitialPress && !showSeriesStatuses) {
                        val statuses = SeriesStatus.entries
                        val nextIndex = (seriesStatus.ordinal + statusStep + statuses.size) % statuses.size
                        focusFilterDuringReload()
                        onSeriesStatusSelected(statuses[nextIndex])
                    }
                    return@onPreviewKeyEvent true
                }
                val option = sortOptions.firstOrNull { it.keyCode == keyEvent.keyCode }
                    ?: return@onPreviewKeyEvent false
                if (!showSeriesStatuses && isInitialPress && option.sort != activeModeData.catalogSort) {
                    if (isContentRailFocused) focusFilterDuringReload()
                    focusedItemKey = null
                    onCatalogSortSelected(option.sort)
                }
                true
            }
            .background(
                Brush.verticalGradient(
                    0f to LocalLostFilmColors.current.homeBackgroundTop,
                    0.34f to LocalLostFilmColors.current.homeBackgroundMid,
                    1f to BackgroundPrimary,
                ),
            ),
    ) {
        HomeBackdrop(backdropPath = releasePreview.backdropUrl ?: focusedItem?.backdropUrl)

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 22.dp, top = 22.dp, end = 10.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            val headerPrimaryRequester = modeFocusRequesters.getValue(state.selectedMode)

            BackHandler(enabled = isContentRailFocused && !showSeriesStatuses) {
                focusScope.launch {
                    requestFocusWhenReady(headerPrimaryRequester)
                }
            }

            HomeHeader(
                selectedMode = state.selectedMode,
                availableModes = state.availableModes,
                onModeActivated = { mode ->
                    startupContentFocusPending = false
                    isHomeMenuFocused = true
                    onModeSelected(mode)
                    focusScope.launch {
                        withFrameNanos { }
                        requestFocusWhenReady(modeFocusRequesters.getValue(mode))
                    }
                },
                onHeaderInteraction = {
                    if (!isSwitchingModeFromRail) {
                        isFilterFocusPending = false
                        if (initialMenuFocusIgnored) {
                            startupContentFocusPending = false
                        } else {
                            initialMenuFocusIgnored = true
                        }
                        isHomeMenuFocused = true
                    }
                },
                onBackToContent = {
                    if (headerDownTarget == null) {
                        false
                    } else if (activeModeState is HomeModeContentState.Content) {
                        startupContentFocusPending = false
                        focusedItemKey = newestContentDetailsUrl
                        contentReturnFocusRequestVersion += 1
                        focusScope.launch {
                            requestFocusWhenReady(headerDownTarget)
                        }
                        true
                    } else {
                        focusScope.launch {
                            requestFocusWhenReady(headerDownTarget)
                        }
                        true
                    }
                },
                onSearchClick = onSearchClick,
                onScheduleClick = onScheduleClick,
                onHistoryClick = onHistoryClick,
                onSettingsClick = onSettingsClick,
                onUpdateClick = if (savedAppUpdate != null) onUpdateClick else onRetry,
                selectedNavItem = selectedNavItem,
                onNavItemSelected = onNavItemSelected,
                onNavItemLongClick = onNavItemLongClick,
                updateVersionText = savedAppUpdate?.latestVersion,
                isUpdateDownloading = isUpdateDownloading,
                updateDownloadProgress = updateDownloadProgress,
                modeFocusRequesters = modeFocusRequesters,
                searchFocusRequester = searchRequester,
                scheduleFocusRequester = scheduleRequester,
                historyFocusRequester = historyRequester,
                updateFocusRequester = updateRequester,
                settingsFocusRequester = settingsRequester,
                menuLabelsFocusRequester = menuLabelsRequester,
                downTarget = headerDownTarget,
                showLabels = shouldShowHomeMenuLabels,
                menuLabelsEnabled = state.isHomeMenuLabelsEnabled,
                onHomeMenuLabelsVisibilitySelected = onHomeMenuLabelsVisibilitySelected,
                isVisualFocusEnabled = !isSwitchingModeFromRail,
                modifier = Modifier
                    .width(if (shouldShowHomeMenuLabels) ExpandedMenuWidth else CollapsedMenuWidth)
                    .fillMaxHeight(),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (hasReleaseWatchFilter) {
                    ReleaseWatchFilterLegend(
                        selectedFilter = releaseWatchFilter,
                        options = releaseFilterOptions,
                        modifier = Modifier
                            .focusRequester(filterFocusRequester)
                            .focusProperties {
                                canFocus = isFilterFocusPending || isEmptyRail
                                left = headerPrimaryRequester
                            }
                            .focusable(),
                    )
                }
                if (hasCatalogSort) {
                    CatalogSortLegend(
                        options = sortOptions,
                        selectedSort = activeModeData.catalogSort,
                        showStatusHint = isSeriesCatalog,
                        modifier = Modifier
                            .focusRequester(filterFocusRequester)
                            .focusProperties {
                                canFocus = isFilterFocusPending || isEmptyRail
                                left = headerPrimaryRequester
                            }
                            .focusable(),
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
                            newReleaseFilter = state.newReleaseFilter,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        )
                    }
                    isAllNewMode && state.items.isEmpty() && state.fullScreenErrorMessage != null -> {
                        HomeActionPanel(
                            message = state.fullScreenErrorMessage,
                            actionLabel = "Повторить",
                            actionFocusRequester = retryActionRequester,
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
                                when {
                                    state.selectedMode == HomeFeedMode.FavoriteSeries -> {
                                        FavoriteSeriesInfoPanel(item = focusedItem, preview = releasePreview)
                                        Spacer(Modifier.weight(1f))
                                    }
                                    isCatalog -> {
                                        CatalogInfoPanel(
                                            item = focusedItem,
                                            preview = releasePreview,
                                            isSeries = isSeriesCatalog,
                                        )
                                        Spacer(Modifier.weight(1f))
                                    }
                                    else -> HomeHeroStage(
                                        item = heroItem,
                                        modifier = Modifier.fillMaxWidth(),
                                        releasePreview = releasePreview,
                                    )
                                }
                                HomeRailSectionHeader(
                                    selectedMode = state.selectedMode,
                                    favoriteSeriesCount = state.favoriteSeriesCount,
                                    seriesStatus = seriesStatus,
                                    newReleaseFilter = state.newReleaseFilter,
                                )
                                HomeRail(
                                    railId = activeRailId,
                                    items = activeModeState.items,
                                    modifier = if (isCatalog) Modifier.padding(bottom = 30.dp) else Modifier,
                                    focusedItemKey = focusedItemKey,
                                    entryFocusRequester = contentEntryRequester,
                                    cardFocusRequesters = cardFocusRequesters,
                                    shouldRequestFocus = shouldFocusContent,
                                    returnFocusRequestVersion = contentReturnFocusRequestVersion,
                                    leftTargetRequester = headerPrimaryRequester,
                                    isPaging = activeModeData.isPaging,
                                    onItemFocused = { detailsUrl ->
                                        isFilterFocusPending = false
                                        startupContentFocusPending = false
                                        isHomeMenuFocused = false
                                        if (pendingRailMode == state.selectedMode) pendingRailMode = null
                                        focusedItemKey = detailsUrl
                                        onItemFocused(detailsUrl)
                                    },
                                    onRailFocusChanged = { isFocused ->
                                        isContentRailFocused = isFocused
                                        if (isFocused) {
                                            isHomeMenuFocused = false
                                        }
                                    },
                                    onOpenDetails = if (state.selectedMode == HomeFeedMode.Series ||
                                        state.selectedMode == HomeFeedMode.FavoriteSeries
                                    ) {
                                        onOpenSeriesGuide
                                    } else {
                                        onOpenDetails
                                    },
                                    onMoveToPreviousMode = { switchModeFromRail(offset = -1) },
                                    onMoveToNextMode = { switchModeFromRail(offset = 1) },
                                    onEndReached = onEndReached,
                                    favoritePageToLoad = activeModeData.nextPage.takeIf {
                                        isFavorites && activeModeData.hasNextPage && !activeModeData.isPaging &&
                                            activeModeData.pagingErrorMessage == null
                                    },
                                )
                            }
                            HomeModeContentState.Empty -> {
                                HomeActionPanel(
                                    message = when (state.selectedMode) {
                                        HomeFeedMode.Favorites -> when (state.favoriteReleaseFilter) {
                                            ReleaseWatchFilter.All, ReleaseWatchFilter.Favorites -> "Пока нет новых релизов в избранном"
                                            ReleaseWatchFilter.Unwatched -> "Нет непросмотренных релизов"
                                            ReleaseWatchFilter.Watched -> "Нет просмотренных релизов"
                                        }
                                        HomeFeedMode.FavoriteSeries -> "Пока нет избранных сериалов"
                                        HomeFeedMode.Movies -> "Пока нет фильмов"
                                        HomeFeedMode.AllNew -> when (releaseWatchFilter) {
                                            ReleaseWatchFilter.All -> "Пока нет новых релизов"
                                            ReleaseWatchFilter.Favorites ->
                                                "За последние ${NEW_FAVORITES_WINDOW_DAYS} дней новых эпизодов в избранном нет"
                                            ReleaseWatchFilter.Unwatched -> "Нет непросмотренных релизов"
                                            ReleaseWatchFilter.Watched -> "Нет просмотренных релизов"
                                        }
                                        HomeFeedMode.Series -> if (seriesStatus == SeriesStatus.Favorites) {
                                            "Пока нет избранных сериалов"
                                        } else {
                                            "Пока нет сериалов"
                                        }
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
                                    onAction = if (filteredPagingError != null) onPagingRetry else onRetry,
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
                                    selectedMode = state.selectedMode,
                                    seriesStatus = seriesStatus,
                                    favoriteSeriesCount = state.favoriteSeriesCount,
                                    newReleaseFilter = state.newReleaseFilter,
                                )
                            }
                        }
                        val activePagingErrorMessage = activeModeData.pagingErrorMessage
                        if (activePagingErrorMessage != null && filteredPagingError == null) {
                            HomeStatusPanel(
                                tag = "home-paging-status",
                                text = activePagingErrorMessage,
                                isError = true,
                                actionLabel = "Повторить",
                                onAction = onPagingRetry,
                            )
                        }
                    }
                }
            }
            }
        }
        if (showSeriesStatuses) {
            BackHandler { dismissSeriesStatuses() }
            SeriesStatusPicker(
                selectedStatus = seriesStatus,
                onSelected = { status ->
                    if (status == seriesStatus) {
                        dismissSeriesStatuses()
                    } else {
                        focusFilterDuringReload()
                        showSeriesStatuses = false
                        onSeriesStatusSelected(status)
                    }
                },
            )
        }
    }
}

private data class CatalogSortOption(
    val sort: CatalogSort,
    val keyCode: Int,
    val color: Color,
    val label: String,
)

private val catalogSortOptions = listOf(
    CatalogSortOption(CatalogSort.Added, KeyEvent.KEYCODE_PROG_RED, Color(0xFFFF636B), "По добавлению"),
    CatalogSortOption(CatalogSort.Newest, KeyEvent.KEYCODE_PROG_GREEN, Color(0xFF69DD93), "По новизне"),
    CatalogSortOption(CatalogSort.Alphabetical, KeyEvent.KEYCODE_PROG_YELLOW, Color(0xFFFFD46E), "По алфавиту"),
    CatalogSortOption(CatalogSort.Rating, KeyEvent.KEYCODE_PROG_BLUE, Color(0xFF71B2FF), "По рейтингу"),
)

private val completedSeriesSortOptions = catalogSortOptions.map { option ->
    if (option.sort == CatalogSort.Alphabetical) {
        option.copy(sort = CatalogSort.CompletionDate, label = "По дате завершения")
    } else {
        option
    }
}

private val SeriesStatus.label: String
    get() = when (this) {
        SeriesStatus.All -> "Все"
        SeriesStatus.New -> "Новые"
        SeriesStatus.Ongoing -> "Снимающиеся"
        SeriesStatus.Completed -> "Завершённые"
        SeriesStatus.Favorites -> "Избранные"
    }

@Composable
private fun SeriesStatusPicker(selectedStatus: SeriesStatus, onSelected: (SeriesStatus) -> Unit) {
    val selectedRequester = remember { FocusRequester() }
    LaunchedEffect(selectedStatus) { requestFocusWhenReady(selectedRequester) }
    Box(
        modifier = Modifier.fillMaxSize().background(LocalLostFilmColors.current.scrim.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(300.dp)
                .background(HomePanelSurfaceStrong, RoundedCornerShape(12.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Статус сериала", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            SeriesStatus.entries.forEach { status ->
                var focused by remember { mutableStateOf(false) }
                Button(
                    onClick = { onSelected(status) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(2.dp, if (focused) TextPrimary else Color.Transparent, RoundedCornerShape(24.dp))
                        .onFocusChanged { focused = it.isFocused }
                        .then(if (status == selectedStatus) Modifier.focusRequester(selectedRequester) else Modifier)
                        .focusProperties {
                            left = FocusRequester.Cancel
                            right = FocusRequester.Cancel
                            if (status == SeriesStatus.All) up = FocusRequester.Cancel
                            if (status == SeriesStatus.entries.last()) down = FocusRequester.Cancel
                        },
                ) {
                    Text(if (status == selectedStatus) "${status.label} ✓" else status.label)
                }
            }
        }
    }
}

@Composable
private fun CatalogSortLegend(
    options: List<CatalogSortOption>,
    selectedSort: CatalogSort,
    showStatusHint: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { option ->
            RemoteModeLabel(option.label, option.color, selected = option.sort == selectedSort)
        }
        if (showStatusHint) {
            Text(
                text = "CH+/CH- Статус",
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                color = TextPrimary,
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
    }
}

private data class FavoriteFilterOption(
    val filter: ReleaseWatchFilter,
    val keyCode: Int,
    val color: Color,
    val label: String,
)

private val favoriteFilterOptions = listOf(
    FavoriteFilterOption(ReleaseWatchFilter.All, KeyEvent.KEYCODE_PROG_RED, Color(0xFFFF636B), "Все"),
    FavoriteFilterOption(ReleaseWatchFilter.Unwatched, KeyEvent.KEYCODE_PROG_GREEN, Color(0xFF69DD93), "Непросмотренные"),
    FavoriteFilterOption(ReleaseWatchFilter.Watched, KeyEvent.KEYCODE_PROG_YELLOW, Color(0xFFFFD46E), "Просмотренные"),
)

private val newReleaseFilterOptions = favoriteFilterOptions +
    FavoriteFilterOption(ReleaseWatchFilter.Favorites, KeyEvent.KEYCODE_PROG_BLUE, Color(0xFF71B2FF), "Избранное")

@Composable
private fun ReleaseWatchFilterLegend(
    selectedFilter: ReleaseWatchFilter,
    options: List<FavoriteFilterOption>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { option ->
            RemoteModeLabel(option.label, option.color, selected = option.filter == selectedFilter)
        }
    }
}

@Composable
private fun RemoteModeLabel(label: String, color: Color, selected: Boolean) {
    Row(
        modifier = Modifier
            .border(1.dp, if (selected) color else Color.Transparent, RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).background(color, RoundedCornerShape(50)))
        Text(
            text = if (selected) "$label ✓" else label,
            color = if (selected) TextPrimary else HomeTextSecondary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

@Composable
private fun CatalogInfoPanel(item: ReleaseSummary?, preview: HomeReleasePreview, isSeries: Boolean) {
    val series = preview.seriesOverview.takeIf { isSeries }
    val metadata = preview.movieMetadata.takeUnless { isSeries }
    val facts = listOfNotNull(
        series?.genresRu?.takeIf { it.isNotBlank() }
            ?: metadata?.genres?.joinToString(", ")?.takeIf { it.isNotBlank() },
        metadata?.durationMinutes?.let { "$it мин" },
        (series?.imdbRating ?: metadata?.imdbRating)?.let { "IMDb $it" },
    ).joinToString("  ·  ")
    val year = if (isSeries) series?.premiereDateRu?.extractYear() else preview.movieYear
    val title = listOfNotNull(item?.titleRu, (year ?: item?.originalReleaseYear)?.toString())
        .joinToString("  ·  ")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(214.dp)
            .testTag(if (isSeries) "home-series-info" else "home-movie-info")
            .padding(start = 12.dp, top = 4.dp, end = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 27.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (facts.isNotEmpty()) {
            Text(
                text = facts,
                color = HomeAccentGold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val summaryDescription = if (isSeries) item?.seriesOverviewRu else item?.movieOverviewRu
        (preview.description ?: summaryDescription)?.takeIf { it.isNotBlank() }?.let { description ->
            Text(
                text = description,
                color = HomeTextSecondary,
                fontSize = 16.sp,
                lineHeight = 21.sp,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(680.dp),
            )
        }
        val status = item?.availabilityLabel?.takeIf { it.isNotBlank() } ?: series?.statusRu
        status?.takeIf { it.isNotBlank() }?.let { HeroMetaPill(it, highlighted = true) }
    }
}

internal fun Modifier.loopingTitle(): Modifier = basicMarquee(
    iterations = Int.MAX_VALUE,
    initialDelayMillis = 2_000,
    repeatDelayMillis = 0,
    spacing = MarqueeSpacing(32.dp),
)

@Composable
private fun FavoriteSeriesInfoPanel(item: ReleaseSummary?, preview: HomeReleasePreview) {
    val series = preview.seriesOverview
    val year = series?.premiereDateRu?.extractYear() ?: item?.originalReleaseYear
    val title = listOfNotNull(item?.titleRu, year?.toString()).joinToString("  ·  ")
    val status = series?.statusRu?.takeIf { it.isNotBlank() } ?: item?.availabilityLabel
    val rating = series?.imdbRating?.takeIf { (it.toDoubleOrNull() ?: 0.0) > 0.0 }?.let { "IMDb $it" }
        ?: series?.tmdbRating?.takeIf { (it.toDoubleOrNull() ?: 0.0) > 0.0 }?.let { "TMDB $it" }
    val facts = listOfNotNull(series?.genresRu, series?.channelCountryRu, rating)
        .filter { it.isNotBlank() }
        .joinToString("  ·  ")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(FavoriteSeriesInfoHeight)
            .padding(start = 12.dp, top = 4.dp, end = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            key(item?.detailsUrl, title) {
                Text(
                    text = title,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .loopingTitle(),
                    color = TextPrimary,
                    fontSize = 27.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
            status?.takeIf { it.isNotBlank() }?.let { HeroMetaPill(it, highlighted = true) }
        }
        if (facts.isNotEmpty()) {
            Text(facts, color = HomeAccentGold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        (preview.description ?: item?.seriesOverviewRu)?.takeIf { it.isNotBlank() }?.let { description ->
            Text(
                text = description,
                modifier = Modifier.width(680.dp),
                color = HomeTextSecondary,
                fontSize = 16.sp,
                lineHeight = 21.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HomeHeroStage(
    item: ReleaseSummary?,
    releasePreview: HomeReleasePreview,
    modifier: Modifier = Modifier,
) {
    Crossfade(
        targetState = item,
        modifier = modifier
            .height(ReleaseHeroHeight)
            .testTag("home-hero-stage"),
        label = "homeHeroStage",
    ) { targetItem ->
        val preview = releasePreview.takeIf { targetItem?.detailsUrl == item?.detailsUrl }
        val movieYear = if (targetItem?.kind == ReleaseKind.MOVIE) {
            preview?.movieYear ?: targetItem.originalReleaseYear
        } else {
            null
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to BackgroundPrimary.copy(alpha = 0.68f),
                        0.54f to BackgroundPrimary.copy(alpha = 0.22f),
                        1f to Color.Transparent,
                    ),
                )
                .padding(start = 2.dp, top = 4.dp, end = 280.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            key(targetItem?.detailsUrl) {
                Text(
                    modifier = Modifier.loopingTitle(),
                    text = listOfNotNull(targetItem?.titleRu, movieYear?.toString()).joinToString("  ·  "),
                    color = TextPrimary,
                    fontSize = 30.sp,
                    lineHeight = 40.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }


            val episodeTitle = targetItem
                ?.takeIf { it.kind == ReleaseKind.SERIES }
                ?.episodeTitleRu
            episodeTitle?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = episodeTitle,
                    style = TextStyle(
                        platformStyle = PlatformTextStyle(includeFontPadding = true),
                    ),
                    color = HomeAccentGold,
                    fontSize = 18.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (targetItem != null) ReleaseHeroDetails(targetItem, preview)

            targetItem.heroDescription().takeIf { it.isNotBlank() }?.let { description ->
                Text(
                    text = description,
                    color = HomeTextSecondary,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(760.dp),
                )
            }
        }
    }
}

@Composable
private fun ReleaseHeroDetails(item: ReleaseSummary, preview: HomeReleasePreview?) {
    val movie = preview?.movieMetadata
    val facts = if (item.kind == ReleaseKind.SERIES) {
        listOfNotNull(
            item.seasonNumber?.let { "Сезон $it" },
            item.episodeNumber?.let { "Серия $it" },
            item.releaseDateRu.takeIf { it.isNotBlank() },
        )
    } else {
        listOfNotNull(
            movie?.genres?.joinToString(", ")?.takeIf { it.isNotBlank() },
            movie?.durationMinutes?.let { "$it мин" },
            movie?.imdbRating?.takeIf { (it.toDoubleOrNull() ?: 0.0) > 0.0 }?.let { "IMDb $it" },
            item.releaseDateRu.takeIf { it.isNotBlank() },
        )
    }
    if (facts.isNotEmpty() || item.isWatched || !item.availabilityLabel.isNullOrBlank()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            item.availabilityLabel?.takeIf { it.isNotBlank() }?.let { HeroMetaPill(it) }
            if (item.isWatched) HeroMetaPill("Просмотрено")
            if (facts.isNotEmpty()) {
                Text(
                    text = facts.joinToString("  ·  "),
                    color = HomeTextSecondary,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun HeroMetaPill(
    label: String,
    highlighted: Boolean = false,
) {
    val shape = RoundedCornerShape(999.dp)
    Text(
        text = label,
        color = TextPrimary,
        fontSize = 11.sp,
        lineHeight = 13.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .background(
                color = HomePanelSurface.copy(alpha = 0.46f),
                shape = shape,
            )
            .border(
                width = 1.dp,
                color = if (highlighted) HomeAccentGold.copy(alpha = 0.62f) else HomePanelBorder.copy(alpha = 0.46f),
                shape = shape,
            )
            .padding(horizontal = 11.dp, vertical = 4.dp),
    )
}

private fun ReleaseSummary?.heroDescription(): String {
    val item = this ?: return ""
    return when (item.kind) {
        ReleaseKind.SERIES -> item.episodeOverviewRu
            ?.takeIf { it.isNotBlank() }
            ?: item.seriesOverviewRu?.takeIf { it.isNotBlank() }.orEmpty()
        ReleaseKind.MOVIE -> item.movieOverviewRu.orEmpty()
    }
}

@Composable
private fun HomeRailSectionHeader(
    selectedMode: HomeFeedMode,
    favoriteSeriesCount: Int?,
    seriesStatus: SeriesStatus = SeriesStatus.All,
    newReleaseFilter: ReleaseWatchFilter = ReleaseWatchFilter.All,
) {
    val title = when (selectedMode) {
        HomeFeedMode.AllNew -> if (newReleaseFilter == ReleaseWatchFilter.Favorites) {
            "Новинки в избранном за ${NEW_FAVORITES_WINDOW_DAYS} дней"
        } else {
            "Новинки"
        }
        HomeFeedMode.Favorites -> "Избранное"
        HomeFeedMode.FavoriteSeries -> "Мои сериалы"
        HomeFeedMode.Movies -> "Фильмы"
        HomeFeedMode.Series -> "Сериалы · ${seriesStatus.label}"
    }
    val countLabel = when (selectedMode) {
        HomeFeedMode.Favorites -> favoriteSeriesCount?.let { "${it.formatFavoriteSeriesCount()} в избранном" }
        HomeFeedMode.FavoriteSeries -> favoriteSeriesCount?.formatFavoriteSeriesCount()
        else -> null
    }

    Row(
        modifier = Modifier.padding(start = 2.dp).testTag("home-rail-section-header"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        )
        if (countLabel != null) {
            Text(
                text = countLabel,
                color = HomeTextSecondary,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun Int.formatFavoriteSeriesCount(): String {
    val absCount = kotlin.math.abs(this)
    val lastTwoDigits = absCount % 100
    val lastDigit = absCount % 10
    val suffix = when {
        lastTwoDigits in 11..14 -> "сериалов"
        lastDigit == 1 -> "сериал"
        lastDigit in 2..4 -> "сериала"
        else -> "сериалов"
    }
    return "$this $suffix"
}

@Composable
private fun HomeBackdrop(backdropPath: String?) {
    val scrim = LocalLostFilmColors.current.scrim
    val context = LocalContext.current
    val backdropUrl = resolveArtworkForDisplay(backdropPath)

    Crossfade(
        targetState = backdropUrl,
        label = "homeBackdrop",
    ) { targetUrl ->
        if (targetUrl != null) {
            val request = remember(context, targetUrl) {
                ImageRequest.Builder(context)
                    .data(targetUrl)
                    .size(1280, 720)
                    .crossfade(true)
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().testTag("home-backdrop"),
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(scrim.copy(alpha = 0.5412f))
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to scrim.copy(alpha = 1f),
                        0.34f to scrim.copy(alpha = 0.88f),
                        0.62f to scrim.copy(alpha = 0.18f),
                        1f to scrim.copy(alpha = 0.28f),
                    ),
                )
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to scrim.copy(alpha = 0.2f),
                        0.38f to Color.Transparent,
                        1f to scrim.copy(alpha = 0.96f),
                    ),
                )
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to scrim.copy(alpha = 0.74f),
                        0.18f to scrim.copy(alpha = 0.48f),
                        0.34f to Color.Transparent,
                    ),
                )
            },
    )
}

@Composable
private fun HomeLoadingSkeleton(
    modifier: Modifier = Modifier,
    selectedMode: HomeFeedMode = HomeFeedMode.AllNew,
    seriesStatus: SeriesStatus = SeriesStatus.All,
    favoriteSeriesCount: Int? = null,
    newReleaseFilter: ReleaseWatchFilter = ReleaseWatchFilter.All,
) {
    val shimmerBrush = rememberShimmerSkeletonBrush(
        label = "homeSkeleton",
        baseColor = HomePanelSurfaceStrong,
        highlightColor = LocalLostFilmColors.current.highlight,
        baseAlpha = 0.72f,
        highlightAlpha = 0.10f,
        startOffset = -520f,
        endOffset = 1_160f,
        shimmerWidth = 440f,
        durationMillis = 1_300,
    )

    if (selectedMode == HomeFeedMode.Movies || selectedMode == HomeFeedMode.Series) {
        CatalogLoadingSkeleton(selectedMode, seriesStatus, shimmerBrush, modifier)
        return
    }

    val isFavoriteSeries = selectedMode == HomeFeedMode.FavoriteSeries
    Column(modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        if (isFavoriteSeries) {
            FavoriteSeriesInfoSkeleton(shimmerBrush)
            Spacer(Modifier.weight(1f))
        } else {
            ReleaseHeroSkeleton(shimmerBrush)
        }

        HomeRailSectionHeader(selectedMode, favoriteSeriesCount, newReleaseFilter = newReleaseFilter)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(218.dp)
                .padding(start = 12.dp, top = 12.dp, end = 10.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(8) {
                ShimmerSkeletonBox(
                    brush = shimmerBrush,
                    modifier = Modifier.size(width = 112.dp, height = 172.dp),
                    shape = RoundedCornerShape(12.dp),
                    baseColor = HomePanelSurfaceStrong,
                    borderColor = HomePanelBorder.copy(alpha = 0.14f),
                )
            }
        }
    }
}

@Composable
private fun ReleaseHeroSkeleton(brush: Brush) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(ReleaseHeroHeight)
            .padding(start = 2.dp, top = 4.dp, end = 280.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        ShimmerSkeletonBox(brush, Modifier.size(540.dp, 40.dp), RoundedCornerShape(10.dp))
        ShimmerSkeletonBox(brush, Modifier.size(240.dp, 19.dp), RoundedCornerShape(8.dp))
        ShimmerSkeletonBox(brush, Modifier.size(330.dp, 22.dp), RoundedCornerShape(8.dp))
        listOf(680.dp, 660.dp, 480.dp).forEach { width ->
            ShimmerSkeletonBox(brush, Modifier.size(width, 13.dp), RoundedCornerShape(8.dp))
        }
    }
}

@Composable
private fun FavoriteSeriesInfoSkeleton(brush: Brush) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(FavoriteSeriesInfoHeight)
            .padding(start = 12.dp, top = 4.dp, end = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShimmerSkeletonBox(brush, Modifier.size(300.dp, 32.dp), RoundedCornerShape(10.dp))
            ShimmerSkeletonBox(brush, Modifier.size(86.dp, 21.dp), RoundedCornerShape(999.dp))
        }
        ShimmerSkeletonBox(brush, Modifier.size(410.dp, 19.dp), RoundedCornerShape(8.dp))
        ShimmerSkeletonBox(brush, Modifier.size(590.dp, 13.dp), RoundedCornerShape(8.dp))
        ShimmerSkeletonBox(brush, Modifier.size(470.dp, 13.dp), RoundedCornerShape(8.dp))
    }
}

@Composable
private fun CatalogLoadingSkeleton(
    mode: HomeFeedMode,
    seriesStatus: SeriesStatus,
    brush: Brush,
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(214.dp)
                .padding(start = 12.dp, top = 4.dp, end = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ShimmerSkeletonBox(brush, Modifier.size(420.dp, 30.dp), RoundedCornerShape(10.dp))
            ShimmerSkeletonBox(brush, Modifier.size(250.dp, 18.dp), RoundedCornerShape(8.dp))
            repeat(4) { index ->
                ShimmerSkeletonBox(
                    brush,
                    Modifier.size(width = if (index == 3) 440.dp else 680.dp, height = 13.dp),
                    RoundedCornerShape(8.dp),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        HomeRailSectionHeader(selectedMode = mode, favoriteSeriesCount = null, seriesStatus = seriesStatus)
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().padding(bottom = 30.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            val cardWidth = homeCarouselCardWidth(maxWidth, maxHeight)
            Row(
                modifier = Modifier.padding(start = HOME_RAIL_HORIZONTAL_PADDING),
                horizontalArrangement = Arrangement.spacedBy(cardWidth * -0.15f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(3) { index ->
                    ShimmerSkeletonBox(
                        brush = brush,
                        modifier = Modifier
                            .size(cardWidth, cardWidth * (9f / 16f))
                            .graphicsLayer {
                                scaleX = if (index == 0) 1f else 0.82f
                                scaleY = scaleX
                            },
                        shape = RoundedCornerShape(14.dp),
                    )
                }
            }
        }
    }
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
    val background = if (isError) LocalLostFilmColors.current.errorSurface else HomePanelSurfaceStrong
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
                    color = if (isError) LocalLostFilmColors.current.onAccent else TextPrimary,
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
                    Text(actionLabel, color = LocalLostFilmColors.current.onAccent)
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
            focusMoved = runCatching { requester.requestFocus(FocusDirection.Enter) }.getOrDefault(false)
        }
    }
    return focusMoved
}
