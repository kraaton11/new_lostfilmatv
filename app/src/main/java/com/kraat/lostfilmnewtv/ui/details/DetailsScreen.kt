package com.kraat.lostfilmnewtv.ui.details

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kraat.lostfilmnewtv.data.model.ReleaseDetails
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
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
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun DetailsScreen(
    state: DetailsUiState,
    isAuthenticated: Boolean,
    onRetry: () -> Unit,
    onWatchedClick: () -> Unit = {},
    onFavoriteClick: () -> Unit = {},
    onSeriesOverviewClick: () -> Unit = {},
    onSeriesGuideClick: () -> Unit = {},
    onAuthClick: () -> Unit = {},
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
        onWatchedClick = onWatchedClick,
        onFavoriteClick = onFavoriteClick,
        onSeriesOverviewClick = onSeriesOverviewClick,
        onSeriesGuideClick = onSeriesGuideClick,
        onAuthClick = onAuthClick,
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
    onWatchedClick: () -> Unit = {},
    onFavoriteClick: () -> Unit = {},
    onSeriesOverviewClick: () -> Unit = {},
    onSeriesGuideClick: () -> Unit = {},
    onAuthClick: () -> Unit = {},
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
            onWatchedClick = onWatchedClick,
            onFavoriteClick = onFavoriteClick,
            onSeriesOverviewClick = onSeriesOverviewClick,
            onSeriesGuideClick = onSeriesGuideClick,
            onAuthClick = onAuthClick,
            onOpenTorrServe = onOpenTorrServe,
        )
    }
}

