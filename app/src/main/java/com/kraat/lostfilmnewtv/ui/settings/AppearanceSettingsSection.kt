package com.kraat.lostfilmnewtv.ui.settings

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.kraat.lostfilmnewtv.ui.theme.AppTheme

@Composable
internal fun AppearanceSettingsSection(
    selectedTheme: AppTheme,
    onThemeSelected: (AppTheme) -> Unit,
    railRequester: FocusRequester,
    contentRequesters: Map<String, FocusRequester>,
    onActionFocused: (SettingsFocusTarget) -> Unit,
) {
    SettingsOptionsSection {
        Column(modifier = Modifier.focusGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val themes = AppTheme.entries
            themes.forEachIndexed { index, theme ->
                val target = SettingsFocusTarget.Theme(theme)
                val tag = target.toTag()
                val requester = contentRequesters.getValue(tag)
                SettingsRowButton(
                    title = theme.label(),
                    description = null,
                    value = "",
                    radio = true,
                    isSelected = theme == selectedTheme,
                    tag = tag,
                    onClick = {
                        onThemeSelected(theme)
                        requester.requestFocus()
                    },
                    onFocused = { onActionFocused(target) },
                    modifier = Modifier.focusRequester(requester).focusProperties {
                        left = railRequester
                        up = themes.getOrNull(index - 1)?.let {
                            contentRequesters.getValue(SettingsFocusTarget.Theme(it).toTag())
                        } ?: railRequester
                        down = themes.getOrNull(index + 1)?.let {
                            contentRequesters.getValue(SettingsFocusTarget.Theme(it).toTag())
                        } ?: FocusRequester.Default
                    },
                )
            }
        }
    }
}

internal fun AppTheme.label(): String = when (this) {
    AppTheme.CLASSIC -> "Классика"
    AppTheme.AMOLED -> "AMOLED"
    AppTheme.BLUE -> "Синяя"
    AppTheme.NEON -> "Neon"
}
