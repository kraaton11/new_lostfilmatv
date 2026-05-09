@file:OptIn(ExperimentalFoundationApi::class)

package com.kraat.lostfilmnewtv.ui.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kraat.lostfilmnewtv.R
import com.kraat.lostfilmnewtv.ui.theme.FocusBorder
import com.kraat.lostfilmnewtv.ui.theme.FocusBackground
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
    selectedNavItem: NavItem,
    onNavItemSelected: (NavItem) -> Unit,
    onNavItemLongClick: (NavItem) -> Unit = {},
    updateVersionText: String?,
    modeFocusRequesters: Map<HomeFeedMode, FocusRequester>,
    searchFocusRequester: FocusRequester,
    updateFocusRequester: FocusRequester,
    settingsFocusRequester: FocusRequester,
    downTarget: FocusRequester?,
    modifier: Modifier = Modifier,
) {
    val hasModeToggle = availableModes.size > 1
    val firstModeRequester = if (hasModeToggle) modeFocusRequesters[availableModes.first()] else null
    val lastModeRequester = if (hasModeToggle) modeFocusRequesters[availableModes.last()] else null
    val hasUpdate = !updateVersionText.isNullOrBlank()
    val initialFocusRequester = firstModeRequester ?: searchFocusRequester

    LaunchedEffect(Unit) {
        initialFocusRequester.requestFocus()
    }

    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (hasModeToggle) {
                HomeHeaderModeSegmentedControl(
                    currentMode = selectedMode,
                    availableModes = availableModes,
                    modeFocusRequesters = modeFocusRequesters,
                    leftEdgeRequester = null,
                    rightEdgeRequester = searchFocusRequester,
                    downTarget = downTarget,
                    onModeClick = { mode ->
                        onHeaderInteraction()
                        onNavItemSelected(NavItem.HOME)
                        onModeActivated(mode)
                    },
                    onModeLongClick = { onNavItemLongClick(NavItem.HOME) },
                    onInteraction = onHeaderInteraction,
                    onBackClick = onBackToContent,
                    modifier = Modifier
                        .testTag("home-mode-control"),
                )
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HomeHeaderActionButton(
                    label = "Поиск",
                    subtitle = "Сериал или фильм",
                    leadingIcon = HeaderActionIcon.Search,
                    onClick = {
                        onNavItemSelected(NavItem.SEARCH)
                        onSearchClick()
                    },
                    onLongClick = { onNavItemLongClick(NavItem.SEARCH) },
                    onInteraction = onHeaderInteraction,
                    onBackClick = onBackToContent,
                    isPrimary = selectedNavItem == NavItem.SEARCH,
                    minWidth = 82.dp,
                    compact = true,
                    hideSubtitle = true,
                    modifier = Modifier
                        .testTag("home-action-search")
                        .focusRequester(searchFocusRequester)
                        .focusProperties {
                            if (lastModeRequester != null) {
                                left = lastModeRequester
                            }
                            right = if (hasUpdate) updateFocusRequester else settingsFocusRequester
                            if (downTarget != null) {
                                down = downTarget
                            }
                        },
                )
                if (hasUpdate) {
                    HomeHeaderActionButton(
                        label = "Обновить",
                        subtitle = updateVersionText.orEmpty(),
                        statusLabel = "Можно обновить",
                        leadingIcon = HeaderActionIcon.Refresh,
                    onClick = {
                        onNavItemSelected(NavItem.UPDATE)
                        onUpdateClick()
                    },
                    onLongClick = { onNavItemLongClick(NavItem.UPDATE) },
                        onInteraction = onHeaderInteraction,
                        onBackClick = onBackToContent,
                        isPrimary = selectedNavItem == NavItem.UPDATE,
                        minWidth = 92.dp,
                        compact = true,
                        hideSubtitle = true,
                        modifier = Modifier
                            .testTag("home-action-update")
                            .focusRequester(updateFocusRequester)
                            .focusProperties {
                                left = searchFocusRequester
                                right = settingsFocusRequester
                                if (downTarget != null) {
                                    down = downTarget
                                }
                            },
                    )
                }
                HomeHeaderIconButton(
                    iconResId = R.drawable.ic_settings,
                    contentDescription = "Настройки",
                    onClick = {
                        onNavItemSelected(NavItem.SETTINGS)
                        onSettingsClick()
                    },
                    onLongClick = { onNavItemLongClick(NavItem.SETTINGS) },
                    onInteraction = onHeaderInteraction,
                    onBackClick = onBackToContent,
                    isSelected = selectedNavItem == NavItem.SETTINGS,
                    modifier = Modifier
                        .testTag("home-action-settings")
                        .focusRequester(settingsFocusRequester)
                        .focusProperties {
                            left = if (hasUpdate) updateFocusRequester else searchFocusRequester
                        }
                        .applyDownFocus(downTarget),
                )
            }
        }
    }
}

