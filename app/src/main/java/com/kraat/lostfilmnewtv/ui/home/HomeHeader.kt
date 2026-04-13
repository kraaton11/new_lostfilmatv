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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
    modeToggleFocusRequester: FocusRequester?,
    searchFocusRequester: FocusRequester,
    settingsFocusRequester: FocusRequester,
    downTarget: FocusRequester?,
    modifier: Modifier = Modifier,
) {
    val title = when (selectedMode) {
        HomeFeedMode.AllNew -> "Новые релизы"
        HomeFeedMode.Favorites -> "Избранное"
    }
    val subtitle = when (selectedMode) {
        HomeFeedMode.AllNew -> "Быстрый доступ к новым сериям и служебным действиям"
        HomeFeedMode.Favorites -> "Новые релизы по сериалам из избранного LostFilm"
    }
    val hasModeToggle = availableModes.size > 1
    val nextMode = selectedMode.toggled(availableModes)
    val modeRequester = if (hasModeToggle) modeToggleFocusRequester else null

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 30.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                color = HomeTextMuted,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (nextMode != null && modeToggleFocusRequester != null) {
                HomeHeaderModeToggleButton(
                    currentMode = selectedMode,
                    nextMode = nextMode,
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
                modifier = Modifier
                    .testTag("home-action-search")
                    .focusRequester(searchFocusRequester)
                    .focusProperties {
                        if (modeRequester != null) {
                            left = modeRequester
                        }
                        right = settingsFocusRequester
                        if (downTarget != null) {
                            down = downTarget
                        }
                    },
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
    nextMode: HomeFeedMode,
    onClick: () -> Unit,
    onInteraction: () -> Unit,
    onBackClick: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    val label = when (nextMode) {
        HomeFeedMode.AllNew -> "Новые релизы"
        HomeFeedMode.Favorites -> "Избранное"
    }
    val subtitle = when (currentMode) {
        HomeFeedMode.AllNew -> "Открыть избранное"
        HomeFeedMode.Favorites -> "Вернуться к новым релизам"
    }
    HomeHeaderActionButton(
        label = label,
        subtitle = subtitle,
        onClick = onClick,
        onInteraction = onInteraction,
        onBackClick = onBackClick,
        isPrimary = false,
        modifier = modifier,
    )
}

@Composable
private fun HomeHeaderActionButton(
    label: String,
    subtitle: String,
    onClick: () -> Unit,
    onInteraction: () -> Unit = {},
    onBackClick: () -> Boolean = { false },
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
    observeKeyInteractions: Boolean = true,
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.06f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "homeActionScale",
    )

    val backgroundColor = when {
        isPrimary -> HomeAccentGold
        isFocused -> FocusBackground
        else -> HomePanelSurface
    }
    val borderColor = when {
        isPrimary && isFocused -> HomeAccentGoldGlow
        isPrimary -> HomeAccentGold
        isFocused -> FocusBorder
        else -> HomePanelBorder
    }
    val textColor = if (isPrimary) Color(0xFF17120D) else TextPrimary
    val subtitleColor = if (isPrimary) Color(0xFF473317) else HomeTextMuted
    val shape = RoundedCornerShape(20.dp)

    Button(
        onClick = onClick,
        shape = shape,
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
            .widthIn(min = 156.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .border(3.dp, borderColor, shape)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) {
                    onInteraction()
                }
            },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 1.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                color = textColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                color = subtitleColor,
                fontSize = 11.sp,
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

    val iconColor = if (isFocused) HomeAccentGold else TextPrimary
    val shape = RoundedCornerShape(12.dp)

    Button(
        onClick = onClick,
        shape = shape,
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
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
            .size(64.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
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
            modifier = Modifier.size(48.dp),
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
    return availableModes.firstOrNull { it != this }
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
