package com.kraat.lostfilmnewtv.ui.home
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import com.kraat.lostfilmnewtv.data.model.ReleaseSummary
import com.kraat.lostfilmnewtv.data.model.TmdbEpisodeOverviewSource
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
import com.kraat.lostfilmnewtv.ui.theme.HomeTextMuted
import com.kraat.lostfilmnewtv.ui.theme.HomeTextSecondary
import com.kraat.lostfilmnewtv.ui.theme.TextPrimary
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun HomeScreen(
    state: HomeUiState = demoHomeUiState(),
    externalSelectedItemKey: String? = null,
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
    isAuthenticated: Boolean = false,
    savedAppUpdate: SavedAppUpdate? = null,
    isUpdateDownloading: Boolean = false,
    updateDownloadProgress: Int? = null,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val activeModeStateState = remember(
        state.selectedMode,
        state.allNewModeState,
        state.favoritesModeState,
        state.favoriteSeriesModeState,
        state.moviesModeState,
        state.seriesModeState,
    ) {
        derivedStateOf {
            when (state.selectedMode) {
                HomeFeedMode.AllNew -> state.allNewModeState
                HomeFeedMode.Favorites -> state.favoritesModeState
                HomeFeedMode.FavoriteSeries -> state.favoriteSeriesModeState
                HomeFeedMode.Movies -> state.moviesModeState
                HomeFeedMode.Series -> state.seriesModeState
            }
        }
    }
    val activeModeState = activeModeStateState.value
    val isAllNewMode by remember(state.selectedMode) {
        derivedStateOf { state.selectedMode == HomeFeedMode.AllNew }
    }
    val activeItems = remember(
        state.selectedMode,
        state.allNewModeState,
        state.favoritesModeState,
        state.favoriteSeriesModeState,
        state.moviesModeState,
        state.seriesModeState,
    ) {
        state.itemsForMode(state.selectedMode)
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
            externalSelectedItemKey ?: state.selectedItemKey ?: itemKeys.firstOrNull(),
        )
    }
    var lastSyncedKey by remember { mutableStateOf<String?>(null) }
    var startupContentFocusPending by rememberSaveable { mutableStateOf(true) }
    var contentReturnFocusRequestVersion by remember { mutableStateOf(0) }
    var isContentRailFocused by remember(activeRailId) { mutableStateOf(false) }
    var isHomeMenuFocused by rememberSaveable { mutableStateOf(true) }
    val shouldShowHomeMenuLabels = state.isHomeMenuLabelsEnabled || isHomeMenuFocused

    LaunchedEffect(externalSelectedItemKey, state.selectedItemKey, state.selectedMode, itemKeys) {
        val preferredKey = externalSelectedItemKey ?: state.selectedItemKey ?: itemKeys.firstOrNull()
        if (preferredKey != null && preferredKey != lastSyncedKey) {
            focusedItemKey = preferredKey
            lastSyncedKey = preferredKey
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                startupContentFocusPending = true
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
    fun switchModeFromRail(offset: Int) {
        val currentIndex = modeSwitchOrder.indexOf(state.selectedMode)
        if (currentIndex < 0 || modeSwitchOrder.size < 2) return
        val nextIndex = (currentIndex + offset + modeSwitchOrder.size) % modeSwitchOrder.size
        startupContentFocusPending = true
        contentReturnFocusRequestVersion += 1
        contentEntryRequester.requestFocus()
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
        HomeBackdrop(item = focusedItem)

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
                    startupContentFocusPending = false
                    isHomeMenuFocused = true
                },
                onBackToContent = {
                    if (headerDownTarget == null) {
                        false
                    } else if (activeModeState is HomeModeContentState.Content) {
                        startupContentFocusPending = false
                        focusedItemKey = newestContentDetailsUrl
                        contentReturnFocusRequestVersion += 1
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
                                HomeHeroStage(
                                    item = focusedItem,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                HomeRailSectionHeader(
                                    selectedMode = state.selectedMode,
                                    favoriteSeriesCount = state.favoriteSeriesCount,
                                )
                                HomeRail(
                                    railId = activeRailId,
                                    items = activeModeState.items,
                                    modifier = Modifier
                                        .fillMaxWidth(),
                                    focusedItemKey = focusedItemKey,
                                    entryFocusRequester = contentEntryRequester,
                                    cardFocusRequesters = cardFocusRequesters,
                                    shouldRequestFocus = startupContentFocusPending,
                                    returnFocusRequestVersion = contentReturnFocusRequestVersion,
                                    upTargetRequester = headerPrimaryRequester,
                                    leftTargetRequester = headerPrimaryRequester,
                                    downTargetRequester = null,
                                    isPaging = when (state.selectedMode) {
                                        HomeFeedMode.AllNew -> state.isPaging
                                        HomeFeedMode.Movies -> state.isMoviesPaging
                                        HomeFeedMode.Favorites -> state.isFavoritesPaging
                                        HomeFeedMode.FavoriteSeries -> false
                                        HomeFeedMode.Series -> state.isSeriesPaging
                                    },
                                    onItemFocused = { detailsUrl ->
                                        startupContentFocusPending = false
                                        isHomeMenuFocused = false
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
                                )
                            }
                        }
                        val activePagingErrorMessage = when (state.selectedMode) {
                            HomeFeedMode.AllNew -> state.pagingErrorMessage
                            HomeFeedMode.Movies -> state.moviesPagingErrorMessage
                            HomeFeedMode.Favorites -> state.favoritesPagingErrorMessage
                            HomeFeedMode.FavoriteSeries -> null
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
                    }
                }
            }
            }
        }
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
        item?.tmdbRating?.takeIf { it.isNotBlank() }?.let { rating ->
            HeroMetaPill(label = "TMDB $rating")
        }
        item.episodeOverviewSourceLabel()?.let { label ->
            HeroMetaPill(label = label)
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
        ReleaseKind.MOVIE -> item.movieOverviewRu
            ?.takeIf { it.isNotBlank() }
            ?: "Фильм доступен в релизах LostFilm."
    }
}

