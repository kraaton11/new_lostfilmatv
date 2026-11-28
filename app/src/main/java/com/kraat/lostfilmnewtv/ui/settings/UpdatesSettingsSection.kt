package com.kraat.lostfilmnewtv.ui.settings

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.kraat.lostfilmnewtv.updates.UpdateCheckMode

@Composable
internal fun UpdatesSectionContent(
    selectedUpdateMode: UpdateCheckMode,
    installedVersionText: String,
    latestVersionText: String?,
    statusText: String?,
    isCheckingForUpdates: Boolean,
    isDownloadingUpdate: Boolean,
    installDownloadProgress: Int?,
    installUrl: String?,
    updateModes: List<UpdateCheckMode>,
    onUpdateModeSelected: (UpdateCheckMode) -> Unit,
    onCheckForUpdatesClick: () -> Unit,
    onInstallUpdateClick: () -> Unit,
    railRequester: FocusRequester,
    contentRequesters: Map<String, FocusRequester>,
    onActionFocused: (SettingsFocusTarget) -> Unit,
) {
    val isInstallVisible by remember(installUrl) {
        derivedStateOf { !installUrl.isNullOrBlank() }
    }
    val isCheckEnabled by remember(isCheckingForUpdates) {
        derivedStateOf { !isCheckingForUpdates }
    }
    val isInstallEnabled by remember(isDownloadingUpdate) {
        derivedStateOf { !isDownloadingUpdate }
    }
    val updateStatus = when {
        isDownloadingUpdate -> installDownloadProgress?.let { "Скачивание обновления… $it%" } ?: "Скачивание обновления…"
        else -> statusText ?: selectedUpdateMode.summaryLabel()
    }
    SettingsOverviewCard(
        title = "Обновления",
        subtitle = "Режим проверки и установка новой версии через GitHub.",
    ) {
        SettingsOverviewValue(text = "Установлена версия: $installedVersionText")
        SettingsOverviewValue(text = "Последняя версия: ${latestVersionText ?: "-"}")
        SettingsOverviewValue(text = updateStatus)
    }
    Column(
        modifier = Modifier.focusGroup(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val orderedTags = buildList {
            updateModes.forEach { add(it.buttonTag()) }
            add(SettingsFocusTarget.CheckForUpdates.toTag())
            if (isInstallVisible) add(SettingsFocusTarget.InstallUpdate.toTag())
        }
        updateModes.forEach { mode ->
            val tag = mode.buttonTag()
            val focusTarget = SettingsFocusTarget.UpdateChannel(mode)
            val index = orderedTags.indexOf(tag)
            SettingsRowButton(
                title = mode.shortLabel(),
                description = mode.description(),
                value = "",
                radio = true,
                onClick = { onUpdateModeSelected(mode) },
                isSelected = mode == selectedUpdateMode,
                tag = tag,
                onFocused = { onActionFocused(focusTarget) },
                modifier = Modifier
                    .focusRequester(contentRequesters.getValue(tag))
                    .focusProperties {
                        left = railRequester
                        up = if (index > 0) {
                            contentRequesters.getValue(orderedTags[index - 1])
                        } else {
                            railRequester
                        }
                        down = if (index < orderedTags.lastIndex) {
                            contentRequesters.getValue(orderedTags[index + 1])
                        } else {
                            FocusRequester.Default
                        }
                    },
            )
        }
        val checkUpdatesTag = SettingsFocusTarget.CheckForUpdates.toTag()
        val checkUpdatesIndex = orderedTags.indexOf(checkUpdatesTag)
        SettingsRowButton(
            title = "Проверить обновления",
            description = "Запустить проверку сейчас.",
            value = if (isCheckingForUpdates) "Проверяем..." else "Проверить",
            onClick = onCheckForUpdatesClick,
            enabled = isCheckEnabled,
            tag = checkUpdatesTag,
            onFocused = { onActionFocused(SettingsFocusTarget.CheckForUpdates) },
            modifier = Modifier
                .focusRequester(contentRequesters.getValue(checkUpdatesTag))
                .focusProperties {
                    left = railRequester
                    up = if (checkUpdatesIndex > 0) {
                        contentRequesters.getValue(orderedTags[checkUpdatesIndex - 1])
                    } else {
                        railRequester
                    }
                    down = if (checkUpdatesIndex < orderedTags.lastIndex) {
                        contentRequesters.getValue(orderedTags[checkUpdatesIndex + 1])
                    } else {
                        FocusRequester.Default
                    }
                },
        )
        if (isInstallVisible) {
            val installUpdateTag = SettingsFocusTarget.InstallUpdate.toTag()
            val installUpdateIndex = orderedTags.indexOf(installUpdateTag)
            SettingsRowButton(
                title = if (isDownloadingUpdate) "Скачивается обновление" else "Скачать и установить",
                description = "Открыть APK новой версии.",
                value = when {
                    isDownloadingUpdate && installDownloadProgress != null -> "${installDownloadProgress}%"
                    isDownloadingUpdate -> "Скачивание…"
                    else -> "Установить"
                },
                onClick = onInstallUpdateClick,
                enabled = isInstallEnabled,
                tag = installUpdateTag,
                onFocused = { onActionFocused(SettingsFocusTarget.InstallUpdate) },
                modifier = Modifier
                    .focusRequester(contentRequesters.getValue(installUpdateTag))
                    .focusProperties {
                        left = railRequester
                        up = if (installUpdateIndex > 0) {
                            contentRequesters.getValue(orderedTags[installUpdateIndex - 1])
                        } else {
                            railRequester
                        }
                        down = if (installUpdateIndex < orderedTags.lastIndex) {
                            contentRequesters.getValue(orderedTags[installUpdateIndex + 1])
                        } else {
                            FocusRequester.Default
                        }
                    },
            )
        }
    }
}
private fun UpdateCheckMode.shortLabel(): String {
    return when (this) {
        UpdateCheckMode.MANUAL -> "Ручная проверка"
        UpdateCheckMode.QUIET_CHECK -> "Автоматическая проверка"
    }
}

private fun UpdateCheckMode.description(): String {
    return when (this) {
        UpdateCheckMode.MANUAL -> "Приложение не ищет обновления само."
        UpdateCheckMode.QUIET_CHECK -> "Тихо проверять и показывать доступную версию."
    }
}

private fun UpdateCheckMode.summaryLabel(): String {
    return when (this) {
        UpdateCheckMode.MANUAL -> "Ручная проверка"
        UpdateCheckMode.QUIET_CHECK -> "Автоматическая проверка"
    }
}

internal fun UpdateCheckMode.shortSummary(): String {
    return when (this) {
        UpdateCheckMode.MANUAL -> "Вручную"
        UpdateCheckMode.QUIET_CHECK -> "Авто"
    }
}

internal fun UpdateCheckMode.buttonTag(): String {
    return SettingsFocusTarget.UpdateChannel(this).toTag()
}
