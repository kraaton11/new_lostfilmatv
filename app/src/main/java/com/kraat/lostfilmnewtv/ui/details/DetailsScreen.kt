package com.kraat.lostfilmnewtv.ui.details

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import com.kraat.lostfilmnewtv.ui.theme.BackgroundPrimary
import com.kraat.lostfilmnewtv.ui.theme.TextPrimary
import kotlinx.coroutines.launch

// ─── Цветовая палитра ─────────────────────────────────────────────────────────

private val AccentBlue       = Color(0xFF4A90D9)
private val AccentBlueFocus  = Color(0xFF7AB8F5)
private val SurfaceCard      = Color(0xFF162130)
private val SurfaceRow       = Color(0xFF1A2738)
private val SurfacePillFocus = Color(0xFF1D3251)
private val TextSecondary    = Color(0xFFAABDD4)
private val TextMuted        = Color(0xFF637A96)
private val StaleBannerBg    = Color(0x26F0A500)
private val StaleBannerText  = Color(0xFFD4892A)
private val ErrorRed         = Color(0xFFE07060)

// ─── Главный Composable ───────────────────────────────────────────────────────

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

// ─── Состояние ошибки ─────────────────────────────────────────────────────────

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(BackgroundPrimary),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(text = message, color = TextSecondary, fontSize = 18.sp)
            TvButton(label = "Повторить", onClick = onRetry, isPrimary = true)
        }
    }
}

