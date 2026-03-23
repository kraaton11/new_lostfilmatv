package com.kraat.lostfilmnewtv.ui.details

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kraat.lostfilmnewtv.data.model.ReleaseDetails
import com.kraat.lostfilmnewtv.ui.theme.BackgroundPrimary
import com.kraat.lostfilmnewtv.ui.theme.DetailsAccentBlue
import com.kraat.lostfilmnewtv.ui.theme.DetailsAccentGold
import com.kraat.lostfilmnewtv.ui.theme.DetailsAccentGoldFocus
import com.kraat.lostfilmnewtv.ui.theme.DetailsBackgroundMid
import com.kraat.lostfilmnewtv.ui.theme.DetailsBackgroundTop
import com.kraat.lostfilmnewtv.ui.theme.DetailsBorderDefault
import com.kraat.lostfilmnewtv.ui.theme.DetailsSurfaceCard
import com.kraat.lostfilmnewtv.ui.theme.DetailsSurfaceFocused
import com.kraat.lostfilmnewtv.ui.theme.DetailsSurfaceSoft
import com.kraat.lostfilmnewtv.ui.theme.DetailsTextMuted
import com.kraat.lostfilmnewtv.ui.theme.DetailsTextSecondary
import com.kraat.lostfilmnewtv.ui.theme.TextPrimary

@Composable
fun DetailsScreen(
    state: DetailsUiState,
    isAuthenticated: Boolean,
    onRetry: () -> Unit,
) {
    val context = LocalContext.current
    val torrentRows = state.details?.toTorrentRows().orEmpty()

    DetailsScreen(
        state = state,
        isAuthenticated = isAuthenticated,
        availableTorrentRowsCount = torrentRows.size,
        playbackRow = torrentRows.firstOrNull(),
        torrServeMessage = null,
        activeTorrServeRowId = null,
        isTorrServeBusy = false,
        onRetry = onRetry,
        onOpenTorrServe = { _, url ->
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        },
    )
}

@Composable
fun DetailsScreen(
    state: DetailsUiState,
    isAuthenticated: Boolean,
    availableTorrentRowsCount: Int,
    playbackRow: DetailsTorrentRowUiModel?,
    torrServeMessage: TorrServeMessage?,
    activeTorrServeRowId: String?,
    isTorrServeBusy: Boolean,
    onRetry: () -> Unit,
    onOpenTorrServe: (String, String) -> Unit,
) {
    when {
        state.errorMessage != null -> ErrorState(message = state.errorMessage, onRetry = onRetry)
        state.isLoading && state.details == null -> LoadingState()
        else -> ContentState(
            state = state,
            isAuthenticated = isAuthenticated,
            availableTorrentRowsCount = availableTorrentRowsCount,
            playbackRow = playbackRow,
            torrServeMessage = torrServeMessage,
            activeTorrServeRowId = activeTorrServeRowId,
            isTorrServeBusy = isTorrServeBusy,
            onOpenTorrServe = onOpenTorrServe,
        )
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(detailsBackgroundBrush()),
        contentAlignment = Alignment.Center,
    ) {
        DetailsCenteredStatePanel {
            CircularProgressIndicator(
                modifier = Modifier.testTag("details-loading"),
                color = DetailsAccentGold,
            )
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(detailsBackgroundBrush()),
        contentAlignment = Alignment.Center,
    ) {
        DetailsCenteredStatePanel {
            Text(text = message, color = DetailsTextSecondary, fontSize = 18.sp)
            StageButton(
                label = "Повторить",
                subtitle = "Повторить загрузку",
                onClick = onRetry,
                modifier = Modifier,
                isPrimary = true,
            )
        }
    }
}

@Composable
private fun ContentState(
    state: DetailsUiState,
    isAuthenticated: Boolean,
    availableTorrentRowsCount: Int,
    playbackRow: DetailsTorrentRowUiModel?,
    torrServeMessage: TorrServeMessage?,
    activeTorrServeRowId: String?,
    isTorrServeBusy: Boolean,
    onOpenTorrServe: (String, String) -> Unit,
) {
    val details = state.details
    val stageUi = buildDetailsStageUi(
        state = state,
        isAuthenticated = isAuthenticated,
        availableTorrentRowsCount = availableTorrentRowsCount,
        playbackRow = playbackRow,
        activeTorrServeRowId = activeTorrServeRowId,
        isTorrServeBusy = isTorrServeBusy,
        torrServeMessageText = torrServeMessage?.takeIf { it.rowId == playbackRow?.rowId }?.text,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(detailsBackgroundBrush()),
    ) {
        BackgroundPoster(details = details)
        AmbientGlow()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 48.dp, top = 24.dp, end = 48.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state.showStaleBanner) {
                DetailsStatusPanel(
                    text = "Данные показаны из кэша и могут быть устаревшими",
                    modifier = Modifier.testTag("details-stale-banner"),
                )
            }

            HeroStage(
                details = details,
                stageUi = stageUi,
                onOpenTorrServe = {
                    val row = playbackRow ?: return@HeroStage
                    onOpenTorrServe(row.rowId, row.url)
                },
            )
            Spacer(modifier = Modifier.weight(1f))
            DetailsBottomStage(
                statusLine = stageUi.bottomStageStatusLine,
                supportLine = stageUi.bottomStageSupportLine,
            )
        }
    }
}

