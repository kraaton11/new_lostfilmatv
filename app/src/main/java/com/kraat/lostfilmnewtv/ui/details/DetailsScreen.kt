package com.kraat.lostfilmnewtv.ui.details

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kraat.lostfilmnewtv.data.model.ReleaseDetails
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import com.kraat.lostfilmnewtv.ui.theme.BackgroundPrimary
import com.kraat.lostfilmnewtv.ui.theme.TextPrimary
import kotlinx.coroutines.launch

private val AccentGold = Color(0xFFF2C46E)
private val AccentGoldFocus = Color(0xFFFFE0A8)
private val AccentBlue = Color(0xFF86C8FF)
private val AccentBlueSoft = Color(0x332B7DBA)
private val SurfaceCard = Color(0xCC08111A)
private val SurfaceSoft = Color(0xB30B1520)
private val SurfaceTech = Color(0xB3101C29)
private val SurfaceFocused = Color(0xFF16293C)
private val BorderDefault = Color(0x1FFFFFFF)
private val BorderFocused = Color(0x52FFFFFF)
private val TextSecondary = Color(0xFFCCDAE6)
private val TextMuted = Color(0xFF8FA7BB)
private val StatusWarn = Color(0xFFD99C45)
private val StatusError = Color(0xFFE07060)