// ─── Основное содержимое ──────────────────────────────────────────────────────

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
    val backRequester = remember { FocusRequester() }
    val pillRequesters = remember(torrentRows) { torrentRows.map { FocusRequester() } }
    val qualityStatus = qualityStatusText(
        hasDetails = details != null,
        torrentRowsCount = torrentRows.size,
        isAuthenticated = isAuthenticated,
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundPrimary)
            .verticalScroll(rememberScrollState()),
    ) {
        // ── Hero-зона ─────────────────────────────────────────────────────────
        Box(
            modifier = Modifier.fillMaxWidth().height(400.dp),
        ) {
            // Размытый фоновый постер
            if (details != null) {
                AsyncImage(
                    model = details.posterUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    alpha = 0.18f,
                )
            }

            // Градиент снизу
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.5f to BackgroundPrimary.copy(alpha = 0.7f),
                            1f to BackgroundPrimary,
                        )
                    ),
            )

            // Постер + метаданные
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 56.dp, end = 56.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                // Постер
                if (details != null) {
                    Box(
                        modifier = Modifier
                            .size(width = 190.dp, height = 285.dp)
                            .shadow(elevation = 24.dp, shape = RoundedCornerShape(14.dp))
                            .clip(RoundedCornerShape(14.dp))
                            .background(SurfaceCard),
                    ) {
                        AsyncImage(
                            model = details.posterUrl,
                            contentDescription = details.titleRu,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                } else if (state.isLoading) {
                    SkeletonBox(width = 190.dp, height = 285.dp)
                }

                // Метаданные
                Column(
                    modifier = Modifier.weight(1f).padding(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (details != null) {
                        KindBadge(kind = details.kind)
                        Text(
                            text = details.titleRu,
                            color = TextPrimary,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 40.sp,
                        )
                        if (details.kind == ReleaseKind.SERIES &&
                            details.seasonNumber != null &&
                            details.episodeNumber != null
                        ) {
                            Text(
                                text = "Сезон ${details.seasonNumber}  ·  Серия ${details.episodeNumber}",
                                color = TextSecondary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        Text(
                            text = details.releaseDateRu,
                            color = TextMuted,
                            fontSize = 16.sp,
                        )
                    } else if (state.isLoading) {
                        SkeletonBox(width = 280.dp, height = 36.dp)
                        Spacer(Modifier.height(4.dp))
                        SkeletonBox(width = 180.dp, height = 22.dp)
                        SkeletonBox(width = 120.dp, height = 16.dp)
                    }
                }
            }
        }

        // ── Нижняя зона ───────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 56.dp)
                .padding(bottom = 56.dp),
        ) {
            // Баннер устаревших данных
            if (state.showStaleBanner) {
                StaleBanner()
                Spacer(Modifier.height(20.dp))
            }

            // Кнопка "Назад"
            TvButton(
                label = "← Назад",
                onClick = onBack,
                modifier = Modifier
                    .focusRequester(backRequester)
                    .focusProperties {
                        down = pillRequesters.firstOrNull() ?: FocusRequester.Default
                    },
            )

            // Раздел торрентов
            if (qualityStatus != null) {
                Spacer(Modifier.height(36.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 3.dp, height = 20.dp)
                            .background(AccentBlue, RoundedCornerShape(2.dp)),
                    )
                    Text(
                        text = "Качество",
                        color = TextMuted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.8.sp,
                    )
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    text = qualityStatus,
                    color = if (torrentRows.isNotEmpty()) TextSecondary else TextMuted,
                    fontSize = 14.sp,
                )
            }

            if (torrentRows.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    torrentRows.forEachIndexed { index, row ->
                        val bringIntoViewRequester = remember { BringIntoViewRequester() }
                        val scope = rememberCoroutineScope()

                        QualityActionPill(
                            label = row.label,
                            onClick = { onOpenTorrServe(row.rowId, row.url) },
                            isBusy = isTorrServeBusy,
                            isActive = activeTorrServeRowId == row.rowId,
                            modifier = Modifier
                                .testTag("torrent-torrserve-${row.rowId}")
                                .bringIntoViewRequester(bringIntoViewRequester)
                                .focusRequester(pillRequesters[index])
                                .focusProperties {
                                    up = backRequester
                                    left = pillRequesters.getOrElse(index - 1) { pillRequesters[index] }
                                    right = pillRequesters.getOrElse(index + 1) { pillRequesters[index] }
                                    down = pillRequesters[index]
                                }
                                .onFocusChanged { focusState ->
                                    if (focusState.isFocused) {
                                        scope.launch { bringIntoViewRequester.bringIntoView() }
                                    }
                                },
                        )
                    }
                }

                val activeMessage = torrServeMessage?.text
                if (activeMessage != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(text = activeMessage, color = ErrorRed, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun QualityActionPill(
    label: String,
    onClick: () -> Unit,
    isBusy: Boolean,
    isActive: Boolean,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1f,
        animationSpec = tween(durationMillis = 110),
        label = "pillScale",
    )

    val bgColor = when {
        isActive -> SurfacePillFocus
        isFocused -> SurfacePillFocus
        else -> SurfaceRow
    }
    val borderColor = when {
        isActive -> AccentBlueFocus
        isFocused -> AccentBlue
        else -> Color(0xFF243248)
    }
    val textColor = when {
        isActive -> AccentBlueFocus
        isFocused -> TextPrimary
        else -> TextSecondary
    }

    Button(
        onClick = onClick,
        enabled = !isBusy,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = bgColor,
            disabledContainerColor = SurfaceRow.copy(alpha = 0.5f),
        ),
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .border(1.5.dp, borderColor, RoundedCornerShape(10.dp))
            .onFocusChanged { isFocused = it.isFocused },
    ) {
        Text(
            text = if (isActive) "Открывается…" else label,
            color = if (isBusy && !isActive) TextMuted else textColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

// ─── TV-кнопка с эффектом фокуса ─────────────────────────────────────────────

@Composable
private fun TvButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isPrimary: Boolean = false,
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.07f else 1f,
        animationSpec = tween(durationMillis = 110),
        label = "btnScale",
    )

    val bgColor = when {
        !enabled  -> SurfaceCard.copy(alpha = 0.5f)
        isPrimary -> AccentBlue
        else      -> SurfaceCard
    }

    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = bgColor,
            disabledContainerColor = SurfaceCard.copy(alpha = 0.4f),
        ),
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(
                if (isFocused) Modifier.border(
                    width = 2.dp,
                    color = if (isPrimary) AccentBlueFocus else AccentBlue,
                    shape = RoundedCornerShape(10.dp),
                ) else Modifier
            )
            .onFocusChanged { isFocused = it.isFocused },
    ) {
        Text(
            text = label,
            color = if (enabled) TextPrimary else TextMuted,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

// ─── Вспомогательные компоненты ───────────────────────────────────────────────

@Composable
private fun KindBadge(kind: ReleaseKind) {
    val label = when (kind) { ReleaseKind.SERIES -> "СЕРИАЛ"; ReleaseKind.MOVIE -> "ФИЛЬМ" }
    val color = when (kind) { ReleaseKind.SERIES -> AccentBlue; ReleaseKind.MOVIE -> Color(0xFF5A7A9A) }
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.18f), RoundedCornerShape(6.dp))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
    }
}

@Composable
private fun StaleBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(StaleBannerBg, RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = "⚠  Данные показаны из кэша и могут быть устаревшими",
            color = StaleBannerText,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun SkeletonBox(width: Dp, height: Dp) {
    Box(
        modifier = Modifier
            .size(width = width, height = height)
            .clip(RoundedCornerShape(6.dp))
            .background(SurfaceCard),
    )
}
