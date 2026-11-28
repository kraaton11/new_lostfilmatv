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
import com.kraat.lostfilmnewtv.playback.PlaybackQualityPreference
import com.kraat.lostfilmnewtv.playback.WatchedMarkingMode
import com.kraat.lostfilmnewtv.ui.theme.HomeAccentGold

@Composable
internal fun PlaybackSettingsSection(
    selectedQuality: PlaybackQualityPreference,
    onQualitySelected: (PlaybackQualityPreference) -> Unit,
    selectedWatchedMarkingMode: WatchedMarkingMode,
    onWatchedMarkingModeSelected: (WatchedMarkingMode) -> Unit,
    qualityOptions: List<PlaybackQualityPreference>,
    railRequester: FocusRequester,
    contentRequesters: Map<String, FocusRequester>,
    onActionFocused: (SettingsFocusTarget) -> Unit,
) {
    SettingsOptionsSection {
        SettingsOverviewCard(
            title = "Воспроизведение",
            subtitle = "Какое качество запускать и когда отмечать серию просмотренной.",
        )
        Column(modifier = Modifier.focusGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SettingsGroupLabel("КАЧЕСТВО ПО УМОЛЧАНИЮ", HomeAccentGold)
            qualityOptions.forEachIndexed { index, quality ->
                SettingsRowButton(
                    title = quality.shortLabel(),
                    description = quality.description(),
                    value = "",
                    radio = true,
                    onClick = {
                        onQualitySelected(quality)
                        contentRequesters.getValue(quality.buttonTag()).requestFocus()
                    },
                    isSelected = quality == selectedQuality,
                    tag = quality.buttonTag(),
                    onFocused = { onActionFocused(SettingsFocusTarget.PlaybackQuality(quality)) },
                    modifier = Modifier
                        .focusRequester(contentRequesters.getValue(quality.buttonTag()))
                        .focusProperties {
                            left = railRequester
                            up = if (index > 0) {
                                contentRequesters.getValue(qualityOptions[index - 1].buttonTag())
                            } else {
                                railRequester
                            }
                            down = if (index < qualityOptions.lastIndex) {
                                contentRequesters.getValue(qualityOptions[index + 1].buttonTag())
                            } else {
                                contentRequesters.getValue(SettingsFocusTarget.WatchedMarkingToggle.toTag())
                            }
                        },
                )
            }
            SettingsGroupLabel("ПОСЛЕ ЗАПУСКА", HomeAccentGold)
            SettingsRowButton(
                title = "Отмечать просмотренным",
                description = "Серия отмечается автоматически при запуске",
                value = "",
                toggle = true,
                onClick = {
                    onWatchedMarkingModeSelected(
                        if (selectedWatchedMarkingMode == WatchedMarkingMode.AUTO) {
                            WatchedMarkingMode.DISABLED
                        } else {
                            WatchedMarkingMode.AUTO
                        },
                    )
                    contentRequesters.getValue(SettingsFocusTarget.WatchedMarkingToggle.toTag()).requestFocus()
                },
                isSelected = selectedWatchedMarkingMode == WatchedMarkingMode.AUTO,
                tag = SettingsFocusTarget.WatchedMarkingToggle.toTag(),
                onFocused = { onActionFocused(SettingsFocusTarget.WatchedMarkingToggle) },
                modifier = Modifier
                    .focusRequester(contentRequesters.getValue(SettingsFocusTarget.WatchedMarkingToggle.toTag()))
                    .focusProperties {
                        left = railRequester
                        up = contentRequesters.getValue(qualityOptions.last().buttonTag())
                        down = FocusRequester.Default
                    },
            )
        }
    }
}

internal fun PlaybackQualityPreference.shortLabel(): String {
    return when (this) {
        PlaybackQualityPreference.Q1080 -> "1080p"
        PlaybackQualityPreference.Q720 -> "720p"
        PlaybackQualityPreference.Q480 -> "480p"
    }
}

private fun PlaybackQualityPreference.description(): String {
    return when (this) {
        PlaybackQualityPreference.Q1080 -> "Максимальное качество, если доступно."
        PlaybackQualityPreference.Q720 -> "Оптимальный баланс качества и размера."
        PlaybackQualityPreference.Q480 -> "Меньше трафика и быстрее запуск."
    }
}

internal fun PlaybackQualityPreference.buttonTag(): String {
    return SettingsFocusTarget.PlaybackQuality(this).toTag()
}
