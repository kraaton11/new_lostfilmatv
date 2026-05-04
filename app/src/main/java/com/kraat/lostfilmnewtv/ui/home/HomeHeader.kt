package com.kraat.lostfilmnewtv.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kraat.lostfilmnewtv.R
import com.kraat.lostfilmnewtv.ui.theme.FocusBorder
import com.kraat.lostfilmnewtv.ui.theme.FocusBackground
import com.kraat.lostfilmnewtv.ui.theme.HomeAccentBlue
import com.kraat.lostfilmnewtv.ui.theme.HomeAccentGold
import com.kraat.lostfilmnewtv.ui.theme.HomeAccentGoldGlow
import com.kraat.lostfilmnewtv.ui.theme.HomePanelBorder
import com.kraat.lostfilmnewtv.ui.theme.HomePanelSurface
import com.kraat.lostfilmnewtv.ui.theme.HomePanelSurfaceStrong
import com.kraat.lostfilmnewtv.ui.theme.HomeTextMuted
import com.kraat.lostfilmnewtv.ui.theme.TextPrimary

@Composable
fun HomeHeader(
    selectedMode: HomeFeedMode,
    availableModes: List<HomeFeedMode>,
    onModeActivated: (HomeFeedMode) -> Unit,
    onHeaderInteraction: () -> Unit,
    onBackToContent: () -> Boolean,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onUpdateClick: () -> Unit,
    updateVersionText: String?,
    modeToggleFocusRequester: FocusRequester?,
    searchFocusRequester: FocusRequester,
    updateFocusRequester: FocusRequester,
    settingsFocusRequester: FocusRequester,
    downTarget: FocusRequester?,
    modifier: Modifier = Modifier,
) {
    val title = when (selectedMode) {
        HomeFeedMode.AllNew -> "Новые релизы"
        HomeFeedMode.Favorites -> "Избранное"
        HomeFeedMode.Movies -> "Фильмы"
        HomeFeedMode.Series -> "Сериалы"
    }
    val subtitle = when (selectedMode) {
        HomeFeedMode.AllNew -> "Новые серии, фильмы и быстрый переход к поиску"
        HomeFeedMode.Favorites -> "Свежие релизы по сериалам из избранного LostFilm"
        HomeFeedMode.Movies -> "Кино LostFilm отдельной витриной"
        HomeFeedMode.Series -> "Каталог сериалов LostFilm"
    }
    val hasModeToggle = availableModes.size > 1
    val nextMode = selectedMode.toggled(availableModes)
    val modeRequester = if (hasModeToggle) modeToggleFocusRequester else null
    val hasUpdate = !updateVersionText.isNullOrBlank()

    Box(modifier = modifier.fillMaxWidth()) {
        Box {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 1.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .size(1.dp)
                    .alpha(0f),
            )
            Text(
                text = subtitle,
                color = HomeTextMuted,
                fontSize = 1.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .size(1.dp)
                    .alpha(0f),
            )
        }

        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (hasUpdate) {
                HomeHeaderActionButton(
                    label = "Обновить",
                    subtitle = updateVersionText.orEmpty(),
                    statusLabel = "Можно обновить",
                    onClick = onUpdateClick,
                    onInteraction = onHeaderInteraction,
                    onBackClick = onBackToContent,
                    isPrimary = true,
                    minWidth = 92.dp,
                    compact = true,
                    hideSubtitle = true,
                    modifier = Modifier
                        .testTag("home-action-update")
                        .focusRequester(updateFocusRequester)
                        .focusProperties {
                            right = modeRequester ?: searchFocusRequester
                            if (downTarget != null) {
                                down = downTarget
                            }
                        },
                )
            }
            if (nextMode != null && modeToggleFocusRequester != null) {
                HomeHeaderModeToggleButton(
                    currentMode = selectedMode,
                    availableModes = availableModes,
                    onClick = {
                        onHeaderInteraction()
                        onModeActivated(nextMode)
                    },
                    onInteraction = onHeaderInteraction,
                    onBackClick = onBackToContent,
                    modifier = Modifier
                        .testTag("home-mode-toggle")
                        .focusRequester(modeToggleFocusRequester)
                        .focusProperties {
                            if (hasUpdate) {
                                left = updateFocusRequester
                            }
                            right = searchFocusRequester
                            if (downTarget != null) {
                                down = downTarget
                            }
                        },
                )
            }

            HomeHeaderActionButton(
                label = "Поиск",
                subtitle = "Сериал или фильм",
                onClick = onSearchClick,
                onInteraction = onHeaderInteraction,
                onBackClick = onBackToContent,
                minWidth = 98.dp,
                compact = true,
                hideSubtitle = true,
                modifier = Modifier
                    .testTag("home-action-search")
                    .focusRequester(searchFocusRequester)
                    .focusProperties {
                        if (modeRequester != null) {
                            left = modeRequester
                        } else if (hasUpdate) {
                            left = updateFocusRequester
                        }
                        right = settingsFocusRequester
                        if (downTarget != null) {
                            down = downTarget
                        }
                    },
            )
            HomeHeaderIconButton(
                iconResId = R.drawable.ic_settings,
                contentDescription = "Настройки",
                onClick = onSettingsClick,
                onInteraction = onHeaderInteraction,
                onBackClick = onBackToContent,
                modifier = Modifier
                    .testTag("home-action-settings")
                    .focusRequester(settingsFocusRequester)
                    .focusProperties {
                        left = searchFocusRequester
                    }
                    .applyDownFocus(downTarget),
            )
        }
    }
}

