package com.kraat.lostfilmnewtv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kraat.lostfilmnewtv.playback.PlaybackQualityPreference
import com.kraat.lostfilmnewtv.tvchannel.AndroidTvChannelMode
import com.kraat.lostfilmnewtv.ui.theme.BackgroundPrimary
import com.kraat.lostfilmnewtv.ui.theme.BackgroundSurface
import com.kraat.lostfilmnewtv.ui.theme.TextPrimary
import com.kraat.lostfilmnewtv.updates.UpdateCheckMode

@Composable
fun SettingsScreen(
    selectedQuality: PlaybackQualityPreference,
    onQualitySelected: (PlaybackQualityPreference) -> Unit,
    selectedUpdateMode: UpdateCheckMode,
    selectedChannelMode: AndroidTvChannelMode,
    installedVersionText: String,
    latestVersionText: String?,
    statusText: String?,
    isCheckingForUpdates: Boolean,
    installUrl: String?,
    onUpdateModeSelected: (UpdateCheckMode) -> Unit,
    onChannelModeSelected: (AndroidTvChannelMode) -> Unit,
    onCheckForUpdatesClick: () -> Unit,
    onInstallUpdateClick: () -> Unit,
) {
    var selectedSectionName by rememberSaveable { mutableStateOf(SettingsSection.UPDATES.name) }
    val selectedSection = SettingsSection.valueOf(selectedSectionName)
    val contentScrollState = rememberScrollState()

    LaunchedEffect(selectedSection) {
        contentScrollState.scrollTo(0)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundPrimary)
            .padding(horizontal = 48.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(
            text = "Настройки",
            color = TextPrimary,
            fontSize = 30.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            SettingsSectionRail(
                selectedSection = selectedSection,
                onSectionSelected = { selectedSectionName = it.name },
                modifier = Modifier
                    .width(280.dp)
                    .fillMaxHeight(),
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(BackgroundSurface, RoundedCornerShape(24.dp))
                    .padding(24.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(contentScrollState),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    when (selectedSection) {
                        SettingsSection.QUALITY -> {
                            SettingsOptionsSection(title = selectedSection.panelTitle) {
                                PlaybackQualityPreference.entries.forEach { quality ->
                                    SettingsSelectionButton(
                                        text = quality.label(),
                                        isSelected = quality == selectedQuality,
                                        tag = "settings-quality-${quality.storageValue}",
                                        onClick = { onQualitySelected(quality) },
                                    )
                                }
                            }
                        }

                        SettingsSection.UPDATES -> {
                            UpdatesSectionContent(
                                selectedUpdateMode = selectedUpdateMode,
                                installedVersionText = installedVersionText,
                                latestVersionText = latestVersionText,
                                statusText = statusText,
                                isCheckingForUpdates = isCheckingForUpdates,
                                installUrl = installUrl,
                                onUpdateModeSelected = onUpdateModeSelected,
                                onCheckForUpdatesClick = onCheckForUpdatesClick,
                                onInstallUpdateClick = onInstallUpdateClick,
                            )
                        }

                        SettingsSection.CHANNEL -> {
                            SettingsOptionsSection(title = selectedSection.panelTitle) {
                                AndroidTvChannelMode.entries.forEach { mode ->
                                    SettingsSelectionButton(
                                        text = mode.label(),
                                        isSelected = mode == selectedChannelMode,
                                        tag = mode.buttonTag(),
                                        onClick = { onChannelModeSelected(mode) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class SettingsSection(
    val railTitle: String,
    val panelTitle: String,
    val tag: String,
) {
    QUALITY("Качество", "Качество по умолчанию", "settings-section-quality"),
    UPDATES("Обновления", "Обновления", "settings-section-updates"),
    CHANNEL("Канал Android TV", "Канал Android TV", "settings-section-channel"),
}

@Composable
private fun SettingsSectionRail(
    selectedSection: SettingsSection,
    onSectionSelected: (SettingsSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(BackgroundSurface, RoundedCornerShape(24.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingsSection.entries.forEach { section ->
            SettingsSelectionButton(
                text = section.railTitle,
                isSelected = section == selectedSection,
                tag = section.tag,
                onClick = { onSectionSelected(section) },
            )
        }
    }
}

@Composable
private fun SettingsOptionsSection(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
        )
        subtitle?.let {
            Text(
                text = it,
                color = TextPrimary.copy(alpha = 0.72f),
                fontSize = 16.sp,
            )
        }
        content()
    }
}

@Composable
private fun UpdatesSectionContent(
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
    SettingsOptionsSection(title = "Обновления") {
        UpdatesStatusCard(
            installedVersionText = installedVersionText,
            latestVersionText = latestVersionText,
            statusText = statusText,
        )
        Text(
            text = "Режим проверки",
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
        )
        UpdateCheckMode.entries.forEach { mode ->
            SettingsSelectionButton(
                text = mode.label(),
                isSelected = mode == selectedUpdateMode,
                tag = mode.buttonTag(),
                onClick = { onUpdateModeSelected(mode) },
            )
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

@Composable
private fun UpdatesStatusCard(
    installedVersionText: String,
    latestVersionText: String?,
    statusText: String?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF16293C), RoundedCornerShape(20.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SettingsValueRow(text = "Установлена версия: $installedVersionText")
        SettingsValueRow(text = "Последняя версия: ${latestVersionText ?: "-"}")
        statusText?.let { SettingsValueRow(text = it) }
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

private fun AndroidTvChannelMode.label(): String {
    return when (this) {
        AndroidTvChannelMode.ALL_NEW -> "Все новые релизы"
        AndroidTvChannelMode.UNWATCHED -> "Только непросмотренные"
        AndroidTvChannelMode.DISABLED -> "Не показывать"
    }
}

private fun AndroidTvChannelMode.buttonTag(): String {
    return when (this) {
        AndroidTvChannelMode.ALL_NEW -> "settings-tv-channel-all-new"
        AndroidTvChannelMode.UNWATCHED -> "settings-tv-channel-unwatched"
        AndroidTvChannelMode.DISABLED -> "settings-tv-channel-disabled"
    }
}