@Composable
private fun HomeHeaderModeSegmentedControl(
    currentMode: HomeFeedMode,
    availableModes: List<HomeFeedMode>,
    modeFocusRequesters: Map<HomeFeedMode, FocusRequester>,
    leftEdgeRequester: FocusRequester?,
    rightEdgeRequester: FocusRequester,
    downTarget: FocusRequester?,
    onModeClick: (HomeFeedMode) -> Unit,
    onModeLongClick: (HomeFeedMode) -> Unit,
    onInteraction: () -> Unit,
    onBackClick: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)

    Row(
        modifier = modifier
            .height(38.dp)
            .width(
                when {
                    availableModes.size > 3 -> 390.dp
                    availableModes.size > 2 -> 305.dp
                    else -> 200.dp
                },
            )
            .background(HomePanelSurface, shape)
            .border(1.dp, HomePanelBorder.copy(alpha = 0.52f), shape)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        availableModes.forEachIndexed { index, mode ->
            val requester = modeFocusRequesters.getValue(mode)
            val leftRequester = if (index == 0) {
                leftEdgeRequester
            } else {
                modeFocusRequesters[availableModes[index - 1]]
            }
            val rightRequester = if (index == availableModes.lastIndex) {
                rightEdgeRequester
            } else {
                modeFocusRequesters[availableModes[index + 1]]
            }
            HomeModeSegmentButton(
                mode = mode,
                label = mode.segmentLabel(),
                selected = currentMode == mode,
                focusRequester = requester,
                leftRequester = leftRequester,
                rightRequester = rightRequester,
                downTarget = downTarget,
                onClick = { onModeClick(mode) },
                onLongClick = { onModeLongClick(mode) },
                onInteraction = onInteraction,
                onBackClick = onBackClick,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun HomeModeSegmentButton(
    mode: HomeFeedMode,
    label: String,
    selected: Boolean,
    focusRequester: FocusRequester,
    leftRequester: FocusRequester?,
    rightRequester: FocusRequester?,
    downTarget: FocusRequester?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onInteraction: () -> Unit,
    onBackClick: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.04f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "homeModeSegmentScale",
    )
    val shape = RoundedCornerShape(9.dp)
    Box(
        modifier = modifier
            .height(34.dp)
            .testTag(if (selected) "home-mode-toggle" else "home-mode-${mode.storageValue}")
            .focusRequester(focusRequester)
            .focusProperties {
                leftRequester?.let { left = it }
                rightRequester?.let { right = it }
                downTarget?.let { down = it }
            }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
                    return@onPreviewKeyEvent onBackClick()
                }
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                    onInteraction()
                    return@onPreviewKeyEvent onBackClick()
                }
                if (event.type == KeyEventType.KeyDown && event.key.isHeaderActivationKey()) {
                    onInteraction()
                    onClick()
                    return@onPreviewKeyEvent true
                }
                if (event.type == KeyEventType.KeyDown && event.key.isHeaderInteractionKey()) {
                    onInteraction()
                }
                false
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .background(
                color = when {
                    selected -> HomeAccentGold
                    isFocused -> FocusBackground
                    else -> Color.Transparent
                },
                shape = shape,
            )
            .border(
                width = if (isFocused) 1.dp else 0.dp,
                color = if (isFocused) FocusBorder else Color.Transparent,
                shape = shape,
            )
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onInteraction()
            }
            .focusable()
            .combinedClickable(
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) Color(0xFF17120D) else TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
        )
    }
}

private enum class HeaderActionIcon {
    Search,
    Refresh,
}

