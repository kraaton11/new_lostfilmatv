package com.kraat.lostfilmnewtv.ui.search

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kraat.lostfilmnewtv.data.model.LostFilmSearchItem
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import com.kraat.lostfilmnewtv.ui.theme.BackgroundPrimary
import com.kraat.lostfilmnewtv.ui.theme.DetailsAccentGold
import com.kraat.lostfilmnewtv.ui.theme.DetailsAccentGoldFocus
import com.kraat.lostfilmnewtv.ui.theme.DetailsBackgroundMid
import com.kraat.lostfilmnewtv.ui.theme.DetailsBackgroundTop
import com.kraat.lostfilmnewtv.ui.theme.DetailsBorderDefault
import com.kraat.lostfilmnewtv.ui.theme.DetailsSurfaceFocused
import com.kraat.lostfilmnewtv.ui.theme.DetailsSurfaceReadable
import com.kraat.lostfilmnewtv.ui.theme.DetailsSurfaceSoft
import com.kraat.lostfilmnewtv.ui.theme.DetailsTextSecondary
import com.kraat.lostfilmnewtv.ui.theme.HomePanelBorder
import com.kraat.lostfilmnewtv.ui.theme.TextPrimary

@Composable
fun SearchScreen(
    state: SearchUiState,
    onQueryChanged: (String) -> Unit,
    onSearchTriggered: () -> Unit,
    onRetry: () -> Unit,
    onOpenItem: (LostFilmSearchItem) -> Unit,
) {
    val queryRequester = remember { FocusRequester() }
    val searchButtonRequester = remember { FocusRequester() }
    val firstResultRequester = remember { FocusRequester() }
    val retryRequester = remember { FocusRequester() }
    val downTarget = when {
        state.items.isNotEmpty() -> firstResultRequester
        state.errorMessage != null -> retryRequester
        else -> null
    }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        queryRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(searchBackgroundBrush()),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 48.dp, top = 24.dp, end = 48.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "Поиск LostFilm",
                    color = TextPrimary,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Ищите сериалы и фильмы напрямую на LostFilm",
                    color = DetailsTextSecondary,
                    fontSize = 14.sp,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQueryChanged,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(queryRequester)
                        .focusProperties {
                            right = searchButtonRequester
                            if (downTarget != null) {
                                down = downTarget
                            }
                        }
                        .testTag("search-query-input"),
                    singleLine = true,
                    label = { Text("Запрос") },
                    placeholder = { Text("Например: Ted или Путь дракона") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Search,
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onSearch = { onSearchTriggered() },
                    ),
                )
                Button(
                    onClick = onSearchTriggered,
                    modifier = Modifier
                        .height(56.dp)
                        .focusRequester(searchButtonRequester)
                        .focusProperties {
                            left = queryRequester
                            if (downTarget != null) {
                                down = downTarget
                            }
                        }
                        .testTag("search-submit"),
                    colors = ButtonDefaults.buttonColors(containerColor = DetailsAccentGold),
                ) {
                    Text(
                        text = "Найти",
                        color = Color(0xFF17120D),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            when {
                state.isLoading && state.items.isEmpty() -> {
                    SearchCenteredStatePanel {
                        CircularProgressIndicator(
                            modifier = Modifier.testTag("search-loading"),
                            color = DetailsAccentGold,
                        )
                    }
                }

                state.errorMessage != null -> {
                    SearchCenteredStatePanel {
                        Text(
                            text = state.errorMessage,
                            color = DetailsTextSecondary,
                            fontSize = 18.sp,
                        )
                        Button(
                            onClick = onRetry,
                            modifier = Modifier
                                .focusRequester(retryRequester)
                                .testTag("search-retry"),
                            colors = ButtonDefaults.buttonColors(containerColor = DetailsAccentGold),
                        ) {
                            Text("Повторить", color = Color(0xFF17120D))
                        }
                    }
                }

                state.items.isEmpty() -> {
                    val emptyMessage = when {
                        state.query.isBlank() -> "Введите название сериала или фильма"
                        state.query.trim().length < 2 -> "Введите минимум 2 символа"
                        state.submittedQuery != null -> "По запросу ничего не найдено"
                        else -> "Начните поиск"
                    }
                    SearchCenteredStatePanel {
                        Text(
                            text = emptyMessage,
                            color = TextPrimary,
                            fontSize = 18.sp,
                        )
                    }
                }

                else -> {
                    if (state.isLoading) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = DetailsAccentGold,
                                strokeWidth = 2.dp,
                            )
                            Text(
                                text = "Обновляем результаты",
                                color = DetailsTextSecondary,
                                fontSize = 14.sp,
                            )
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        itemsIndexed(
                            items = state.items,
                            key = { _, item -> item.targetUrl },
                        ) { index, item ->
                            SearchResultCard(
                                item = item,
                                onClick = { onOpenItem(item) },
                                modifier = Modifier
                                    .then(
                                        if (index == 0) {
                                            Modifier.focusRequester(firstResultRequester)
                                        } else {
                                            Modifier
                                        },
                                    )
                                    .testTag("search-result-$index"),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultCard(
    item: LostFilmSearchItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.02f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "searchResultScale",
    )
    val background = if (isFocused) DetailsSurfaceFocused else DetailsSurfaceReadable
    val border = if (isFocused) DetailsAccentGoldFocus else HomePanelBorder
    val kindLabel = when (item.kind) {
        ReleaseKind.SERIES -> "Сериал"
        ReleaseKind.MOVIE -> "Фильм"
    }

    Button(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(containerColor = background),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .border(1.dp, border, RoundedCornerShape(24.dp))
            .onFocusChanged { isFocused = it.isFocused },
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 86.dp, height = 124.dp)
                    .background(DetailsSurfaceSoft, RoundedCornerShape(16.dp))
                    .border(1.dp, DetailsBorderDefault, RoundedCornerShape(16.dp)),
            ) {
                if (!item.posterUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = item.posterUrl,
                        contentDescription = item.titleRu,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = item.titleRu,
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                item.titleEn?.let { titleEn ->
                    Text(
                        text = titleEn,
                        color = DetailsTextSecondary,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = kindLabel,
                    color = DetailsAccentGold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                item.tmdbRating?.takeIf { it.isNotBlank() }?.let { rating ->
                    Text(
                        text = "TMDB $rating",
                        color = DetailsTextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                item.subtitle?.let { subtitle ->
                    Text(
                        text = subtitle,
                        color = DetailsTextSecondary,
                        fontSize = 14.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.SearchCenteredStatePanel(
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

private fun searchBackgroundBrush(): Brush = Brush.verticalGradient(
    0f to DetailsBackgroundTop,
    0.32f to DetailsBackgroundMid,
    1f to BackgroundPrimary,
)