@Composable
private fun BackgroundPoster(details: ReleaseDetails?) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (details != null) {
            AsyncImage(
                model = details.posterUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                alpha = 0.18f,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.36f to DetailsBackgroundMid.copy(alpha = 0.7f),
                        1f to BackgroundPrimary,
                    ),
                ),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to Color(0xB8040A10),
                        0.45f to Color(0x66040A10),
                        1f to Color(0xD8040A10),
                    ),
                ),
        )
    }
}

@Composable
private fun BoxScope.AmbientGlow() {
    Box(
        modifier = Modifier
            .size(760.dp)
            .align(Alignment.TopCenter)
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0x33FFFFFF), Color.Transparent),
                ),
                CircleShape,
            ),
    )
}

@Composable
private fun HeroStage(
    details: ReleaseDetails?,
    stageUi: DetailsStageUiModel,
    onOpenTorrServe: () -> Unit,
) {
    val primaryActionRequester = remember(stageUi.primaryAction.actionId) { FocusRequester() }

    LaunchedEffect(stageUi.primaryAction.actionId, stageUi.primaryAction.enabled) {
        if (stageUi.primaryAction.enabled) {
            withFrameNanos { }
            primaryActionRequester.requestFocus()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PosterCard(details = details)
        Column(
            modifier = Modifier.width(520.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stageUi.title.ifBlank { "Details" },
                color = TextPrimary,
                fontSize = 52.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 56.sp,
            )
            if (stageUi.heroEpisodeTitle.isNotBlank()) {
                Text(
                    text = stageUi.heroEpisodeTitle,
                    color = TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 28.sp,
                )
            }
            if (stageUi.heroMetaLine.isNotBlank()) {
                Text(
                    text = stageUi.heroMetaLine,
                    modifier = Modifier.testTag("details-hero-meta"),
                    color = DetailsTextSecondary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 20.sp,
                )
            }
            StageButton(
                label = stageUi.primaryAction.label,
                subtitle = stageUi.primaryAction.subtitle,
                onClick = onOpenTorrServe,
                modifier = Modifier
                    .width(248.dp)
                    .focusRequester(primaryActionRequester)
                    .focusProperties {
                        up = primaryActionRequester
                        left = primaryActionRequester
                        right = primaryActionRequester
                    }
                    .testTag(primaryActionTag(stageUi.primaryAction)),
                isPrimary = true,
                enabled = stageUi.primaryAction.enabled,
            )
        }
    }
}

@Composable
private fun PosterCard(details: ReleaseDetails?) {
    Box(
        modifier = Modifier
            .size(width = 260.dp, height = 374.dp)
            .shadow(28.dp, RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF3F586E), Color(0xFF172734), Color(0xFF0A131B)),
                ),
            )
            .border(1.dp, DetailsBorderDefault, RoundedCornerShape(28.dp)),
    ) {
        if (details != null) {
            AsyncImage(
                model = details.posterUrl,
                contentDescription = details.titleRu,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                alpha = 0.34f,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color(0x26FFFFFF),
                        0.4f to Color.Transparent,
                        1f to Color(0xAA040A10),
                    ),
                ),
        )
    }
}

@Composable
private fun StageButton(
    label: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isPrimary: Boolean = false,
    isSecondary: Boolean = false,
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.03f else 1f,
        animationSpec = tween(durationMillis = 110),
        label = "stageButtonScale",
    )

    val background = when {
        !enabled -> DetailsSurfaceSoft.copy(alpha = 0.6f)
        isPrimary -> DetailsAccentGold
        isFocused -> DetailsSurfaceFocused
        isSecondary -> DetailsSurfaceSoft
        else -> DetailsSurfaceCard
    }
    val border = when {
        isPrimary && isFocused -> DetailsAccentGoldFocus
        isPrimary -> DetailsAccentGold
        isFocused -> DetailsAccentBlue
        else -> DetailsBorderDefault
    }
    val textColor = if (isPrimary) Color(0xFF17120D) else TextPrimary
    val subtitleColor = if (isPrimary) Color(0xFF473317) else DetailsTextMuted

    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(22.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = background,
            disabledContainerColor = DetailsSurfaceSoft.copy(alpha = 0.55f),
        ),
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .border(1.5.dp, border, RoundedCornerShape(22.dp))
            .onFocusChanged { isFocused = it.isFocused },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                color = if (enabled) textColor else DetailsTextMuted,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 20.sp,
            )
            Text(
                text = subtitle,
                color = if (enabled) subtitleColor else DetailsTextMuted.copy(alpha = 0.8f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.6.sp,
            )
        }
    }
}

private fun detailsBackgroundBrush(): Brush {
    return Brush.verticalGradient(
        0f to DetailsBackgroundTop,
        0.35f to DetailsBackgroundMid,
        1f to BackgroundPrimary,
    )
}

private fun primaryActionTag(action: DetailsStageActionUiModel): String {
    val rowId = action.rowId ?: return "details-primary-action"
    return when (action.actionType) {
        DetailsStageActionType.OPEN_TORRSERVE -> "torrent-torrserve-$rowId"
        DetailsStageActionType.NONE -> "details-primary-action"
    }
}