@Composable
private fun HomeHeaderActionButton(
    label: String,
    subtitle: String,
    statusLabel: String? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onInteraction: () -> Unit = {},
    onBackClick: () -> Boolean = { false },
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
    observeKeyInteractions: Boolean = true,
    minWidth: androidx.compose.ui.unit.Dp = 156.dp,
    compact: Boolean = false,
    hideSubtitle: Boolean = false,
    leadingIcon: HeaderActionIcon? = null,
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.04f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
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
    val shape = RoundedCornerShape(if (compact) 9.dp else 16.dp)

    Box(
        modifier = modifier
            .then(
                if (observeKeyInteractions) {
                    Modifier.onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
                            return@onPreviewKeyEvent onBackClick()
                        }
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                            onInteraction()
                            return@onPreviewKeyEvent onBackClick()
                        }
                        if (event.type == KeyEventType.KeyDown && event.key.isHeaderActivationKey()) {
                            onInteraction()
                            onClick()
                            return@onPreviewKeyEvent true
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
            .height(if (compact) 38.dp else 46.dp)
            .widthIn(min = minWidth)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .background(backgroundColor, shape)
            .border(if (isFocused) 1.5.dp else 1.dp, borderColor, shape)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) {
                    onInteraction()
                }
            }
            .focusable()
            .combinedClickable(
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(
                horizontal = if (compact) 11.dp else 15.dp,
                vertical = if (compact) 4.dp else 7.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leadingIcon?.let { icon ->
                HeaderVectorIcon(icon = icon, color = textColor)
            }
            Text(
                text = label,
                color = textColor,
                fontSize = if (compact) 13.sp else 15.sp,
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
                fontSize = if (compact) 8.sp else 11.sp,
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
    onLongClick: () -> Unit = {},
    onInteraction: () -> Unit = {},
    onBackClick: () -> Boolean = { false },
    modifier: Modifier = Modifier,
    observeKeyInteractions: Boolean = true,
    isSelected: Boolean = false,
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.04f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "homeActionScale",
    )

    val iconColor = if (isFocused || isSelected) HomeAccentGoldGlow else HomeTextMuted
    val shape = RoundedCornerShape(9.dp)
    val borderColor = if (isFocused) FocusBorder else HomePanelBorder

    Box(
        modifier = modifier
            .then(
                if (observeKeyInteractions) {
                    Modifier.onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
                            return@onPreviewKeyEvent onBackClick()
                        }
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                            onInteraction()
                            return@onPreviewKeyEvent onBackClick()
                        }
                        if (event.type == KeyEventType.KeyDown && event.key.isHeaderActivationKey()) {
                            onInteraction()
                            onClick()
                            return@onPreviewKeyEvent true
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
            .size(38.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .background(if (isFocused || isSelected) FocusBackground else HomePanelSurface, shape)
            .border(if (isFocused) 1.5.dp else 1.dp, borderColor, shape)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) {
                    onInteraction()
                }
            }
            .focusable()
            .combinedClickable(
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(id = iconResId),
            contentDescription = contentDescription,
            modifier = Modifier.size(18.dp),
            tint = iconColor,
        )
    }
}

@Composable
private fun HeaderVectorIcon(icon: HeaderActionIcon, color: Color) {
    when (icon) {
        HeaderActionIcon.Search -> SearchIcon(color = color)
        HeaderActionIcon.Refresh -> RefreshIcon(color = color)
    }
}

@Composable
private fun SearchIcon(color: Color) {
    androidx.compose.foundation.Canvas(modifier = Modifier.size(18.dp)) {
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
        drawCircle(
            color = color,
            radius = size.minDimension * 0.28f,
            center = Offset(size.width * 0.42f, size.height * 0.42f),
            style = stroke,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.62f, size.height * 0.62f),
            end = Offset(size.width * 0.84f, size.height * 0.84f),
            strokeWidth = 2f,
        )
    }
}

@Composable
private fun RefreshIcon(color: Color) {
    androidx.compose.foundation.Canvas(modifier = Modifier.size(18.dp)) {
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
        drawArc(
            color = color,
            startAngle = 40f,
            sweepAngle = 286f,
            useCenter = false,
            topLeft = Offset(size.width * 0.18f, size.height * 0.18f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.64f, size.height * 0.64f),
            style = stroke,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.78f, size.height * 0.18f),
            end = Offset(size.width * 0.78f, size.height * 0.36f),
            strokeWidth = 2f,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.78f, size.height * 0.18f),
            end = Offset(size.width * 0.60f, size.height * 0.18f),
            strokeWidth = 2f,
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

private fun Key.isHeaderActivationKey(): Boolean {
    return when (this) {
        Key.DirectionCenter,
        Key.Enter,
        Key.NumPadEnter,
        -> true

        else -> false
    }
}
