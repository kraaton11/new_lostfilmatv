package com.kraat.lostfilmnewtv.ui.home
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.kraat.lostfilmnewtv.ui.theme.TextPrimary
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import com.kraat.lostfilmnewtv.data.parser.LostFilmBaseUrl

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
) {
    val focusState by if (focusStateFlow != null) {
        focusStateFlow.collectAsStateWithLifecycle()
    } else {
        remember { mutableStateOf(HomeFocusState(selectedItemKey = externalSelectedItemKey)) }
    }
    val resolvedExternalSelectedItemKey = focusState.selectedItemKey

    val lifecycleOwner = LocalLifecycleOwner.current
    val activeModeData = state.modeData(state.selectedMode)
    val activeModeState = activeModeData.contentState
    val isAllNewMode by remember(state.selectedMode) {
        derivedStateOf { state.selectedMode == HomeFeedMode.AllNew }
    }
    val activeItems = activeModeData.items
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
    var isSwitchingModeFromRail by remember { mutableStateOf(false) }
    var contentReturnFocusRequestVersion by remember { mutableStateOf(0) }
    var isContentRailFocused by remember(activeRailId) { mutableStateOf(false) }
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
                isSwitchingModeFromRail = false
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
    val settingsRequester = remember { FocusRequester() }
    val updateRequester = remember { FocusRequester() }
    val menuLabelsRequester = remember { FocusRequester() }
    val loginActionRequester = remember { FocusRequester() }
    val retryActionRequester = remember { FocusRequester() }
    val contentEntryRequester = remember { FocusRequester() }
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
        isSwitchingModeFromRail = true
        startupContentFocusPending = true
        contentReturnFocusRequestVersion += 1
        onModeSelected(modeSwitchOrder[nextIndex])
    }

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
                    0f to Color(0xFF07111B),
                    0.34f to Color(0xFF061018),
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

            BackHandler(enabled = isContentRailFocused) {
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
                updateFocusRequester = updateRequester,
                settingsFocusRequester = settingsRequester,
                menuLabelsFocusRequester = menuLabelsRequester,
                downTarget = headerDownTarget,
                showLabels = shouldShowHomeMenuLabels,
                menuLabelsEnabled = state.isHomeMenuLabelsEnabled,
                onHomeMenuLabelsVisibilitySelected = onHomeMenuLabelsVisibilitySelected,
                isVisualFocusEnabled = !isSwitchingModeFromRail,
                modifier = Modifier
                    .width(if (shouldShowHomeMenuLabels) 204.dp else 68.dp)
                    .fillMaxHeight(),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
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
                                val isCatalog = state.selectedMode == HomeFeedMode.Movies ||
                                    state.selectedMode == HomeFeedMode.Series
                                if (isCatalog) {
                                    CatalogInfoPanel(
                                        item = focusedItem,
                                        preview = releasePreview,
                                        isSeries = state.selectedMode == HomeFeedMode.Series,
                                    )
                                    Spacer(Modifier.weight(1f))
                                } else {
                                    HomeHeroStage(
                                        item = heroItem,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                HomeRailSectionHeader(
                                    selectedMode = state.selectedMode,
                                    favoriteSeriesCount = state.favoriteSeriesCount,
                                )
                                HomeRail(
                                    railId = activeRailId,
                                    items = activeModeState.items,
                                    modifier = if (isCatalog) Modifier.padding(bottom = 30.dp) else Modifier,
                                    focusedItemKey = focusedItemKey,
                                    entryFocusRequester = contentEntryRequester,
                                    cardFocusRequesters = cardFocusRequesters,
                                    shouldRequestFocus = startupContentFocusPending,
                                    returnFocusRequestVersion = contentReturnFocusRequestVersion,
                                    leftTargetRequester = headerPrimaryRequester,
                                    isPaging = activeModeData.isPaging,
                                    onItemFocused = { detailsUrl ->
                                        startupContentFocusPending = false
                                        isHomeMenuFocused = false
                                        isSwitchingModeFromRail = false
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
                                )
                            }
                            HomeModeContentState.Empty -> {
                                HomeActionPanel(
                                    message = when (state.selectedMode) {
                                        HomeFeedMode.Favorites -> "Пока нет новых релизов в избранном"
                                        HomeFeedMode.FavoriteSeries -> "Пока нет избранных сериалов"
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
                                    selectedMode = state.selectedMode,
                                )
                            }
                        }
                        val activePagingErrorMessage = activeModeData.pagingErrorMessage
                        if (activePagingErrorMessage != null) {
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

@Composable
private fun HomeHeroStage(
    item: ReleaseSummary?,
    modifier: Modifier = Modifier,
) {
    Crossfade(
        targetState = item,
        modifier = modifier
            .height(252.dp)
            .testTag("home-hero-stage"),
        label = "homeHeroStage",
    ) { targetItem ->
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
            ReleaseHeroMetaRow(targetItem)

            Text(
                text = targetItem?.titleRu.orEmpty(),
                color = TextPrimary,
                fontSize = 36.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Clip,
            )

            targetItem?.episodeTitleRu?.takeIf { it.isNotBlank() }?.let { episodeTitle ->
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

            targetItem.heroDescription().takeIf { it.isNotBlank() }?.let { description ->
                Text(
                    text = description,
                    color = HomeTextSecondary,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(760.dp),
                )
            }
        }
    }
}

@Composable
private fun ReleaseHeroMetaRow(item: ReleaseSummary?) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item?.availabilityLabel?.takeIf { it.isNotBlank() }?.let { label ->
            HeroMetaPill(label = label, highlighted = true)
        }
        seasonEpisodeHeroLabel(item)?.let { label ->
            HeroMetaPill(label = label, highlighted = true)
        }
        item?.releaseDateRu?.takeIf { it.isNotBlank() }?.let { date ->
            HeroMetaPill(label = date)
        }
        item?.kind?.let { kind ->
            HeroMetaPill(
                label = when (kind) {
                    ReleaseKind.SERIES -> "Сериал"
                    ReleaseKind.MOVIE -> "Фильм"
                },
            )
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

private fun seasonEpisodeHeroLabel(item: ReleaseSummary?): String? {
    if (item?.kind != ReleaseKind.SERIES) return null
    val season = item.seasonNumber?.let { "S${it.toString().padStart(2, '0')}" }
    val episode = item.episodeNumber?.let { "E${it.toString().padStart(2, '0')}" }
    return listOfNotNull(season, episode).joinToString("").takeIf { it.isNotBlank() }
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
) {
    val title = when (selectedMode) {
        HomeFeedMode.AllNew -> "Свежие релизы"
        HomeFeedMode.Favorites -> "Избранное"
        HomeFeedMode.FavoriteSeries -> "Мои сериалы"
        HomeFeedMode.Movies -> "Фильмы"
        HomeFeedMode.Series -> "Сериалы"
    }
    val countLabel = if (selectedMode == HomeFeedMode.Favorites) {
        favoriteSeriesCount?.let { "${it.formatFavoriteSeriesCount()} в избранном" }
    } else {
        null
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
                drawRect(Color(0x8A040A11))
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to BackgroundPrimary.copy(alpha = 1f),
                        0.34f to BackgroundPrimary.copy(alpha = 0.88f),
                        0.62f to BackgroundPrimary.copy(alpha = 0.18f),
                        1f to BackgroundPrimary.copy(alpha = 0.28f),
                    ),
                )
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color(0x33040A11),
                        0.38f to Color.Transparent,
                        1f to BackgroundPrimary.copy(alpha = 0.96f),
                    ),
                )
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to BackgroundPrimary.copy(alpha = 0.74f),
                        0.18f to BackgroundPrimary.copy(alpha = 0.48f),
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
) {
    val shimmerBrush = rememberShimmerSkeletonBrush(
        label = "homeSkeleton",
        baseColor = HomePanelSurfaceStrong,
        highlightColor = Color.White,
        baseAlpha = 0.72f,
        highlightAlpha = 0.10f,
        startOffset = -520f,
        endOffset = 1_160f,
        shimmerWidth = 440f,
        durationMillis = 1_300,
    )

    if (selectedMode == HomeFeedMode.Movies || selectedMode == HomeFeedMode.Series) {
        CatalogLoadingSkeleton(selectedMode, shimmerBrush, modifier)
        return
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(252.dp)
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(4) { index ->
                    ShimmerSkeletonBox(
                        brush = shimmerBrush,
                        modifier = Modifier.size(width = listOf(64.dp, 58.dp, 74.dp, 68.dp)[index], height = 23.dp),
                        shape = RoundedCornerShape(999.dp),
                        baseColor = HomePanelSurface.copy(alpha = 0.46f),
                        borderColor = HomePanelBorder.copy(alpha = 0.46f),
                    )
                }
            }
            ShimmerSkeletonBox(
                brush = shimmerBrush,
                modifier = Modifier.size(width = 650.dp, height = 40.dp),
                shape = RoundedCornerShape(10.dp),
            )
            ShimmerSkeletonBox(
                brush = shimmerBrush,
                modifier = Modifier.size(width = 410.dp, height = 22.dp),
                shape = RoundedCornerShape(8.dp),
            )
            repeat(5) { index ->
                ShimmerSkeletonBox(
                    brush = shimmerBrush,
                    modifier = Modifier.size(width = listOf(760.dp, 735.dp, 710.dp, 690.dp, 520.dp)[index], height = 16.dp),
                    shape = RoundedCornerShape(8.dp),
                )
            }
        }

        ShimmerSkeletonBox(
            brush = shimmerBrush,
            modifier = Modifier
                .padding(start = 2.dp)
                .size(width = 128.dp, height = 18.dp),
            shape = RoundedCornerShape(9.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 2.dp),
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
private fun CatalogLoadingSkeleton(mode: HomeFeedMode, brush: Brush, modifier: Modifier) {
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
        HomeRailSectionHeader(selectedMode = mode, favoriteSeriesCount = null)
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().padding(bottom = 30.dp),
            contentAlignment = Alignment.Center,
        ) {
            val cardWidth = homeCarouselCardWidth(maxWidth, maxHeight)
            Row(
                horizontalArrangement = Arrangement.spacedBy(cardWidth * -0.15f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(3) { index ->
                    ShimmerSkeletonBox(
                        brush = brush,
                        modifier = Modifier
                            .size(cardWidth, cardWidth * (9f / 16f))
                            .graphicsLayer {
                                scaleX = if (index == 1) 1f else 0.82f
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
            focusMoved = runCatching { requester.requestFocus(); true }.getOrDefault(false)
        }
    }
    return focusMoved
}
