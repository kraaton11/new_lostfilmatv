package com.kraat.lostfilmnewtv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kraat.lostfilmnewtv.playback.PlaybackQualityPreference
import com.kraat.lostfilmnewtv.updates.UpdateCheckMode
import com.kraat.lostfilmnewtv.ui.theme.BackgroundPrimary
import com.kraat.lostfilmnewtv.ui.theme.TextPrimary

@Composable
fun SettingsScreen(
    selectedQuality: PlaybackQualityPreference,
    onQualitySelected: (PlaybackQualityPreference) -> Unit,
    selectedUpdateMode: UpdateCheckMode,
    installedVersionText: String,
    latestVersionText: String?,
    statusText: String?,
    isCheckingForUpdates: Boolean,
    installUrl: String?,
    onUpdateModeSelected: (UpdateCheckMode) -> Unit,
    onCheckForUpdatesClick: () -> Unit,
    onInstallUpdateClick: () -> Unit,
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundPrimary)
            .verticalScroll(scrollState)
            .padding(horizontal = 48.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(
            text = "Настройки",
            color = TextPrimary,
            fontSize = 30.sp,
            fontWeight = FontWeight.SemiBold,
        )
        SettingsGroup(title = "Качество по умолчанию") {
            PlaybackQualityPreference.entries.forEach { quality ->
                SettingsSelectionButton(
                    text = quality.label(),
                    isSelected = quality == selectedQuality,
                    tag = "settings-quality-${quality.storageValue}",
                    onClick = { onQualitySelected(quality) },
                )
            }
        }
        SettingsGroup(title = "Обновления") {
            UpdateCheckMode.entries.forEach { mode ->
                SettingsSelectionButton(
                    text = mode.label(),
                    isSelected = mode == selectedUpdateMode,
                    tag = mode.buttonTag(),
                    onClick = { onUpdateModeSelected(mode) },
                )
            }
            SettingsValueRow(
                text = "Установлена версия: $installedVersionText",
            )
            SettingsValueRow(
                text = "Последняя версия: ${latestVersionText ?: "-"}",
            )
            statusText?.let {
                SettingsValueRow(text = it)
            }
            Button(
                onClick = onCheckForUpdatesClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isCheckingForUpdates,
                colors = secondaryButtonColors(),
            ) {
                Text(
                    text = if (isCheckingForUpdates) "Проверяем..." else "Проверить обновления",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            if (!installUrl.isNullOrBlank()) {
                Button(
                    onClick = onInstallUpdateClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = secondaryButtonColors(),
                ) {
                    Text(
                        text = "Скачать и установить",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
        )
        content()
    }
}

@Composable
private fun SettingsSelectionButton(
    text: String,
    isSelected: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
            .semantics { selected = isSelected },
        colors = selectionButtonColors(isSelected),
    ) {
        Text(
            text = text,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun SettingsValueRow(text: String) {
    Text(
        text = text,
        color = TextPrimary,
        fontSize = 18.sp,
    )
}

@Composable
private fun selectionButtonColors(isSelected: Boolean) = ButtonDefaults.buttonColors(
    containerColor = if (isSelected) Color(0xFFF2C46E) else Color(0xFF16293C),
    contentColor = if (isSelected) Color(0xFF17120D) else TextPrimary,
)

@Composable
private fun secondaryButtonColors() = ButtonDefaults.buttonColors(
    containerColor = Color(0xFF16293C),
    contentColor = TextPrimary,
)

private fun PlaybackQualityPreference.label(): String {
    return when (this) {
        PlaybackQualityPreference.Q1080 -> "1080p"
        PlaybackQualityPreference.Q720 -> "720p"
        PlaybackQualityPreference.Q480 -> "480p / SD"
    }
}

private fun UpdateCheckMode.label(): String {
    return when (this) {
        UpdateCheckMode.MANUAL -> "Проверять вручную"
        UpdateCheckMode.QUIET_CHECK -> "Проверять тихо"
    }
}

private fun UpdateCheckMode.buttonTag(): String {
    return when (this) {
        UpdateCheckMode.MANUAL -> "settings-update-mode-manual"
        UpdateCheckMode.QUIET_CHECK -> "settings-update-mode-quiet"
    }
}
