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
import com.kraat.lostfilmnewtv.tvchannel.AndroidTvChannelMode

@Composable
internal fun ChannelSettingsSection(
    selectedChannelMode: AndroidTvChannelMode,
    onChannelModeSelected: (AndroidTvChannelMode) -> Unit,
    channelModes: List<AndroidTvChannelMode>,
    railRequester: FocusRequester,
    contentRequesters: Map<String, FocusRequester>,
    onActionFocused: (SettingsFocusTarget) -> Unit,
) {
    SettingsOptionsSection {
        SettingsOverviewCard(
            title = "Android TV",
            subtitle = "Канал с релизами на главном экране Android TV, вне приложения.",
        ) {
            SettingsOverviewValue(text = "Сейчас: ${selectedChannelMode.label()}")
        }
        Column(modifier = Modifier.focusGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            channelModes.forEachIndexed { index, mode ->
                SettingsRowButton(
                    title = mode.shortLabel(),
                    description = mode.description(),
                    value = "",
                    radio = true,
                    onClick = {
                        onChannelModeSelected(mode)
                        contentRequesters.getValue(mode.buttonTag()).requestFocus()
                    },
                    isSelected = mode == selectedChannelMode,
                    tag = mode.buttonTag(),
                    onFocused = { onActionFocused(SettingsFocusTarget.ChannelMode(mode)) },
                    modifier = Modifier
                        .focusRequester(contentRequesters.getValue(mode.buttonTag()))
                        .focusProperties {
                            left = railRequester
                            up = if (index > 0) {
                                contentRequesters.getValue(channelModes[index - 1].buttonTag())
                            } else {
                                railRequester
                            }
                            down = if (index < channelModes.lastIndex) {
                                contentRequesters.getValue(channelModes[index + 1].buttonTag())
                            } else {
                                FocusRequester.Default
                            }
                        },
                )
            }
        }
    }
}

internal fun AndroidTvChannelMode.shortLabel(): String {
    return when (this) {
        AndroidTvChannelMode.ALL_NEW -> "Все новые релизы"
        AndroidTvChannelMode.UNWATCHED -> "Только непросмотренные"
        AndroidTvChannelMode.DISABLED -> "Не показывать"
    }
}

private fun AndroidTvChannelMode.description(): String {
    return when (this) {
        AndroidTvChannelMode.ALL_NEW -> "Публиковать все свежие карточки."
        AndroidTvChannelMode.UNWATCHED -> "Показывать только то, что еще не отмечено просмотренным."
        AndroidTvChannelMode.DISABLED -> "Убрать канал приложения с главного экрана TV."
    }
}

private fun AndroidTvChannelMode.label(): String {
    return when (this) {
        AndroidTvChannelMode.ALL_NEW -> "Все новые релизы"
        AndroidTvChannelMode.UNWATCHED -> "Только непросмотренные"
        AndroidTvChannelMode.DISABLED -> "Не показывать"
    }
}

internal fun AndroidTvChannelMode.buttonTag(): String {
    return SettingsFocusTarget.ChannelMode(this).toTag()
}