@Composable
private fun LoadingState() {
    val shimmerBrush = rememberDetailsSkeletonBrush()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(detailsBackgroundBrush()),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 48.dp, top = 24.dp, end = 48.dp, bottom = 24.dp)
                .testTag("details-loading"),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(34.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DetailsSkeletonBox(
                    brush = shimmerBrush,
                    modifier = Modifier.size(width = 264.dp, height = 380.dp),
                    shape = RoundedCornerShape(24.dp),
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    DetailsSkeletonBox(
                        brush = shimmerBrush,
                        modifier = Modifier.size(width = 620.dp, height = 52.dp),
                        shape = RoundedCornerShape(14.dp),
                    )
                    DetailsSkeletonBox(
                        brush = shimmerBrush,
                        modifier = Modifier.size(width = 500.dp, height = 52.dp),
                        shape = RoundedCornerShape(14.dp),
                    )
                    DetailsSkeletonBox(
                        brush = shimmerBrush,
                        modifier = Modifier.size(width = 440.dp, height = 26.dp),
                        shape = RoundedCornerShape(10.dp),
                    )
                    DetailsSkeletonBox(
                        brush = shimmerBrush,
                        modifier = Modifier.size(width = 320.dp, height = 20.dp),
                        shape = RoundedCornerShape(10.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(52.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                repeat(5) {
                    DetailsSkeletonBox(
                        brush = shimmerBrush,
                        modifier = Modifier
                            .weight(1f)
                            .height(76.dp),
                        shape = RoundedCornerShape(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberDetailsSkeletonBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "detailsSkeleton")
    val xOffset by transition.animateFloat(
        initialValue = -520f,
        targetValue = 1_280f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_450, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "detailsSkeletonOffset",
    )
    return Brush.linearGradient(
        colors = listOf(
            DetailsSurfaceSoft.copy(alpha = 0.52f),
            DetailsBorderDefault.copy(alpha = 0.5f),
            DetailsSurfaceSoft.copy(alpha = 0.52f),
        ),
        start = Offset(xOffset, 0f),
        end = Offset(xOffset + 520f, 280f),
    )
}

@Composable
private fun DetailsSkeletonBox(
    brush: Brush,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(brush),
    )
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
    onWatchedClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onSeriesOverviewClick: () -> Unit,
    onSeriesGuideClick: () -> Unit,
    onAuthClick: () -> Unit,
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 44.dp, top = 44.dp, end = 48.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
                onWatchedClick = onWatchedClick,
                onFavoriteClick = onFavoriteClick,
                onSeriesOverviewClick = onSeriesOverviewClick,
                onSeriesGuideClick = onSeriesGuideClick,
                onAuthClick = onAuthClick,
                onOpenTorrServe = {
                    val row = playbackRow ?: return@HeroStage
                    onOpenTorrServe(row.rowId, row.url)
                },
            )
        }
    }
}

@Composable
private fun BackgroundPoster(details: ReleaseDetails?) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (details != null) {
            val backgroundImage = details.backdropUrl?.takeIf { it.isNotBlank() }
                ?: details.posterUrl
            AsyncImage(
                model = backgroundImage,
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
private fun HeroStage(
    details: ReleaseDetails?,
    stageUi: DetailsStageUiModel,
    onWatchedClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onSeriesOverviewClick: () -> Unit,
    onSeriesGuideClick: () -> Unit,
    onAuthClick: () -> Unit,
    onOpenTorrServe: () -> Unit,
) {
    val primaryActionRequester = remember(stageUi.primaryAction.actionId) { FocusRequester() }
    val secondaryActions = stageUi.secondaryActions
    val leadingAction = secondaryActions.firstOrNull { it.actionType == DetailsStageActionType.OPEN_SERIES_OVERVIEW }
    val trailingActions = if (leadingAction == null) secondaryActions else secondaryActions.filterNot { it.actionId == leadingAction.actionId }
    val useFlexibleActionWidth = secondaryActions.size >= 3
    var isPrimaryActionFocused by remember(stageUi.primaryAction.actionId, stageUi.title) { mutableStateOf(false) }
    var isLeadingActionFocusable by remember(stageUi.primaryAction.actionId, leadingAction?.actionId) {
        mutableStateOf(leadingAction == null)
    }
    val secondaryActionRequesters = remember(secondaryActions.map { it.actionId }) {
        secondaryActions.associate { it.actionId to FocusRequester() }
    }

    LaunchedEffect(stageUi.primaryAction.actionId, stageUi.title) {
        val focusApplied = requestDetailsFocusWhenReady(
            requester = primaryActionRequester,
            isFocused = { isPrimaryActionFocused },
        )
        if (leadingAction != null && focusApplied) {
            isLeadingActionFocusable = true
        }
    }

    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(26.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.width(242.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PosterCard(details = details)
            StageButton(
                label = stageUi.primaryAction.label,
                subtitle = stageUi.primaryAction.subtitle,
                onClick = primaryActionClickHandler(
                    action = stageUi.primaryAction,
                    onAuthClick = onAuthClick,
                    onOpenTorrServe = onOpenTorrServe,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(primaryActionRequester)
                    .testTag(primaryActionTag(stageUi.primaryAction)),
                isPrimary = true,
                enabled = stageUi.primaryAction.enabled,
                onFocusedChange = { isPrimaryActionFocused = it },
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(top = 46.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stageUi.title.ifBlank { "Details" },
                color = TextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 32.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (stageUi.heroEpisodeTitle.isNotBlank()) {
                Text(
                    text = stageUi.heroEpisodeTitle,
                    color = DetailsAccentGold,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 25.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val metaChips = remember(details, stageUi.heroMetaLine) {
                buildDetailsMetaChips(details = details, heroMetaLine = stageUi.heroMetaLine)
            }
            if (metaChips.isNotEmpty()) {
                Row(
                    modifier = Modifier.testTag("details-hero-meta"),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    metaChips.forEach { chip ->
                        DetailsMetaChip(text = chip)
                    }
                }
            }
            DetailsInfoRows(
                details = details,
                statusLine = stageUi.heroStatusLine,
            )
            DetailsDivider()
            DetailsDescription(details = details)

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leadingAction?.let { action ->
                    StageButton(
                        label = action.label,
                        subtitle = action.subtitle,
                        onClick = secondaryActionClickHandler(
                            action = action,
                            onWatchedClick = onWatchedClick,
                            onFavoriteClick = onFavoriteClick,
                            onSeriesOverviewClick = onSeriesOverviewClick,
                            onSeriesGuideClick = onSeriesGuideClick,
                        ),
                        modifier = detailsSecondaryActionModifier(action)
                            .focusProperties { canFocus = isLeadingActionFocusable }
                            .focusRequester(secondaryActionRequesters.getValue(action.actionId))
                            .testTag(secondaryActionTag(action)),
                        enabled = action.enabled,
                        isHighlighted = action.isHighlighted,
                        isSecondary = true,
                    )
                }
                trailingActions.forEach { action ->
                    StageButton(
                        label = action.label,
                        subtitle = action.subtitle,
                        onClick = secondaryActionClickHandler(
                            action = action,
                            onWatchedClick = onWatchedClick,
                            onFavoriteClick = onFavoriteClick,
                            onSeriesOverviewClick = onSeriesOverviewClick,
                            onSeriesGuideClick = onSeriesGuideClick,
                        ),
                        modifier = detailsSecondaryActionModifier(action)
                            .focusRequester(secondaryActionRequesters.getValue(action.actionId))
                            .testTag(secondaryActionTag(action)),
                        enabled = action.enabled,
                        isHighlighted = action.isHighlighted,
                        isSecondary = true,
                    )
                }
            }
        }
    }
}

private fun detailsSecondaryActionModifier(action: DetailsStageActionUiModel): Modifier {
    val width = when (action.actionType) {
        DetailsStageActionType.OPEN_SERIES_OVERVIEW -> 104.dp
        DetailsStageActionType.OPEN_SERIES_GUIDE -> 104.dp
        DetailsStageActionType.TOGGLE_FAVORITE -> 116.dp
        DetailsStageActionType.TOGGLE_WATCHED -> 136.dp
        else -> 116.dp
    }
    return Modifier.width(width)
}

@Composable
private fun DetailsMetaChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(DetailsSurfaceSoft.copy(alpha = 0.72f))
            .border(1.dp, DetailsBorderDefault.copy(alpha = 0.58f), RoundedCornerShape(999.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = DetailsTextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun buildDetailsMetaChips(
    details: ReleaseDetails?,
    heroMetaLine: String,
): List<String> {
    return buildList {
        if (details?.kind == ReleaseKind.SERIES) {
            details.seasonNumber?.let { add("Сезон $it") }
            details.episodeNumber?.let { add("Серия $it") }
        } else {
            heroMetaLine.takeIf { it.isNotBlank() }?.let(::add)
        }
        details?.releaseDateRu?.takeIf { it.isNotBlank() }?.let(::add)
        details?.kind?.let { kind ->
            add(
                when (kind) {
                    ReleaseKind.SERIES -> "Сериал"
                    ReleaseKind.MOVIE -> "Фильм"
                },
            )
        }
    }.distinct()
}

@Composable
private fun DetailsInfoRows(
    details: ReleaseDetails?,
    statusLine: String,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(7.dp),
        modifier = Modifier.testTag("details-info-rows"),
    ) {
        statusLine.takeIf { it.isNotBlank() }?.let { status ->
            DetailsInfoRow(
                icon = "▣",
                text = status,
                modifier = Modifier.testTag("details-hero-status"),
            )
        }
        details?.releaseDateRu?.takeIf { it.isNotBlank() }?.let { date ->
            DetailsInfoRow(
                icon = "◷",
                text = "Дата релиза: $date",
            )
        }
    }
}

@Composable
private fun DetailsInfoRow(
    icon: String,
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(DetailsSurfaceSoft.copy(alpha = 0.72f))
                .border(1.dp, DetailsBorderDefault.copy(alpha = 0.45f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = icon,
                color = DetailsTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = text,
            color = DetailsTextSecondary,
            fontSize = 13.sp,
            lineHeight = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DetailsDivider() {
    Box(
        modifier = Modifier
            .width(640.dp)
            .height(1.dp)
            .background(DetailsBorderDefault.copy(alpha = 0.38f)),
    )
}

@Composable
private fun DetailsDescription(details: ReleaseDetails?) {
    val description = buildDetailsDescription(details)
    if (description.isBlank()) return

    Text(
        text = description,
        color = DetailsTextSecondary,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        maxLines = 4,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .widthIn(max = 660.dp)
            .testTag("details-description"),
    )
}

private fun buildDetailsDescription(details: ReleaseDetails?): String {
    if (details == null) return ""
    return when (details.kind) {
        ReleaseKind.SERIES -> {
            details.episodeOverviewRu
                ?.takeIf { it.isNotBlank() }
                ?: details.seriesStatusRu.orEmpty()
        }
        ReleaseKind.MOVIE -> "Фильм доступен в релизах LostFilm."
    }
}

@Composable
private fun PosterCard(details: ReleaseDetails?) {
    Box(
        modifier = Modifier
            .size(width = 242.dp, height = 318.dp)
            .shadow(28.dp, RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF3F586E), Color(0xFF172734), Color(0xFF0A131B)),
                ),
            )
            .border(1.dp, DetailsBorderDefault.copy(alpha = 0.74f), RoundedCornerShape(22.dp)),
    ) {
        if (details != null) {
            AsyncImage(
                model = details.posterUrl,
                contentDescription = details.titleRu,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                alpha = 0.92f,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.54f to Color.Transparent,
                        1f to Color(0xB8040A10),
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
    isHighlighted: Boolean = false,
    onFocusedChange: (Boolean) -> Unit = {},
) {
    var isFocused by remember { mutableStateOf(false) }
    val isInteractionEnabled = enabled
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.03f else 1f,
        animationSpec = tween(durationMillis = 110),
        label = "stageButtonScale",
    )

    val background = when {
        !enabled -> DetailsSurfaceSoft.copy(alpha = 0.6f)
        isPrimary -> DetailsAccentGold
        isHighlighted && isFocused -> DetailsSurfaceFocused
        isHighlighted -> DetailsSurfaceSoft
        isFocused -> DetailsSurfaceFocused
        isSecondary -> DetailsSurfaceSoft
        else -> DetailsSurfaceCard
    }
    val border = when {
        isPrimary && isFocused -> DetailsAccentGoldFocus
        isPrimary -> DetailsAccentGold
        isHighlighted && isFocused -> DetailsAccentGoldFocus
        isHighlighted -> DetailsAccentGold
        isFocused -> DetailsAccentBlue
        else -> DetailsBorderDefault
    }
    val textColor = when {
        isPrimary -> Color(0xFF17120D)
        isHighlighted -> DetailsAccentGold
        else -> TextPrimary
    }
    val subtitleColor = when {
        isPrimary -> Color(0xFF473317)
        isHighlighted -> DetailsAccentGoldFocus
        else -> DetailsTextMuted
    }

    Button(
        onClick = {
            if (isInteractionEnabled) {
                onClick()
            }
        },
        enabled = true,
        shape = RoundedCornerShape(if (isPrimary) 12.dp else 10.dp),
        contentPadding = PaddingValues(
            horizontal = if (isPrimary) 16.dp else 12.dp,
            vertical = 0.dp,
        ),
        colors = ButtonDefaults.buttonColors(
            containerColor = background,
            disabledContainerColor = DetailsSurfaceSoft.copy(alpha = 0.55f),
        ),
        modifier = modifier
            .height(if (isPrimary) 56.dp else 48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = 1f
            }
            .border(1.15.dp, border, RoundedCornerShape(if (isPrimary) 12.dp else 10.dp))
            .onFocusChanged {
                isFocused = it.isFocused
                onFocusedChange(it.isFocused)
            },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isPrimary) Arrangement.spacedBy(12.dp) else Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StageButtonIcon(
                actionLabel = label,
                isPrimary = isPrimary,
                tint = if (enabled) textColor else DetailsTextMuted,
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(if (isPrimary) 1.dp else 0.dp),
            ) {
                Text(
                    text = label,
                    color = if (enabled) textColor else DetailsTextMuted,
                    fontSize = if (isPrimary) 14.sp else 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = if (isPrimary) 17.sp else 15.sp,
                    maxLines = if (isPrimary) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        color = if (enabled) subtitleColor else DetailsTextMuted.copy(alpha = 0.8f),
                        fontSize = if (isPrimary) 10.sp else 9.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun StageButtonIcon(
    actionLabel: String,
    isPrimary: Boolean,
    tint: Color,
) {
    val normalized = actionLabel.lowercase()
    Box(
        modifier = Modifier
            .size(if (isPrimary) 32.dp else 19.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (isPrimary) Color(0xFF1B1710).copy(alpha = 0.9f) else Color.Transparent)
            .border(
                width = if (isPrimary) 0.dp else 1.dp,
                color = if (isPrimary) Color.Transparent else tint.copy(alpha = 0.8f),
                shape = RoundedCornerShape(999.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = when {
                isPrimary -> "▶"
                normalized.contains("обзор") -> "i"
                normalized.contains("сер") || normalized.contains("гид") -> "≡"
                normalized.contains("избран") -> "♡"
                normalized.contains("просмотр") -> "✓"
                else -> "•"
            },
            color = if (isPrimary) DetailsAccentGold else tint,
            fontSize = if (isPrimary) 14.sp else 12.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = if (isPrimary) 14.sp else 12.sp,
        )
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
        DetailsStageActionType.OPEN_AUTH -> "details-primary-action"
        DetailsStageActionType.TOGGLE_WATCHED -> "details-primary-action"
        DetailsStageActionType.TOGGLE_FAVORITE -> "details-primary-action"
        DetailsStageActionType.OPEN_SERIES_OVERVIEW -> "details-primary-action"
        DetailsStageActionType.OPEN_SERIES_GUIDE -> "details-primary-action"
        DetailsStageActionType.NONE -> "details-primary-action"
    }
}

private fun secondaryActionTag(action: DetailsStageActionUiModel): String {
    return when (action.actionType) {
        DetailsStageActionType.TOGGLE_WATCHED -> "details-watched-action"
        DetailsStageActionType.TOGGLE_FAVORITE -> "details-favorite-action"
        DetailsStageActionType.OPEN_SERIES_OVERVIEW -> "details-series-overview-action"
        DetailsStageActionType.OPEN_SERIES_GUIDE -> "details-series-guide-action"
        DetailsStageActionType.OPEN_TORRSERVE -> "details-secondary-${action.actionId}"
        DetailsStageActionType.OPEN_AUTH -> "details-secondary-${action.actionId}"
        DetailsStageActionType.NONE -> "details-secondary-${action.actionId}"
    }
}

private fun primaryActionClickHandler(
    action: DetailsStageActionUiModel,
    onAuthClick: () -> Unit,
    onOpenTorrServe: () -> Unit,
): () -> Unit {
    return when (action.actionType) {
        DetailsStageActionType.OPEN_AUTH -> onAuthClick
        DetailsStageActionType.OPEN_TORRSERVE -> onOpenTorrServe
        else -> ({})
    }
}

private fun secondaryActionClickHandler(
    action: DetailsStageActionUiModel,
    onWatchedClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onSeriesOverviewClick: () -> Unit,
    onSeriesGuideClick: () -> Unit,
): () -> Unit {
    return when (action.actionType) {
        DetailsStageActionType.TOGGLE_WATCHED -> onWatchedClick
        DetailsStageActionType.TOGGLE_FAVORITE -> onFavoriteClick
        DetailsStageActionType.OPEN_SERIES_OVERVIEW -> onSeriesOverviewClick
        DetailsStageActionType.OPEN_SERIES_GUIDE -> onSeriesGuideClick
        DetailsStageActionType.OPEN_TORRSERVE,
        DetailsStageActionType.OPEN_AUTH,
        DetailsStageActionType.NONE,
        -> ({})
    }
}

private suspend fun requestDetailsFocusWhenReady(
    requester: FocusRequester,
    isFocused: () -> Boolean,
): Boolean {
    if (isFocused()) {
        return true
    }

    var focusMoved = false
    withTimeoutOrNull(1_000L) {
        while (!isFocused()) {
            withFrameNanos { }
            focusMoved = runCatching { requester.requestFocus() }.getOrDefault(false)
        }
    }
    return focusMoved || isFocused()
}
