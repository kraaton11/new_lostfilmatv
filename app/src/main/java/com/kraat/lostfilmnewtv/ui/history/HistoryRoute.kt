package com.kraat.lostfilmnewtv.ui.history

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import com.kraat.lostfilmnewtv.data.model.WatchHistoryEntry
import com.kraat.lostfilmnewtv.data.model.WatchHistoryFilter
import com.kraat.lostfilmnewtv.ui.components.PosterImage
import com.kraat.lostfilmnewtv.ui.components.TvColorKeyLabel
import com.kraat.lostfilmnewtv.ui.components.resolveArtworkForDisplay
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

private val HistoryRowShape = RoundedCornerShape(8.dp)
private val HistoryPanelShape = RoundedCornerShape(28.dp)

private data class HistoryFilterOption(
    val filter: WatchHistoryFilter,
    val keyCode: Int,
    val label: String,
    val color: Color,
)

private val HistoryFilterOptions = listOf(
    HistoryFilterOption(WatchHistoryFilter.All, KeyEvent.KEYCODE_PROG_RED, "Все", Color(0xFFFF636B)),
    HistoryFilterOption(WatchHistoryFilter.Favorites, KeyEvent.KEYCODE_PROG_GREEN, "Избранные", Color(0xFF69DD93)),
    HistoryFilterOption(WatchHistoryFilter.Ongoing, KeyEvent.KEYCODE_PROG_YELLOW, "Снимающиеся", Color(0xFFFFD46E)),
    HistoryFilterOption(WatchHistoryFilter.Completed, KeyEvent.KEYCODE_PROG_BLUE, "Завершённые", Color(0xFF71B2FF)),
)

@Composable
internal fun HistoryRoute(
    isAuthenticated: Boolean,
    onSignIn: () -> Unit,
    onOpenSeries: (String) -> Unit,
) {
    val viewModel: HistoryViewModel = hiltViewModel()
    val loadedState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val state = if (isAuthenticated) loadedState else HistoryState.SignIn
    val focusRequester = remember { FocusRequester() }
    val filtersRequester = remember { FocusRequester() }
    val focusFilters = state == HistoryState.Loading || state is HistoryState.Loaded && state.entries.isEmpty()
    val listState = rememberLazyListState()
    var lastOpenedSeries by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(isAuthenticated) { viewModel.setAuthenticated(isAuthenticated) }
    LaunchedEffect(state) {
        if (state == HistoryState.SignIn || state == HistoryState.Error) {
            requestFocusWhenReady(focusRequester)
        } else if (focusFilters) {
            requestFocusWhenReady(filtersRequester)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (!isAuthenticated) return@onPreviewKeyEvent false
                val keyEvent = event.nativeKeyEvent
                val option = HistoryFilterOptions.firstOrNull { it.keyCode == keyEvent.keyCode }
                    ?: return@onPreviewKeyEvent false
                if (keyEvent.action == KeyEvent.ACTION_DOWN && keyEvent.repeatCount == 0 &&
                    option.filter != selectedFilter
                ) {
                    lastOpenedSeries = null
                    viewModel.selectFilter(option.filter)
                }
                true
            }
            .background(
                Brush.verticalGradient(
                    0f to DetailsBackgroundTop,
                    0.32f to DetailsBackgroundMid,
                    1f to BackgroundPrimary,
                ),
            )
            .padding(start = 48.dp, top = 24.dp, end = 48.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("История", color = TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
        if (isAuthenticated) {
            Row(
                modifier = if (focusFilters) Modifier.focusRequester(filtersRequester).focusable() else Modifier,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HistoryFilterOptions.forEach { option ->
                    TvColorKeyLabel(option.label, option.color, selectedFilter == option.filter)
                }
            }
        }
        when (state) {
            HistoryState.SignIn -> HistoryStatePanel("Войдите в LostFilm, чтобы увидеть историю просмотра") {
                HistoryAction("Войти", onSignIn, Modifier.focusRequester(focusRequester))
            }
            HistoryState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DetailsAccentGold)
            }
            HistoryState.Error -> HistoryStatePanel("Не удалось загрузить историю просмотра") {
                HistoryAction("Повторить", viewModel::load, Modifier.focusRequester(focusRequester))
            }
            is HistoryState.Loaded -> {
                val entries = state.entries
                if (entries.isEmpty()) {
                    HistoryStatePanel("История просмотра пока пуста")
                } else {
                    LaunchedEffect(entries) {
                        val index = entries.indexOfFirst { it.seriesUrl == lastOpenedSeries }.coerceAtLeast(0)
                        if (listState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
                            listState.scrollToItem(index)
                        }
                        requestFocusWhenReady(focusRequester)
                    }
                    val focusUrl = lastOpenedSeries
                        ?.takeIf { url -> entries.any { it.seriesUrl == url } }
                        ?: entries.first().seriesUrl
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(entries, key = { it.seriesUrl }) { entry ->
                            HistoryRow(
                                entry = entry,
                                modifier = if (entry.seriesUrl == focusUrl) {
                                    Modifier.focusRequester(focusRequester)
                                } else {
                                    Modifier
                                },
                                onClick = {
                                    lastOpenedSeries = entry.seriesUrl
                                    onOpenSeries(entry.seriesUrl)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(entry: WatchHistoryEntry, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(74.dp),
        shape = ButtonDefaults.shape(shape = HistoryRowShape),
        colors = ButtonDefaults.colors(
            containerColor = DetailsSurfaceReadable,
            focusedContainerColor = lerp(DetailsSurfaceReadable, Color.White, 0.08f),
            contentColor = TextPrimary,
            focusedContentColor = TextPrimary,
        ),
        scale = ButtonDefaults.scale(focusedScale = 1f),
        border = ButtonDefaults.border(
            border = Border(BorderStroke(1.dp, HomePanelBorder), shape = HistoryRowShape),
            focusedBorder = Border(BorderStroke(1.5.dp, DetailsAccentGoldFocus), shape = HistoryRowShape),
        ),
        contentPadding = PaddingValues(start = 10.dp, end = 18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            PosterImage(
                model = resolveArtworkForDisplay(entry.posterUrl),
                contentDescription = null,
                modifier = Modifier.size(54.dp).clip(RoundedCornerShape(6.dp)),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    entry.titleRu,
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (entry.titleEn.isNotBlank() && entry.titleEn != entry.titleRu) {
                    Text(
                        entry.titleEn,
                        color = DetailsTextSecondary,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                "Просмотрено ${entry.watchedCount} из ${entry.totalCount}",
                color = DetailsAccentGold,
                fontSize = 15.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ColumnScope.HistoryStatePanel(message: String, action: (@Composable () -> Unit)? = null) {
    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .background(DetailsSurfaceReadable, HistoryPanelShape)
                .border(1.dp, DetailsBorderDefault, HistoryPanelShape)
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(message, color = DetailsTextSecondary, fontSize = 18.sp)
            action?.invoke()
        }
    }
}

@Composable
private fun HistoryAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.colors(
            containerColor = DetailsAccentGold,
            focusedContainerColor = DetailsAccentGoldFocus,
        ),
    ) {
        Text(label, color = Color(0xFF17120D))
    }
}
