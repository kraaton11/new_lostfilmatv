package com.kraat.lostfilmnewtv.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import com.kraat.lostfilmnewtv.ui.theme.BackgroundPrimary
import com.kraat.lostfilmnewtv.ui.theme.TextPrimary
import kotlinx.coroutines.launch

@Composable
fun DetailsScreen(
    state: DetailsUiState,
    torrentRows: List<DetailsTorrentRowUiModel>,
    torrServeMessage: TorrServeMessage?,
    activeTorrServeRowId: String?,
    isTorrServeBusy: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenLink: (String) -> Unit,
    onOpenTorrServe: (String, String) -> Unit,
) {
    when {
        state.errorMessage != null -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BackgroundPrimary),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = state.errorMessage,
                        color = TextPrimary,
                    )
                    Button(onClick = onRetry) {
                        Text("Повторить")
                    }
                }
            }
        }

        else -> {
            val details = state.details
            val backRequester = remember { FocusRequester() }
            val focusTargets = remember(torrentRows) {
                torrentRows.associate { row ->
                    row.rowId to TorrentRowFocusTargets(
                        open = FocusRequester(),
                        torrServe = FocusRequester(),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BackgroundPrimary)
                    .verticalScroll(rememberScrollState())
                    .padding(40.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Button(
                    modifier = Modifier.focusRequester(backRequester).focusProperties {
                        down = focusTargets[torrentRows.firstOrNull()?.rowId]?.open ?: FocusRequester.Default
                    },
                    onClick = onBack,
                ) {
                    Text("Назад")
                }

                if (state.showStaleBanner) {
                    Text(
                        text = "Детали показаны из кэша",
                        color = TextPrimary.copy(alpha = 0.72f),
                    )
                }

                if (details != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        AsyncImage(
                            model = details.posterUrl,
                            contentDescription = details.titleRu,
                            modifier = Modifier
                                .size(width = 220.dp, height = 330.dp)
                                .clip(RoundedCornerShape(20.dp)),
                        )
                        Column(
                            modifier = Modifier.width(520.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = details.titleRu,
                                color = TextPrimary,
                                fontSize = 32.sp,
                            )
                            if (details.kind == ReleaseKind.SERIES && details.seasonNumber != null && details.episodeNumber != null) {
                                Text(
                                    text = "Сезон ${details.seasonNumber}, серия ${details.episodeNumber}",
                                    color = TextPrimary.copy(alpha = 0.8f),
                                    fontSize = 20.sp,
                                )
                            }
                            Text(
                                text = details.releaseDateRu,
                                color = TextPrimary.copy(alpha = 0.72f),
                                fontSize = 20.sp,
                            )
                        }
                    }

                    if (torrentRows.isNotEmpty()) {
                        Text(
                            text = "Ссылки",
                            color = TextPrimary,
                            fontSize = 22.sp,
                        )

                        torrentRows.forEachIndexed { index, row ->
                            TorrentRow(
                                row = row,
                                isBusy = isTorrServeBusy,
                                isActive = activeTorrServeRowId == row.rowId,
                                message = if (torrServeMessage?.rowId == row.rowId) torrServeMessage.text else null,
                                focusTargets = focusTargets.getValue(row.rowId),
                                previousOpenRequester = focusTargets[torrentRows.getOrNull(index - 1)?.rowId]?.open
                                    ?: backRequester,
                                nextOpenRequester = focusTargets[torrentRows.getOrNull(index + 1)?.rowId]?.open,
                                onOpenLink = { onOpenLink(row.url) },
                                onOpenTorrServe = { onOpenTorrServe(row.rowId, row.url) },
                            )
                        }
                    }
                } else if (state.isLoading) {
                    Text(
                        text = "Загрузка деталей...",
                        color = TextPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun TorrentRow(
    row: DetailsTorrentRowUiModel,
    isBusy: Boolean,
    isActive: Boolean,
    message: String?,
    focusTargets: TorrentRowFocusTargets,
    previousOpenRequester: FocusRequester,
    nextOpenRequester: FocusRequester?,
    onOpenLink: () -> Unit,
    onOpenTorrServe: () -> Unit,
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    Column(
        modifier = Modifier.bringIntoViewRequester(bringIntoViewRequester),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = row.label,
            color = TextPrimary,
            fontSize = 18.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                modifier = Modifier
                    .testTag("torrent-open-${row.rowId}")
                    .focusRequester(focusTargets.open)
                    .focusProperties {
                        left = focusTargets.open
                        right = if (row.isTorrServeSupported && !isBusy) {
                            focusTargets.torrServe
                        } else {
                            focusTargets.open
                        }
                        up = previousOpenRequester
                        down = nextOpenRequester ?: focusTargets.open
                    }
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            scope.launch {
                                bringIntoViewRequester.bringIntoView()
                            }
                        }
                    },
                onClick = onOpenLink,
            ) {
                Text("Открыть ссылку")
            }
            if (row.isTorrServeSupported) {
                Button(
                    modifier = Modifier
                        .testTag("torrent-torrserve-${row.rowId}")
                        .focusRequester(focusTargets.torrServe)
                        .focusProperties {
                            left = focusTargets.open
                            right = focusTargets.torrServe
                            up = previousOpenRequester
                            down = nextOpenRequester ?: focusTargets.torrServe
                        }
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                scope.launch {
                                    bringIntoViewRequester.bringIntoView()
                                }
                            }
                        },
                    enabled = !isBusy,
                    onClick = onOpenTorrServe,
                ) {
                    Text(if (isActive) "Открывается..." else "TorrServe")
                }
            }
        }
        if (message != null) {
            Text(
                text = message,
                color = TextPrimary.copy(alpha = 0.8f),
                fontSize = 18.sp,
            )
        }
    }
}

private data class TorrentRowFocusTargets(
    val open: FocusRequester,
    val torrServe: FocusRequester,
)
