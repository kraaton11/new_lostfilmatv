package com.kraat.lostfilmnewtv.ui.settings

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
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
    iconPath: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    enabled: Boolean = true,
    summary: String? = null,
    tag: String? = null,
    summaryTag: String? = null,
    summaryIndicatorColor: Color? = null,
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
        isSelected -> FocusBackground
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
    val textColor = if (enabled) TextPrimary else HomeTextMuted
    val selectionAccent = HomeAccentGold
    val iconColor = if (isSelected) selectionAccent else HomeTextMuted
    val summaryColor = HomeTextMuted
    val shape = RoundedCornerShape(12.dp)

    Button(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
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
                if (isSelected) {
                    drawLine(selectionAccent, Offset(2.dp.toPx(), 10.dp.toPx()),
                        Offset(2.dp.toPx(), size.height - 10.dp.toPx()), strokeWidth = 3.dp.toPx())
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .border(2.dp, if (isFocused) borderColor else Color.Transparent, shape)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocused?.invoke()
            }
            .semantics { selected = isSelected }
            .then(if (tag != null) Modifier.testTag(tag) else Modifier),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val path = remember(iconPath) { PathParser().parsePathString(iconPath).toPath() }
            Canvas(Modifier.size(22.dp)) {
                scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
                    drawPath(path, iconColor, style = Stroke(1.8f))
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 2.dp, vertical = 1.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    text = text,
                    color = textColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (summary != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (summaryIndicatorColor != null) {
                            Canvas(Modifier.size(7.dp)) { drawCircle(summaryIndicatorColor) }
                        }
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
            .padding(horizontal = 2.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
            subtitle?.let {
                Text(
                    text = it,
                    color = HomeTextMuted,
                    fontSize = 13.sp,
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
    radio: Boolean = false,
    toggle: Boolean = false,
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
        isFocused -> FocusBackground
        else -> HomePanelSurfaceStrong
    }
    val borderColor = when {
        !enabled -> HomePanelBorder
        isFocused -> HomeAccentGoldGlow
        else -> Color.Transparent
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
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
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
            val optionAccent = if (isSelected) HomeAccentGold else HomeTextMuted
            if (radio) {
                Canvas(Modifier.size(20.dp)) {
                    drawCircle(
                        color = optionAccent,
                        radius = size.minDimension / 2 - 1.dp.toPx(),
                        style = Stroke(2.dp.toPx()),
                    )
                    if (isSelected) drawCircle(optionAccent, radius = 4.5.dp.toPx())
                }
                Spacer(Modifier.width(14.dp))
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    color = textColor,
                    fontSize = 15.sp,
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
            if (toggle) {
                val thumbColor = HomePanelSurface
                Canvas(Modifier.size(46.dp, 24.dp)) {
                    drawRoundRect(
                        color = optionAccent,
                        cornerRadius = CornerRadius(size.height / 2),
                    )
                    val thumbX = if (isSelected) size.width - size.height / 2 else size.height / 2
                    drawCircle(thumbColor, radius = 9.dp.toPx(), center = Offset(thumbX, size.height / 2))
                }
            } else if (value.isNotBlank()) {
                Spacer(Modifier.width(18.dp))
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
}

@Composable
fun SettingsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    tag: String? = null,
    isPassword: Boolean = false,
    onFocused: (() -> Unit)? = null,
) {
    var isFocused by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = if (isPassword) KeyboardOptions(keyboardType = KeyboardType.Password) else KeyboardOptions.Default,
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
