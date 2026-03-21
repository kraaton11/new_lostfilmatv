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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import com.kraat.lostfilmnewtv.ui.theme.TextPrimary

private val AccentGold = Color(0xFFF2C46E)
private val AccentGoldFocus = Color(0xFFFFE0A8)
private val AccentBlue = Color(0xFF86C8FF)
private val SurfaceCard = Color(0xCC08111A)
private val SurfaceSoft = Color(0xB30B1520)
private val SurfaceFocused = Color(0xFF16293C)
private val BorderDefault = Color(0x1FFFFFFF)
private val TextSecondary = Color(0xFFCCDAE6)
private val TextMuted = Color(0xFF8FA7BB)
private val StatusError = Color(0xFFE07060)

@Composable
fun DetailsScreen(
    state: DetailsUiState,
    isAuthenticated: Boolean,
    onBack: () -> Unit,
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
        onBack = onBack,
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
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenTorrServe: (String, String) -> Unit,
) {
    when {
        state.errorMessage != null -> ErrorState(message = state.errorMessage, onRetry = onRetry)
        else -> ContentState(
            state = state,
            isAuthenticated = isAuthenticated,
            availableTorrentRowsCount = availableTorrentRowsCount,
            playbackRow = playbackRow,
            torrServeMessage = torrServeMessage,
            activeTorrServeRowId = activeTorrServeRowId,
            isTorrServeBusy = isTorrServeBusy,
            onBack = onBack,
            onOpenTorrServe = onOpenTorrServe,
        )
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundPrimary),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(text = message, color = TextSecondary, fontSize = 18.sp)
            StageButton(
                label = "Повторить",
                subtitle = "Перезагрузить details",
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
    onBack: () -> Unit,
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
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Color(0xFF102434),
                    0.35f to Color(0xFF08131D),
                    1f to BackgroundPrimary,
                ),
            )
            .verticalScroll(scrollState),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(760.dp),
        ) {
            BackgroundPoster(details = details)
            AmbientGlow()

            HeroStage(
                details = details,
                stageUi = stageUi,
            )

            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(top = 118.dp, end = 28.dp, bottom = 152.dp)
                    .width(236.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                StageButton(
                    label = stageUi.primaryAction.label,
                    subtitle = stageUi.primaryAction.subtitle,
                    onClick = {
                        val row = playbackRow ?: return@StageButton
                        onOpenTorrServe(row.rowId, row.url)
                    },
                    modifier = Modifier
                        .testTag(primaryActionTag(stageUi.primaryAction)),
                    isPrimary = true,
                    enabled = stageUi.primaryAction.enabled,
                )
            }
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
                        0.36f to Color(0x8808131D),
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
private fun BoxScope.HeroStage(
    details: ReleaseDetails?,
    stageUi: DetailsStageUiModel,
) {
    Row(
        modifier = Modifier
            .align(Alignment.CenterStart)
            .padding(start = 72.dp, top = 64.dp, end = 320.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PosterCard(details = details)
        Column(
            modifier = Modifier.width(460.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stageUi.title.ifBlank { "Details" },
                color = TextPrimary,
                fontSize = 52.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 56.sp,
            )
            if (stageUi.heroMetaLine.isNotBlank()) {
                Text(
                    text = stageUi.heroMetaLine,
                    color = TextSecondary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            if (stageUi.heroEpisodeTitle.isNotBlank()) {
                Text(
                    text = stageUi.heroEpisodeTitle,
                    color = TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 28.sp,
                )
            }
            if (stageUi.heroStatusLine.isNotBlank()) {
                CompactStatusLine(
                    text = stageUi.heroStatusLine,
                    isError = stageUi.heroStatusLine.contains("Не удалось"),
                )
            }
        }
    }
}

@Composable
private fun PosterCard(details: ReleaseDetails?) {
    Box(
        modifier = Modifier
            .size(width = 254.dp, height = 372.dp)
            .shadow(28.dp, RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF3F586E), Color(0xFF172734), Color(0xFF0A131B)),
                ),
            )
            .border(1.dp, BorderDefault, RoundedCornerShape(28.dp)),
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
private fun CompactStatusLine(
    text: String,
    isError: Boolean,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (isError) Color(0x402A0E10) else SurfaceSoft)
            .border(1.dp, if (isError) StatusError else BorderDefault, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 11.dp),
    ) {
        Text(
            text = text,
            color = if (isError) StatusError else TextSecondary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 22.sp,
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
        !enabled -> SurfaceSoft.copy(alpha = 0.6f)
        isPrimary -> AccentGold
        isFocused -> SurfaceFocused
        isSecondary -> SurfaceSoft
        else -> SurfaceCard
    }
    val border = when {
        isPrimary && isFocused -> AccentGoldFocus
        isPrimary -> AccentGold
        isFocused -> AccentBlue
        else -> BorderDefault
    }
    val textColor = if (isPrimary) Color(0xFF17120D) else TextPrimary
    val subtitleColor = if (isPrimary) Color(0xFF473317) else TextMuted

    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(22.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = background,
            disabledContainerColor = SurfaceSoft.copy(alpha = 0.55f),
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
                color = if (enabled) textColor else TextMuted,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 20.sp,
            )
            Text(
                text = subtitle,
                color = if (enabled) subtitleColor else TextMuted.copy(alpha = 0.8f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.6.sp,
            )
        }
    }
}

private fun primaryActionTag(action: DetailsStageActionUiModel): String {
    val rowId = action.rowId ?: return "details-primary-action"
    return when (action.actionType) {
        DetailsStageActionType.OPEN_TORRSERVE -> "torrent-torrserve-$rowId"
        DetailsStageActionType.NONE -> "details-primary-action"
    }
}
