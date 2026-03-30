package com.kraat.lostfilmnewtv.ui.home
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.kraat.lostfilmnewtv.BuildConfig
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
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun HomeScreen(
    state: HomeUiState = demoHomeUiState(),
    onItemFocused: (String) -> Unit = {},
    onModeSelected: (HomeFeedMode) -> Unit = {},
    onOpenDetails: (String) -> Unit = {},
    onEndReached: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onAuthClick: () -> Unit = {},
    onRetry: () -> Unit = {},
    onPagingRetry: () -> Unit = {},
    isAuthenticated: Boolean = false,
    appVersionText: String = BuildConfig.VERSION_NAME,
    savedAppUpdate: SavedAppUpdate? = null,
    appUpdateStatusText: String? = null,
    onInstallUpdateClick: () -> Unit = {},
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val activeModeState = when (state.selectedMode) {
        HomeFeedMode.AllNew -> state.allNewModeState
        HomeFeedMode.Favorites -> state.favoritesModeState
    }
    val activeItems = state.itemsForMode(state.selectedMode)
    val activeRailId = when (state.selectedMode) {
        HomeFeedMode.AllNew -> HOME_RAIL_ALL_NEW
        HomeFeedMode.Favorites -> HOME_RAIL_FAVORITES
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
    val settingsRequester = remember { FocusRequester() }
    val updateRequester = remember { FocusRequester() }
    val loginActionRequester = remember { FocusRequester() }
    val retryActionRequester = remember { FocusRequester() }
    val contentEntryRequester = remember(activeRailId) { FocusRequester() }
    val cardFocusRequesters = remember(activeRailId) {
        linkedMapOf<String, FocusRequester>()
    }
    val activeItemFocusKeys = itemKeys.map { detailsUrl -> homeItemKey(activeRailId, detailsUrl) }
    cardFocusRequesters.keys.retainAll(activeItemFocusKeys.toSet())
    activeItemFocusKeys.forEach { itemKey ->
        cardFocusRequesters.getOrPut(itemKey) { FocusRequester() }
    }
    val headerDownTarget = when (activeModeState) {
        is HomeModeContentState.Content -> contentEntryRequester
        is HomeModeContentState.Error -> retryActionRequester
        is HomeModeContentState.LoginRequired -> loginActionRequester
        else -> null
    }
    val railItems = (activeModeState as? HomeModeContentState.Content)?.items ?: activeItems
    val focusedItem = railItems.firstOrNull { it.detailsUrl == focusedItemKey }
        ?: state.selectedItem
        ?: railItems.firstOrNull()
    val stageStatusText = appUpdateStatusText ?: savedAppUpdate?.let { "Доступно обновление ${it.latestVersion}" }

    LaunchedEffect(startupContentFocusPending, activeModeState, headerDownTarget) {
        if (!startupContentFocusPending || headerDownTarget == null) {
            return@LaunchedEffect
        }
        if (activeModeState == HomeModeContentState.Loading) {
            return@LaunchedEffect
        }
        requestFocusWhenReady(headerDownTarget)
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 48.dp, top = 24.dp, end = 48.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val headerPrimaryRequester = if (state.availableModes.size > 1) {
                modeToggleRequester
            } else {
                settingsRequester
            }

            HomeHeader(
                selectedMode = state.selectedMode,
                availableModes = state.availableModes,
                onModeActivated = { mode ->
                    startupContentFocusPending = true
                    onModeSelected(mode)
                },
                onHeaderInteraction = { /* navigation within header; rail resets this on focus */ },
                hasSavedUpdate = savedAppUpdate != null,
                onSettingsClick = onSettingsClick,
                onInstallUpdateClick = onInstallUpdateClick,
                modeToggleFocusRequester = if (state.availableModes.size > 1) modeToggleRequester else null,
                settingsFocusRequester = settingsRequester,
                updateFocusRequester = if (savedAppUpdate != null) updateRequester else null,
                downTarget = headerDownTarget,
            )

            if (state.showStaleBanner) {
                HomeStatusPanel(
                    tag = "home-stale-status",
                    text = "Данные показаны из кэша и могут быть устаревшими",
                    isError = false,
                )
            }

            if (state.fullScreenErrorMessage != null && state.items.isNotEmpty()) {
                HomeStatusPanel(
                    tag = "home-error-status",
                    text = state.fullScreenErrorMessage,
                    isError = true,
                    actionLabel = "Повторить",
                    onAction = onRetry,
                )
            }

            when {
                state.isInitialLoading && state.items.isEmpty() && state.fullScreenErrorMessage == null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = HomeAccentGold)
                    }
                }
                state.items.isEmpty() && state.fullScreenErrorMessage != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        HomeCenteredPanel {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text(
                                    text = state.fullScreenErrorMessage,
                                    color = TextPrimary,
                                    fontSize = 18.sp,
                                )
                                Button(
                                    onClick = onRetry,
                                    colors = ButtonDefaults.buttonColors(containerColor = HomeAccentGold),
                                ) {
                                    Text("Повторить", color = Color(0xFF17120D))
                                }
                            }
                        }
                    }
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        when (activeModeState) {
                            is HomeModeContentState.Content -> {
                                HomeRail(
                                    railId = activeRailId,
                                    items = activeModeState.items,
                                    focusedItemKey = focusedItemKey,
                                    entryFocusRequester = contentEntryRequester,
                                    cardFocusRequesters = cardFocusRequesters,
                                    shouldRequestFocus = startupContentFocusPending,
                                    upTargetRequester = headerPrimaryRequester,
                                    downTargetRequester = null,
                                    isPaging = state.isPaging && state.selectedMode == HomeFeedMode.AllNew,
                                    onItemFocused = { detailsUrl ->
                                        startupContentFocusPending = false
                                        focusedItemKey = detailsUrl
                                        onItemFocused(detailsUrl)
                                    },
                                    onOpenDetails = onOpenDetails,
                                    onEndReached = if (state.selectedMode == HomeFeedMode.AllNew) onEndReached else ({}),
                                )
                            }
                            HomeModeContentState.Empty -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    HomeCenteredPanel {
                                        Text(
                                            text = "Пока нет новых релизов в избранном",
                                            color = TextPrimary,
                                            fontSize = 18.sp,
                                        )
                                    }
                                }
                            }
                            is HomeModeContentState.Error -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    HomeCenteredPanel {
                                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                            Text(
                                                text = activeModeState.message,
                                                color = TextPrimary,
                                                fontSize = 18.sp,
                                            )
                                            Button(
                                                onClick = onRetry,
                                                modifier = Modifier
                                                    .focusRequester(retryActionRequester)
                                                    .testTag("home-mode-retry-action"),
                                                colors = ButtonDefaults.buttonColors(containerColor = HomeAccentGold),
                                            ) {
                                                Text("Повторить", color = Color(0xFF17120D))
                                            }
                                        }
                                    }
                                }
                            }
                            is HomeModeContentState.LoginRequired -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    HomeCenteredPanel {
                                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                            Text(
                                                text = activeModeState.message,
                                                color = TextPrimary,
                                                fontSize = 18.sp,
                                            )
                                            Button(
                                                onClick = onAuthClick,
                                                modifier = Modifier
                                                    .focusRequester(loginActionRequester)
                                                    .testTag("home-mode-login-action"),
                                                colors = ButtonDefaults.buttonColors(containerColor = HomeAccentGold),
                                            ) {
                                                Text("Войти", color = Color(0xFF17120D))
                                            }
                                        }
                                    }
                                }
                            }
                            HomeModeContentState.Loading -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(color = HomeAccentGold)
                                }
                            }
                        }
                        if (state.pagingErrorMessage != null) {
                            HomeStatusPanel(
                                tag = "home-paging-status",
                                text = state.pagingErrorMessage,
                                isError = true,
                                actionLabel = "Повторить",
                                onAction = onPagingRetry,
                            )
                        }
                        if (activeModeState is HomeModeContentState.Content) {
                            HomeBottomStage(
                                item = focusedItem,
                                appVersionText = appVersionText,
                                appUpdateStatusText = stageStatusText,
                            )
                        }
                    }
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
private fun HomeCenteredPanel(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(28.dp)

    Column(
        modifier = Modifier
            .testTag("home-centered-panel")
            .background(HomePanelSurfaceStrong, shape)
            .border(BorderStroke(1.dp, HomePanelBorder), shape)
            .padding(horizontal = 28.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        content()
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
            focusMoved = runCatching {
                    requester.requestFocus()
                    true
                }.getOrDefault(false)
        }
    }
    return focusMoved
}