private fun ReleaseSummary?.episodeOverviewSourceLabel(): String? {
    val item = this ?: return null
    if (item.kind != ReleaseKind.SERIES || item.episodeOverviewRu.isNullOrBlank()) return null
    return when (item.episodeOverviewSource) {
        TmdbEpisodeOverviewSource.MACHINE_TRANSLATED.name -> "Автоперевод"
        TmdbEpisodeOverviewSource.TMDB_EN.name -> "English"
        else -> null
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
        modifier = Modifier.padding(start = 2.dp),
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
private fun HomeBackdrop(item: ReleaseSummary?) {
    val context = LocalContext.current
    val backdropUrl = item?.backdropUrl?.takeIf { it.isNotBlank() }
    val posterUrl = item?.posterUrl?.takeIf { it.isNotBlank() }
    val backdropImage = HomeBackdropImage(
        url = backdropUrl ?: posterUrl,
        isWide = backdropUrl != null,
    )

    Crossfade(
        targetState = backdropImage,
        label = "homeBackdrop",
    ) { targetImage ->
        val targetImageUrl = targetImage.url
        if (targetImageUrl != null) {
            val request = remember(context, targetImageUrl) {
                ImageRequest.Builder(context)
                    .data(targetImageUrl)
                    .size(1280, 720)
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
                        .then(
                            if (targetImage.isWide) {
                                Modifier.fillMaxSize()
                            } else {
                                Modifier
                                    .fillMaxHeight()
                                    .width(1_120.dp)
                                    .blur(8.dp)
                            },
                        ),
                )
            }
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

private data class HomeBackdropImage(
    val url: String?,
    val isWide: Boolean,
)

@Composable
private fun HomeLoadingSkeleton(modifier: Modifier = Modifier) {
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