@Composable
fun DetailsScreen(
    state: DetailsUiState,
    isAuthenticated: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    val context = LocalContext.current

    DetailsScreen(
        state = state,
        isAuthenticated = isAuthenticated,
        torrentRows = state.details?.toTorrentRows().orEmpty(),
        torrServeMessage = null,
        activeTorrServeRowId = null,
        isTorrServeBusy = false,
        onBack = onBack,
        onRetry = onRetry,
        onOpenLink = { url ->
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        },
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
        state.errorMessage != null -> ErrorState(message = state.errorMessage, onRetry = onRetry)
        else -> ContentState(
            state = state,
            isAuthenticated = isAuthenticated,
            torrentRows = torrentRows,
            torrServeMessage = torrServeMessage,
            activeTorrServeRowId = activeTorrServeRowId,
            isTorrServeBusy = isTorrServeBusy,
            onBack = onBack,
            onOpenLink = onOpenLink,
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
    torrentRows: List<DetailsTorrentRowUiModel>,
    torrServeMessage: TorrServeMessage?,
    activeTorrServeRowId: String?,
    isTorrServeBusy: Boolean,
    onBack: () -> Unit,
    onOpenLink: (String) -> Unit,
    onOpenTorrServe: (String, String) -> Unit,
) {
    val details = state.details
    var activeRowId by remember(torrentRows) { mutableStateOf(torrentRows.firstOrNull()?.rowId) }
    var focusedTechCardId by remember { mutableStateOf("quality") }
    val stageUi = buildDetailsStageUi(
        state = state,
        isAuthenticated = isAuthenticated,
        torrentRows = torrentRows,
        activeRowId = activeRowId,
        activeTorrServeRowId = activeTorrServeRowId,
        isTorrServeBusy = isTorrServeBusy,
    )
    val scrollState = rememberScrollState()
    val actionScrollState = rememberScrollState()
    val backRequester = remember { FocusRequester() }
    val qualityRequesters = remember(stageUi.qualityActions) { stageUi.qualityActions.map { FocusRequester() } }
    val openLinkRequester = remember { FocusRequester() }
    val techRequesters = remember(stageUi.techCards) {
        stageUi.techCards.associate { it.cardId to FocusRequester() }
    }
    val qualityBringIntoView = remember(stageUi.qualityActions) {
        stageUi.qualityActions.map { BringIntoViewRequester() }
    }
    val activeActionIndex = stageUi.qualityActions.indexOfFirst { it.rowId == stageUi.activeRowId }
        .takeIf { it >= 0 } ?: 0
    val activeRow = torrentRows.firstOrNull { it.rowId == stageUi.activeRowId }
    val activeActionMessage = when {
        stageUi.primaryAction.actionType == DetailsStageActionType.OPEN_LINK -> "Этот вариант откроется прямой ссылкой"
        isTorrServeBusy && activeTorrServeRowId == stageUi.activeRowId -> "Открывается..."
        activeRow?.isTorrServeSupported == true -> "Основной путь запуска через TorrServe"
        else -> qualityStatusText(
            hasDetails = details != null,
            torrentRowsCount = torrentRows.size,
            isAuthenticated = isAuthenticated,
        ) ?: ""
    }
    val visibleActionMessage = when {
        torrServeMessage?.text.isNullOrBlank() -> activeActionMessage
        activeActionMessage.isBlank() -> torrServeMessage?.text.orEmpty()
        activeActionMessage.contains(torrServeMessage?.text.orEmpty()) -> activeActionMessage
        else -> "$activeActionMessage\n${torrServeMessage?.text.orEmpty()}"
    }

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
                .height(860.dp),
        ) {
            BackgroundPoster(details = details)
            AmbientGlow()

            TopOverlay(
                stageUi = stageUi,
                details = details,
                state = state,
                onBack = onBack,
                backRequester = backRequester,
                downTarget = qualityRequesters.firstOrNull() ?: FocusRequester.Default,
            )

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
                Column(
                    modifier = Modifier
                        .height(560.dp)
                        .verticalScroll(actionScrollState),
                ) {
                    stageUi.qualityActions.forEachIndexed { index, action ->
                        val row = torrentRows.firstOrNull { it.rowId == action.rowId }
                        val bringIntoViewRequester = qualityBringIntoView[index]
                        val scope = rememberCoroutineScope()
                        StageButton(
                            label = action.label,
                            subtitle = action.subtitle,
                            onClick = {
                                if (row == null) return@StageButton
                                activeRowId = row.rowId
                                if (row.isTorrServeSupported) {
                                    onOpenTorrServe(row.rowId, row.url)
                                } else {
                                    onOpenLink(row.url)
                                }
                            },
                            modifier = Modifier
                                .padding(bottom = 10.dp)
                                .testTag(primaryActionTag(action))
                                .bringIntoViewRequester(bringIntoViewRequester)
                                .focusRequester(qualityRequesters[index])
                                .focusProperties {
                                    up = if (index == 0) backRequester else qualityRequesters[index - 1]
                                    down = qualityRequesters.getOrElse(index + 1) { openLinkRequester }
                                    left = techRequesters[focusedTechCardId] ?: FocusRequester.Default
                                    right = qualityRequesters[index]
                                }
                                .onFocusChanged { focusState ->
                                    if (focusState.isFocused) {
                                        activeRowId = action.rowId
                                        scope.launch { bringIntoViewRequester.bringIntoView() }
                                    }
                                },
                            isPrimary = action.rowId == stageUi.activeRowId,
                            enabled = action.enabled,
                        )
                    }
                    if (stageUi.secondaryActions.isNotEmpty()) {
                        val openLinkAction = stageUi.secondaryActions.first()
                        StageButton(
                            label = openLinkAction.label,
                            subtitle = openLinkAction.subtitle,
                            onClick = {
                                val row = torrentRows.firstOrNull { it.rowId == stageUi.activeRowId } ?: return@StageButton
                                onOpenLink(row.url)
                            },
                            modifier = Modifier
                                .testTag("details-open-link")
                                .focusRequester(openLinkRequester)
                                .focusProperties {
                                    up = qualityRequesters.lastOrNull() ?: backRequester
                                    down = openLinkRequester
                                    left = techRequesters[focusedTechCardId] ?: FocusRequester.Default
                                    right = openLinkRequester
                                },
                            isSecondary = true,
                        )
                    }
                }
            }

            if (!torrServeMessage?.text.isNullOrBlank()) {
                Text(
                    text = torrServeMessage?.text.orEmpty(),
                    color = StatusError,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .width(236.dp)
                        .padding(end = 28.dp, bottom = 118.dp),
                )
            }

            TechSheet(
                stageUi = stageUi,
                activeActionMessage = activeActionMessage,
                techRequesters = techRequesters,
                activeActionRequester = qualityRequesters.getOrElse(activeActionIndex) { backRequester },
                focusedTechCardId = focusedTechCardId,
                onTechFocused = { focusedTechCardId = it },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 26.dp, end = 278.dp, bottom = 26.dp),
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
private fun BoxScope.TopOverlay(
    stageUi: DetailsStageUiModel,
    details: ReleaseDetails?,
    state: DetailsUiState,
    onBack: () -> Unit,
    backRequester: FocusRequester,
    downTarget: FocusRequester,
) {
    Row(
        modifier = Modifier
            .align(Alignment.TopStart)
            .fillMaxWidth()
            .padding(horizontal = 26.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StageButton(
                label = "Назад",
                subtitle = "Вернуться к релизам",
                onClick = onBack,
                modifier = Modifier
                    .width(176.dp)
                    .testTag("details-back")
                    .focusRequester(backRequester)
                    .focusProperties {
                        down = downTarget
                    },
                isSecondary = true,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                stageUi.metaChips.forEach { chip ->
                    MetaChip(text = chip)
                }
            }

            if (details?.kind == ReleaseKind.SERIES &&
                details.seasonNumber != null &&
                details.episodeNumber != null
            ) {
                Text(
                    text = "Сезон ${details.seasonNumber}, серия ${details.episodeNumber}",
                    color = TextSecondary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            details?.releaseDateRu?.let { releaseDate ->
                Text(
                    text = releaseDate,
                    color = TextMuted,
                    fontSize = 15.sp,
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.showStaleBanner) {
                FloatingStatusPill(
                    label = "Кэш",
                    value = "Данные могут быть устаревшими",
                    accent = StatusWarn,
                )
            }
            FloatingStatusPill(
                label = "Режим",
                value = "Cinematic TV Details",
                accent = AccentBlue,
            )
        }
    }
}

@Composable
private fun BoxScope.HeroStage(
    details: ReleaseDetails?,
    stageUi: DetailsStageUiModel,
) {
    Column(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 108.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PosterCard(details = details)
        Spacer(modifier = Modifier.height(34.dp))
        Text(
            text = stageUi.title.ifBlank { "Details" },
            color = TextPrimary,
            fontSize = 58.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 62.sp,
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = stageUi.heroSubtitle,
            color = TextSecondary,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            modifier = Modifier.width(620.dp),
        )
        Spacer(modifier = Modifier.height(18.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            stageUi.heroStats.forEachIndexed { index, stat ->
                HeroStatChip(
                    text = stat,
                    isAccent = index == 1,
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
private fun HeroStatChip(text: String, isAccent: Boolean) {
    Box(
        modifier = Modifier
            .background(
                if (isAccent) AccentBlueSoft else SurfaceSoft,
                RoundedCornerShape(999.dp),
            )
            .border(
                width = 1.dp,
                color = if (isAccent) AccentBlue else BorderDefault,
                shape = RoundedCornerShape(999.dp),
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            color = if (isAccent) AccentBlue else TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun MetaChip(text: String) {
    Box(
        modifier = Modifier
            .background(SurfaceSoft, RoundedCornerShape(999.dp))
            .border(1.dp, BorderDefault, RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            text = text,
            color = TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp,
        )
    }
}

@Composable
private fun FloatingStatusPill(label: String, value: String, accent: Color) {
    Column(
        modifier = Modifier
            .background(SurfaceSoft, RoundedCornerShape(18.dp))
            .border(1.dp, BorderDefault, RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Text(
            text = label,
            color = accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
        )
        Text(
            text = value,
            color = TextSecondary,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun TechSheet(
    stageUi: DetailsStageUiModel,
    activeActionMessage: String,
    techRequesters: Map<String, FocusRequester>,
    activeActionRequester: FocusRequester,
    focusedTechCardId: String,
    onTechFocused: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeCard = stageUi.techCards.firstOrNull { it.cardId == focusedTechCardId } ?: stageUi.techCards.first()
    val techCards = stageUi.techCards

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(SurfaceCard)
            .border(1.dp, BorderDefault, RoundedCornerShape(28.dp))
            .padding(horizontal = 22.dp, vertical = 20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Сигнал релиза",
                    color = TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Техданные собраны из уже доступных сигналов экрана: качества, состояния доступа, формата релиза и статуса запуска.",
                    color = TextMuted,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            }

            MetaChip(text = "Quality · ${stageUi.primaryAction.qualityLabel ?: "N/A"}")
        }

        Spacer(modifier = Modifier.height(18.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            techCards.forEachIndexed { index, card ->
                TechCard(
                    card = card,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("details-tech-${card.cardId}")
                        .focusRequester(techRequesters.getValue(card.cardId))
                        .focusProperties {
                            left = techRequesters[techCards.getOrNull(index - 1)?.cardId]
                                ?: techRequesters.getValue(card.cardId)
                            right = techRequesters[techCards.getOrNull(index + 1)?.cardId]
                                ?: techRequesters.getValue(card.cardId)
                            up = activeActionRequester
                            down = techRequesters.getValue(card.cardId)
                        },
                    isFocused = card.cardId == focusedTechCardId,
                    onFocused = { onTechFocused(card.cardId) },
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DetailPanel(
                label = "Focused card",
                title = "${activeCard.label}: ${activeCard.value}",
                body = activeCard.supportingText,
                modifier = Modifier.weight(1f),
            )
            DetailPanel(
                label = "Action response",
                title = stageUi.primaryAction.label,
                body = activeActionMessage,
                modifier = Modifier.weight(0.92f),
                highlight = activeActionMessage.contains("Открывается") || activeActionMessage.contains("TorrServe"),
                isError = activeActionMessage.contains("Не удалось"),
            )
        }
    }
}

@Composable
private fun TechCard(
    card: DetailsTechCardUiModel,
    isFocused: Boolean,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.03f else 1f,
        animationSpec = tween(durationMillis = 110),
        label = "techCardScale",
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(20.dp))
            .background(if (isFocused) SurfaceFocused else SurfaceTech)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) AccentBlue else BorderDefault,
                shape = RoundedCornerShape(20.dp),
            )
            .onFocusChanged { focusState ->
                if (focusState.isFocused) onFocused()
            }
            .focusable()
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = card.label,
                color = TextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
            Text(
                text = card.value,
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 21.sp,
            )
            Text(
                text = card.supportingText,
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        }
    }
}

@Composable
private fun DetailPanel(
    label: String,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    isError: Boolean = false,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(if (highlight) SurfaceFocused else SurfaceSoft)
            .border(1.dp, BorderDefault, RoundedCornerShape(22.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = label,
                color = TextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
            Text(
                text = title,
                color = if (isError) StatusError else TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 24.sp,
            )
            Text(
                text = body,
                color = if (isError) StatusError else TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        }
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
        DetailsStageActionType.OPEN_LINK -> "torrent-open-$rowId"
        DetailsStageActionType.NONE -> "details-primary-action"
    }
}

private fun ReleaseDetails.releaseTypeLabel(): String {
    return when (kind) {
        ReleaseKind.SERIES -> "SERIES"
        ReleaseKind.MOVIE -> "MOVIE"
    }
}

@Composable
private fun SkeletonBox(width: Dp, height: Dp) {
    Box(
        modifier = Modifier
            .size(width = width, height = height)
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceSoft),
    )
}
