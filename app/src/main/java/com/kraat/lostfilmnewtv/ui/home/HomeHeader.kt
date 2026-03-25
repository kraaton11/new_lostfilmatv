package com.kraat.lostfilmnewtv.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
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
    onModeSelected: (HomeFeedMode) -> Unit,
    onModeActivated: (HomeFeedMode) -> Unit,
    onHeaderInteraction: () -> Unit,
    hasSavedUpdate: Boolean,
    onSettingsClick: () -> Unit,
    onInstallUpdateClick: () -> Unit,
    modeFocusRequesters: Map<HomeFeedMode, FocusRequester>,
    settingsFocusRequester: FocusRequester,
    updateFocusRequester: FocusRequester?,
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
    val lastModeRequester = availableModes.lastOrNull()?.let(modeFocusRequesters::get)

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
            availableModes.forEachIndexed { index, mode ->
                val leftMode = availableModes.getOrNull(index - 1)
                val rightMode = availableModes.getOrNull(index + 1)
                val leftRequester = availableModes.getOrNull(index - 1)?.let(modeFocusRequesters::get)
                val rightRequester = availableModes.getOrNull(index + 1)?.let(modeFocusRequesters::get)
                    ?: settingsFocusRequester
                HomeHeaderModeButton(
                    mode = mode,
                    isSelected = mode == selectedMode,
                    onClick = {
                        onHeaderInteraction()
                        onModeActivated(mode)
                    },
                    onInteraction = onHeaderInteraction,
                    onMoveLeft = leftMode?.let { targetMode ->
                        {
                            onModeSelected(targetMode)
                            modeFocusRequesters.getValue(targetMode).requestFocus()
                        }
                    },
                    onMoveRight = rightMode?.let { targetMode ->
                        {
                            onModeSelected(targetMode)
                            modeFocusRequesters.getValue(targetMode).requestFocus()
                        }
                    },
                    modifier = Modifier
                        .testTag("home-mode-tab-${mode.testTagSuffix()}")
                        .focusRequester(modeFocusRequesters.getValue(mode))
                        .focusProperties {
                            if (leftRequester != null) {
                                left = leftRequester
                            }
                            right = rightRequester
                            if (downTarget != null) {
                                down = downTarget
                            }
                        },
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HomeHeaderActionButton(
                label = "Настройки",
                subtitle = "Параметры приложения",
                onClick = onSettingsClick,
                onInteraction = onHeaderInteraction,
                modifier = Modifier
                    .testTag("home-action-settings")
                    .focusRequester(settingsFocusRequester)
                    .focusProperties {
                        if (lastModeRequester != null) {
                            left = lastModeRequester
                        }
                    }
                    .applyDownFocus(downTarget),
            )
            if (hasSavedUpdate && updateFocusRequester != null) {
                HomeHeaderActionButton(
                    label = "Обновить",
                    subtitle = "Установить свежую сборку",
                    onClick = onInstallUpdateClick,
                    onInteraction = onHeaderInteraction,
                    isPrimary = true,
                    modifier = Modifier
                        .testTag("home-action-update")
                        .focusRequester(updateFocusRequester)
                        .applyDownFocus(downTarget),
                )
            }
        }
    }
}

@Composable
private fun HomeHeaderModeButton(
    mode: HomeFeedMode,
    isSelected: Boolean,
    onClick: () -> Unit,
    onInteraction: () -> Unit,
    onMoveLeft: (() -> Unit)?,
    onMoveRight: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val label = when (mode) {
        HomeFeedMode.AllNew -> "Новые релизы"
        HomeFeedMode.Favorites -> "Избранное"
    }
    HomeHeaderActionButton(
        label = label,
        subtitle = "Режим Home",
        onClick = onClick,
        modifier = modifier
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }

                if (event.key.isHeaderInteractionKey()) {
                    onInteraction()
                }

                when (event.key) {
                    Key.DirectionLeft -> {
                        onMoveLeft?.invoke()
                        onMoveLeft != null
                    }

                    Key.DirectionRight -> {
                        onMoveRight?.invoke()
                        onMoveRight != null
                    }

                    else -> false
                }
            },
        isPrimary = isSelected,
    )
}

@Composable
private fun HomeHeaderActionButton(
    label: String,
    subtitle: String,
    onClick: () -> Unit,
    onInteraction: () -> Unit = {},
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.03f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "homeActionScale",
    )

    val backgroundColor = when {
        isPrimary -> HomeAccentGold
        isFocused -> HomePanelSurfaceStrong
        else -> HomePanelSurface
    }
    val borderColor = when {
        isPrimary && isFocused -> HomeAccentGoldGlow
        isPrimary -> HomeAccentGold
        isFocused -> HomeAccentBlue
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
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key.isHeaderInteractionKey()) {
                    onInteraction()
                }
                false
            }
            .widthIn(min = 156.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .border(1.5.dp, borderColor, shape)
            .onFocusChanged { isFocused = it.isFocused },
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

private fun Modifier.applyDownFocus(downTarget: FocusRequester?): Modifier {
    return if (downTarget == null) {
        this
    } else {
        this.focusProperties {
            down = downTarget
        }
    }
}

private fun HomeFeedMode.testTagSuffix(): String {
    return when (this) {
        HomeFeedMode.AllNew -> "all-new"
        HomeFeedMode.Favorites -> "favorites"
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
