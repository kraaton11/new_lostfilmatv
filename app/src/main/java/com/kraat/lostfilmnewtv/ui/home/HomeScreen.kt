package com.kraat.lostfilmnewtv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kraat.lostfilmnewtv.BuildConfig
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import com.kraat.lostfilmnewtv.data.model.ReleaseSummary
import com.kraat.lostfilmnewtv.updates.SavedAppUpdate
import com.kraat.lostfilmnewtv.ui.theme.BackgroundPrimary
import com.kraat.lostfilmnewtv.ui.theme.TextPrimary

@Composable
fun HomeScreen(
    state: HomeUiState = demoHomeUiState(),
    onItemFocused: (String) -> Unit = {},
    onOpenDetails: (String) -> Unit = {},
    onEndReached: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onAuthClick: () -> Unit = {},
    isAuthenticated: Boolean = false,
    appVersionText: String = BuildConfig.VERSION_NAME,
    savedAppUpdate: SavedAppUpdate? = null,
    appUpdateStatusText: String? = null,
    onInstallUpdateClick: () -> Unit = {},
) {
    var focusedItemKey by rememberSaveable(state.items) {
        mutableStateOf(state.selectedItemKey ?: state.items.firstOrNull()?.detailsUrl)
    }

    LaunchedEffect(state.selectedItemKey) {
        if (state.selectedItemKey != null) {
            focusedItemKey = state.selectedItemKey
        }
    }

    val focusedItem = state.items.find { it.detailsUrl == focusedItemKey } ?: state.selectedItem ?: state.items.firstOrNull()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundPrimary)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Новые релизы",
                    color = TextPrimary,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onSettingsClick) {
                        Text("Настройки")
                    }
                    Button(onClick = onAuthClick) {
                        Text(if (isAuthenticated) "Выйти" else "Войти")
                    }
                }
            }

            if (state.showStaleBanner) {
                Text(
                    text = "Данные показаны из кэша и могут быть устаревшими",
                    color = TextPrimary.copy(alpha = 0.78f),
                    fontSize = 16.sp,
                    modifier = Modifier.padding(horizontal = 48.dp),
                )
            }

            if (state.items.isEmpty() && state.fullScreenErrorMessage != null) {
                Text(
                    text = state.fullScreenErrorMessage,
                    color = TextPrimary,
                    modifier = Modifier.padding(horizontal = 48.dp),
                )
            } else {
                HomeRail(
                    items = state.items,
                    focusedItemKey = focusedItemKey,
                    isPaging = state.isPaging,
                    onItemFocused = { detailsUrl ->
                        focusedItemKey = detailsUrl
                        onItemFocused(detailsUrl)
                    },
                    onOpenDetails = onOpenDetails,
                    onEndReached = onEndReached,
                )
                BottomInfoPanel(item = focusedItem)
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 48.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            appUpdateStatusText?.let { statusText ->
                Text(
                    text = statusText,
                    color = TextPrimary.copy(alpha = 0.78f),
                    fontSize = 16.sp,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (savedAppUpdate != null) {
                    Button(onClick = onInstallUpdateClick) {
                        Text("Обновить")
                    }
                }
                Text(
                    text = appVersionText,
                    color = TextPrimary.copy(alpha = 0.56f),
                    fontSize = 16.sp,
                )
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
        selectedItem = demoItem,
        selectedItemKey = demoItem.detailsUrl,
        hasNextPage = true,
    )
}
