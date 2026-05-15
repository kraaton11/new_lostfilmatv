package com.kraat.lostfilmnewtv.ui.settings

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kraat.lostfilmnewtv.ui.theme.FocusBackground
import com.kraat.lostfilmnewtv.ui.theme.HomeAccentGold
import com.kraat.lostfilmnewtv.ui.theme.HomeAccentGoldGlow
import com.kraat.lostfilmnewtv.ui.theme.HomePanelBorder
import com.kraat.lostfilmnewtv.ui.theme.HomePanelBorderFocus
import com.kraat.lostfilmnewtv.ui.theme.HomePanelSurface
import com.kraat.lostfilmnewtv.ui.theme.HomePanelSurfaceStrong
import com.kraat.lostfilmnewtv.ui.theme.HomeTextMuted
import com.kraat.lostfilmnewtv.ui.theme.HomeTextSecondary
import com.kraat.lostfilmnewtv.ui.theme.TextPrimary

@Composable
fun SettingsTvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    enabled: Boolean = true,
    summary: String? = null,
    tag: String? = null,
    summaryTag: String? = null,
    onFocused: (() -> Unit)? = null,
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused && enabled) 1.03f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "buttonScale",
    )

    val backgroundColor = when {
        !enabled -> HomePanelSurface.copy(alpha = 0.72f)
        isSelected -> HomeAccentGold
        isFocused -> FocusBackground
        else -> HomePanelSurface
    }
    val borderColor = when {
        !enabled -> HomePanelBorder
        isSelected && isFocused -> HomeAccentGoldGlow
        isFocused -> HomePanelBorderFocus
        isSelected -> HomeAccentGold
        else -> HomePanelBorder
    }
    val textColor = when {
        !enabled -> HomeTextMuted
        isSelected -> Color(0xFF17120D)
        else -> TextPrimary
    }
    val summaryColor = when {
        !enabled -> HomeTextMuted
        isSelected -> Color(0xFF5E4419)
        else -> HomeTextMuted
    }
    val shape = RoundedCornerShape(12.dp)

    Button(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = textColor,
        ),
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                drawRoundRect(
                    color = backgroundColor,
                    cornerRadius = CornerRadius(12.dp.toPx()),
                )
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .border(2.dp, borderColor, shape)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocused?.invoke()
            }
            .semantics { selected = isSelected }
            .then(if (tag != null) Modifier.testTag(tag) else Modifier),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 1.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = text,
                color = textColor,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (summary != null) {
                Text(
                    text = summary,
                    color = summaryColor,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (summaryTag != null) Modifier.testTag(summaryTag) else Modifier,
                )
            }
        }
    }
}

@Composable
fun SettingsOverviewCard(
    title: String,
    subtitle: String? = null,
    tag: String = "settings-overview-card",
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(tag)
            .focusable(false)
            .border(1.dp, HomePanelBorder, RoundedCornerShape(14.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
            )
            subtitle?.let {
                Text(
                    text = it,
                    color = HomeTextMuted,
                    fontSize = 14.sp,
                )
            }
        }
        content()
    }
}

@Composable
fun SettingsOverviewValue(text: String) {
    Text(
        text = text,
        color = HomeTextSecondary,
        fontSize = 15.sp,
    )
}

@Composable
fun SettingsRowButton(
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    tag: String? = null,
    isSelected: Boolean = false,
    enabled: Boolean = true,
    onFocused: (() -> Unit)? = null,
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused && enabled) 1.015f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "settingsRowScale",
    )
    val shape = RoundedCornerShape(12.dp)
    val backgroundColor = when {
        !enabled -> HomePanelSurface.copy(alpha = 0.58f)
        isSelected -> HomePanelSurfaceStrong
        isFocused -> FocusBackground
        else -> HomePanelSurface
    }
    val borderColor = when {
        !enabled -> HomePanelBorder
        isFocused -> HomePanelBorderFocus
        isSelected -> HomeAccentGold.copy(alpha = 0.72f)
        else -> HomePanelBorder
    }
    val valueColor = when {
        !enabled -> HomeTextMuted
        isSelected -> HomeAccentGoldGlow
        else -> HomeAccentGold
    }
    val textColor = if (enabled) TextPrimary else HomeTextMuted

    Button(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = textColor,
        ),
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                drawRoundRect(
                    color = backgroundColor,
                    cornerRadius = CornerRadius(12.dp.toPx()),
                )
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .border(2.dp, borderColor, shape)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocused?.invoke()
            }
            .semantics { selected = isSelected }
            .then(if (tag != null) Modifier.testTag(tag) else Modifier),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    color = textColor,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (description != null) {
                    Text(
                        text = description,
                        color = HomeTextMuted,
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(modifier = Modifier.width(18.dp))
            Text(
                text = value,
                color = valueColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun SettingsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    tag: String? = null,
    onFocused: (() -> Unit)? = null,
) {
    var isFocused by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        label = { Text(text = label) },
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedLabelColor = HomeAccentGoldGlow,
            unfocusedLabelColor = HomeTextMuted,
            cursorColor = HomeAccentGoldGlow,
            focusedBorderColor = HomePanelBorderFocus,
            unfocusedBorderColor = HomePanelBorder,
            focusedContainerColor = FocusBackground,
            unfocusedContainerColor = HomePanelSurface,
        ),
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) HomePanelBorderFocus else Color.Transparent,
                shape = RoundedCornerShape(12.dp),
            )
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocused?.invoke()
            }
            .then(if (tag != null) Modifier.testTag(tag) else Modifier),
    )
}