@Composable
private fun HomeHeaderModeToggleButton(
    currentMode: HomeFeedMode,
    availableModes: List<HomeFeedMode>,
    onClick: () -> Unit,
    onInteraction: () -> Unit,
    onBackClick: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.04f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "homeModeToggleScale",
    )
    val shape = RoundedCornerShape(18.dp)
    val borderColor = if (isFocused) FocusBorder else HomePanelBorder

    Button(
        onClick = onClick,
        shape = shape,
        contentPadding = PaddingValues(4.dp),
        colors = ButtonDefaults.buttonColors(containerColor = HomePanelSurface),
        modifier = modifier
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
                    return@onPreviewKeyEvent onBackClick()
                }
                if (event.type == KeyEventType.KeyDown && event.key.isHeaderInteractionKey()) {
                    onInteraction()
                }
                false
            }
            .heightIn(min = 50.dp)
            .width(
                when {
                    availableModes.size > 3 -> 430.dp
                    availableModes.size > 2 -> 326.dp
                    else -> 224.dp
                },
            )
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .border(1.5.dp, borderColor, shape)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) {
                    onInteraction()
                }
            },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            availableModes.forEach { mode ->
                HomeModeSegment(
                    label = mode.segmentLabel(),
                    selected = currentMode == mode,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun HomeModeSegment(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .background(
                color = if (selected) HomeAccentGold else Color.Transparent,
                shape = shape,
            )
            .padding(horizontal = 10.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) Color(0xFF17120D) else TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HomeHeaderActionButton(
    label: String,
    subtitle: String,
    statusLabel: String? = null,
    onClick: () -> Unit,
    onInteraction: () -> Unit = {},
    onBackClick: () -> Boolean = { false },
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
    observeKeyInteractions: Boolean = true,
    minWidth: androidx.compose.ui.unit.Dp = 156.dp,
    compact: Boolean = false,
    hideSubtitle: Boolean = false,
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.06f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "homeActionScale",
    )

    val backgroundColor = when {
        isPrimary && isFocused -> FocusBackground
        isPrimary -> HomePanelSurface
        isFocused -> FocusBackground
        else -> HomePanelSurface
    }
    val borderColor = when {
        isPrimary && isFocused -> HomeAccentGoldGlow
        isPrimary -> HomeAccentGold.copy(alpha = 0.82f)
        isFocused -> FocusBorder
        else -> HomePanelBorder
    }
    val textColor = if (isPrimary) HomeAccentGold else TextPrimary
    val subtitleColor = if (isPrimary && isFocused) HomeAccentGoldGlow else HomeTextMuted
    val shape = RoundedCornerShape(if (compact) 16.dp else 20.dp)

    Button(
        onClick = onClick,
        shape = shape,
        contentPadding = PaddingValues(
            horizontal = if (compact) 12.dp else 18.dp,
            vertical = if (compact) 7.dp else 10.dp,
        ),
        colors = ButtonDefaults.buttonColors(containerColor = backgroundColor),
        modifier = modifier
            .then(
                if (observeKeyInteractions) {
                    Modifier.onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
                            return@onPreviewKeyEvent onBackClick()
                        }
                        if (event.type == KeyEventType.KeyDown && event.key.isHeaderInteractionKey()) {
                            onInteraction()
                        }
                        false
                    }
                } else {
                    Modifier
                },
            )
            .heightIn(min = if (compact) 48.dp else 58.dp)
            .widthIn(min = minWidth)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .border(if (isFocused) 2.dp else 1.dp, borderColor, shape)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) {
                    onInteraction()
                }
            },
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 4.dp),
        ) {
            Text(
                text = label,
                color = textColor,
                fontSize = if (compact) 13.sp else 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            statusLabel?.let { status ->
                Text(
                    text = status,
                    modifier = Modifier
                        .size(1.dp)
                        .alpha(0f),
                    color = subtitleColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = subtitle,
                modifier = if (hideSubtitle) {
                    Modifier
                        .size(1.dp)
                        .alpha(0f)
                } else {
                    Modifier
                },
                color = subtitleColor,
                fontSize = if (compact) 10.sp else 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HomeHeaderIconButton(
    iconResId: Int,
    contentDescription: String,
    onClick: () -> Unit,
    onInteraction: () -> Unit = {},
    onBackClick: () -> Boolean = { false },
    modifier: Modifier = Modifier,
    observeKeyInteractions: Boolean = true,
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.06f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "homeActionScale",
    )

    val iconColor = if (isFocused) HomeAccentGoldGlow else HomeTextMuted
    val shape = RoundedCornerShape(16.dp)
    val borderColor = if (isFocused) FocusBorder else HomePanelBorder

    Button(
        onClick = onClick,
        shape = shape,
        colors = ButtonDefaults.buttonColors(containerColor = if (isFocused) FocusBackground else HomePanelSurface),
        contentPadding = PaddingValues(0.dp),
        modifier = modifier
            .then(
                if (observeKeyInteractions) {
                    Modifier.onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
                            return@onPreviewKeyEvent onBackClick()
                        }
                        if (event.type == KeyEventType.KeyDown && event.key.isHeaderInteractionKey()) {
                            onInteraction()
                        }
                        false
                    }
                } else {
                    Modifier
                },
            )
            .size(54.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .border(if (isFocused) 2.dp else 1.dp, borderColor, shape)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) {
                    onInteraction()
                }
            },
    ) {
        Icon(
            painter = painterResource(id = iconResId),
            contentDescription = contentDescription,
            modifier = Modifier.size(34.dp),
            tint = iconColor,
        )
    }
}

private fun Modifier.applyDownFocus(downTarget: FocusRequester?): Modifier {
    return if (downTarget == null) {
        this
    } else {
        this.focusProperties {
            down = downTarget
        }
    }
}

private fun HomeFeedMode.toggled(availableModes: List<HomeFeedMode>): HomeFeedMode? {
    if (availableModes.size < 2) return null
    val currentIndex = availableModes.indexOf(this).takeIf { it >= 0 } ?: 0
    return availableModes[(currentIndex + 1) % availableModes.size]
}

private fun HomeFeedMode.segmentLabel(): String {
    return when (this) {
        HomeFeedMode.AllNew -> "Новые"
        HomeFeedMode.Favorites -> "Избранное"
        HomeFeedMode.Movies -> "Фильмы"
        HomeFeedMode.Series -> "Сериалы"
    }
}

private fun Key.isHeaderInteractionKey(): Boolean {
    return when (this) {
        Key.DirectionUp,
        Key.DirectionDown,
        Key.DirectionLeft,
        Key.DirectionRight,
        Key.DirectionCenter,
        Key.Enter,
        Key.NumPadEnter,
        -> true

        else -> false
    }
}
