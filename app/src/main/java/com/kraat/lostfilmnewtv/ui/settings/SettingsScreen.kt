package com.kraat.lostfilmnewtv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kraat.lostfilmnewtv.BuildConfig
import com.kraat.lostfilmnewtv.playback.PlaybackQualityPreference
import com.kraat.lostfilmnewtv.tvchannel.AndroidTvChannelMode
import com.kraat.lostfilmnewtv.ui.theme.BackgroundPrimary
import com.kraat.lostfilmnewtv.ui.theme.BackgroundSurface
import com.kraat.lostfilmnewtv.ui.theme.HomePanelBorder
import com.kraat.lostfilmnewtv.ui.theme.HomePanelBorderFocus
import com.kraat.lostfilmnewtv.ui.theme.HomePanelSurface
import com.kraat.lostfilmnewtv.ui.theme.HomePanelSurfaceStrong
import com.kraat.lostfilmnewtv.ui.theme.HomeTextMuted
import com.kraat.lostfilmnewtv.ui.theme.TextPrimary
import com.kraat.lostfilmnewtv.updates.UpdateCheckMode

@Composable
fun SettingsScreen(
    selectedQuality: PlaybackQualityPreference,
    onQualitySelected: (PlaybackQualityPreference) -> Unit,
    selectedUpdateMode: UpdateCheckMode,
    selectedChannelMode: AndroidTvChannelMode,
    isHomeFavoritesRailEnabled: Boolean = false,
    isAuthenticated: Boolean = false,
    onAuthClick: () -> Unit = {},
    installedVersionText: String,
    latestVersionText: String?,
    statusText: String?,
    isCheckingForUpdates: Boolean,
    isDownloadingUpdate: Boolean = false,
    installUrl: String?,
    onUpdateModeSelected: (UpdateCheckMode) -> Unit,
    onChannelModeSelected: (AndroidTvChannelMode) -> Unit,
    onHomeFavoritesRailVisibilitySelected: (Boolean) -> Unit = {},
    onCheckForUpdatesClick: () -> Unit,
    onInstallUpdateClick: () -> Unit,
) {
    var selectedSectionName by rememberSaveable { mutableStateOf(SettingsSection.QUALITY.name) }
    val selectedSection = SettingsSection.valueOf(selectedSectionName)
    val contentScrollState = rememberScrollState()
    val railRequesters = remember {
        SettingsSection.entries.associateWith { FocusRequester() }
    }
    val contentRequesters = remember {
        mapOf(
            PlaybackQualityPreference.Q1080.buttonTag() to FocusRequester(),
            PlaybackQualityPreference.Q720.buttonTag() to FocusRequester(),
            PlaybackQualityPreference.Q480.buttonTag() to FocusRequester(),
            UpdateCheckMode.MANUAL.buttonTag() to FocusRequester(),
            UpdateCheckMode.QUIET_CHECK.buttonTag() to FocusRequester(),
            CHECK_UPDATES_TAG to FocusRequester(),
            INSTALL_UPDATE_TAG to FocusRequester(),
            AndroidTvChannelMode.ALL_NEW.buttonTag() to FocusRequester(),
            AndroidTvChannelMode.UNWATCHED.buttonTag() to FocusRequester(),
            AndroidTvChannelMode.DISABLED.buttonTag() to FocusRequester(),
            HOME_FAVORITES_SHOW_TAG to FocusRequester(),
            HOME_FAVORITES_HIDE_TAG to FocusRequester(),
            ACCOUNT_AUTH_ACTION_TAG to FocusRequester(),
        )
    }
    var rememberedActionBySection by rememberSaveable {
        mutableStateOf(
            mapOf(
                SettingsSection.QUALITY.name to selectedQuality.buttonTag(),
                SettingsSection.UPDATES.name to selectedUpdateMode.buttonTag(),
                SettingsSection.CHANNEL.name to selectedChannelMode.buttonTag(),
                SettingsSection.HOME_SCREEN.name to if (isHomeFavoritesRailEnabled) HOME_FAVORITES_SHOW_TAG else HOME_FAVORITES_HIDE_TAG,
                SettingsSection.ACCOUNT.name to ACCOUNT_AUTH_ACTION_TAG,
            ),
        )
    }

    LaunchedEffect(selectedSection) {
        contentScrollState.scrollTo(0)
    }
    LaunchedEffect(Unit) {
        railRequesters.getValue(selectedSection).requestFocus()
    }

    val qualitySummary = selectedQuality.label()
    val updateSummary = updateSummary(
        selectedUpdateMode = selectedUpdateMode,
        statusText = statusText,
        isCheckingForUpdates = isCheckingForUpdates,
        isDownloadingUpdate = isDownloadingUpdate,
        installUrl = installUrl,
    )
    val channelSummary = selectedChannelMode.label()
    val homeScreenSummary = if (isHomeFavoritesRailEnabled) "Показывать" else "Скрывать"
    val accountSummary = if (isAuthenticated) "Выполнен вход" else "Не выполнен вход"
    val aboutSummary = BuildConfig.VERSION_NAME

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
                qualitySummary = qualitySummary,
                updateSummary = updateSummary,
                channelSummary = channelSummary,
                homeScreenSummary = homeScreenSummary,
                accountSummary = accountSummary,
                aboutSummary = aboutSummary,
                onSectionSelected = { selectedSectionName = it.name },
                focusRequesterForSection = { railRequesters.getValue(it) },
                contentRequesterForSection = { section ->
                    val tag = targetContentTag(
                        section = section,
                        selectedQuality = selectedQuality,
                        selectedUpdateMode = selectedUpdateMode,
                        selectedChannelMode = selectedChannelMode,
                        installUrl = installUrl,
                        rememberedActionBySection = rememberedActionBySection,
                    )
                    if (tag.isBlank()) {
                        railRequesters.getValue(section)
                    } else {
                        contentRequesters.getValue(tag)
                    }
                },
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
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    when (selectedSection) {
                        SettingsSection.QUALITY -> {
                            SettingsOptionsSection {
                                SettingsOverviewCard(
                                    title = "Качество по умолчанию",
                                    subtitle = "Выбор качества для основного сценария просмотра.",
                                    modifier = Modifier.background(HomePanelSurfaceStrong, RoundedCornerShape(22.dp)),
                                ) {
                                    SettingsOverviewValue(text = "Сейчас выбрано: $qualitySummary")
                                }
                                Column(modifier = Modifier.focusGroup(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    PlaybackQualityPreference.entries.forEach { quality ->
                                        SettingsTvButton(
                                            text = quality.label(),
                                            onClick = { onQualitySelected(quality) },
                                            isSelected = quality == selectedQuality,
                                            tag = quality.buttonTag(),
                                            onFocused = {
                                                rememberedActionBySection = rememberedActionBySection + (
                                                    SettingsSection.QUALITY.name to quality.buttonTag()
                                                )
                                            },
                                            modifier = Modifier
                                                .focusRequester(contentRequesters.getValue(quality.buttonTag()))
                                                .focusProperties {
                                                    left = railRequesters.getValue(SettingsSection.QUALITY)
                                                },
                                        )
                                    }
                                }
                            }
                        }

                        SettingsSection.UPDATES -> {
                            SettingsOptionsSection {
                                UpdatesSectionContent(
                                    selectedUpdateMode = selectedUpdateMode,
                                    installedVersionText = installedVersionText,
                                    latestVersionText = latestVersionText,
                                    statusText = statusText,
                                    isCheckingForUpdates = isCheckingForUpdates,
                                    isDownloadingUpdate = isDownloadingUpdate,
                                    installUrl = installUrl,
                                    onUpdateModeSelected = onUpdateModeSelected,
                                    onCheckForUpdatesClick = onCheckForUpdatesClick,
                                    onInstallUpdateClick = onInstallUpdateClick,
                                    railRequester = railRequesters.getValue(SettingsSection.UPDATES),
                                    contentRequesters = contentRequesters,
                                    onActionFocused = { tag ->
                                        rememberedActionBySection = rememberedActionBySection + (
                                            SettingsSection.UPDATES.name to tag
                                        )
                                    },
                                )
                            }
                        }

                        SettingsSection.CHANNEL -> {
                            SettingsOptionsSection {
                                SettingsOverviewCard(
                                    title = "Канал Android TV",
                                    subtitle = "Что публиковать в системном канале Android TV.",
                                    modifier = Modifier.background(HomePanelSurfaceStrong, RoundedCornerShape(22.dp)),
                                ) {
                                    SettingsOverviewValue(text = "Сейчас выбрано: $channelSummary")
                                }
                                Column(modifier = Modifier.focusGroup(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    AndroidTvChannelMode.entries.forEach { mode ->
                                        SettingsTvButton(
                                            text = mode.label(),
                                            onClick = { onChannelModeSelected(mode) },
                                            isSelected = mode == selectedChannelMode,
                                            tag = mode.buttonTag(),
                                            onFocused = {
                                                rememberedActionBySection = rememberedActionBySection + (
                                                    SettingsSection.CHANNEL.name to mode.buttonTag()
                                                )
                                            },
                                            modifier = Modifier
                                                .focusRequester(contentRequesters.getValue(mode.buttonTag()))
                                                .focusProperties {
                                                    left = railRequesters.getValue(SettingsSection.CHANNEL)
                                                },
                                        )
                                    }
                                }
                            }
                        }

                        SettingsSection.HOME_SCREEN -> {
                            SettingsOptionsSection {
                                SettingsOverviewCard(
                                    title = "Главный экран",
                                    subtitle = "Дополнительные вкладки внутри главного экрана приложения.",
                                    modifier = Modifier.background(HomePanelSurfaceStrong, RoundedCornerShape(22.dp)),
                                ) {
                                    SettingsOverviewValue(text = "Вкладка Избранное")
                                    SettingsOverviewValue(
                                        text = if (isHomeFavoritesRailEnabled) {
                                            "Сейчас: показывать"
                                        } else {
                                            "Сейчас: скрывать"
                                        },
                                    )
                                }
                                Column(modifier = Modifier.focusGroup(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    SettingsTvButton(
                                        text = "Показывать",
                                        onClick = { onHomeFavoritesRailVisibilitySelected(true) },
                                        isSelected = isHomeFavoritesRailEnabled,
                                        tag = HOME_FAVORITES_SHOW_TAG,
                                        onFocused = {
                                            rememberedActionBySection = rememberedActionBySection + (
                                                SettingsSection.HOME_SCREEN.name to HOME_FAVORITES_SHOW_TAG
                                            )
                                        },
                                        modifier = Modifier
                                            .focusRequester(contentRequesters.getValue(HOME_FAVORITES_SHOW_TAG))
                                            .focusProperties {
                                                left = railRequesters.getValue(SettingsSection.HOME_SCREEN)
                                            },
                                    )
                                    SettingsTvButton(
                                        text = "Скрывать",
                                        onClick = { onHomeFavoritesRailVisibilitySelected(false) },
                                        isSelected = !isHomeFavoritesRailEnabled,
                                        tag = HOME_FAVORITES_HIDE_TAG,
                                        onFocused = {
                                            rememberedActionBySection = rememberedActionBySection + (
                                                SettingsSection.HOME_SCREEN.name to HOME_FAVORITES_HIDE_TAG
                                            )
                                        },
                                        modifier = Modifier
                                            .focusRequester(contentRequesters.getValue(HOME_FAVORITES_HIDE_TAG))
                                            .focusProperties {
                                                left = railRequesters.getValue(SettingsSection.HOME_SCREEN)
                                            },
                                    )
                                }
                            }
                        }

                        SettingsSection.ACCOUNT -> {
                            SettingsOptionsSection {
                                SettingsOverviewCard(
                                    title = "Аккаунт LostFilm",
                                    subtitle = "Вход нужен для Избранного и действий аккаунта.",
                                    modifier = Modifier.background(HomePanelSurfaceStrong, RoundedCornerShape(22.dp)),
                                ) {
                                    SettingsOverviewValue(
                                        text = if (isAuthenticated) {
                                            "Статус: выполнен вход"
                                        } else {
                                            "Статус: не выполнен вход"
                                        },
                                    )
                                }
                                Column(modifier = Modifier.focusGroup(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    SettingsTvButton(
                                        text = if (isAuthenticated) "Выйти" else "Войти",
                                        onClick = onAuthClick,
                                        tag = ACCOUNT_AUTH_ACTION_TAG,
                                        onFocused = {
                                            rememberedActionBySection = rememberedActionBySection + (
                                                SettingsSection.ACCOUNT.name to ACCOUNT_AUTH_ACTION_TAG
                                            )
                                        },
                                        modifier = Modifier
                                            .focusRequester(contentRequesters.getValue(ACCOUNT_AUTH_ACTION_TAG))
                                            .focusProperties {
                                                left = railRequesters.getValue(SettingsSection.ACCOUNT)
                                            },
                                    )
                                }
                            }
                        }

                        SettingsSection.ABOUT -> {
                            SettingsOptionsSection {
                                SettingsOverviewCard(
                                    title = "О приложении",
                                    subtitle = "Информация о версии и сборке.",
                                    modifier = Modifier.background(HomePanelSurfaceStrong, RoundedCornerShape(22.dp)),
                                ) {
                                    SettingsOverviewValue(text = "Версия: ${BuildConfig.VERSION_NAME}")
                                    SettingsOverviewValue(text = "Сборка: ${BuildConfig.VERSION_CODE}")
                                    SettingsOverviewValue(text = "Package: ${BuildConfig.APPLICATION_ID}")
                                    SettingsOverviewValue(text = "Min SDK: 26")
                                    SettingsOverviewValue(text = "Target SDK: 35")
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
    val tag: String,
    val summaryTag: String,
) {
    QUALITY("Качество", "settings-section-quality", "settings-section-quality-summary"),
    UPDATES("Обновления", "settings-section-updates", "settings-section-updates-summary"),
    CHANNEL("Канал Android TV", "settings-section-channel", "settings-section-channel-summary"),
    HOME_SCREEN("Главный экран", "settings-section-home-screen", "settings-section-home-screen-summary"),
    ACCOUNT("Аккаунт", "settings-section-account", "settings-section-account-summary"),
    ABOUT("О приложении", "settings-section-about", "settings-section-about-summary"),
}

@Composable
private fun SettingsSectionRail(
    selectedSection: SettingsSection,
    qualitySummary: String,
    updateSummary: String,
    channelSummary: String,
    homeScreenSummary: String,
    accountSummary: String,
    aboutSummary: String,
    onSectionSelected: (SettingsSection) -> Unit,
    focusRequesterForSection: (SettingsSection) -> FocusRequester,
    contentRequesterForSection: (SettingsSection) -> FocusRequester,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(HomePanelSurface, RoundedCornerShape(24.dp))
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val sections = SettingsSection.entries
        sections.forEachIndexed { index, section ->
            SettingsTvButton(
                text = section.railTitle,
                summary = when (section) {
                    SettingsSection.QUALITY -> qualitySummary
                    SettingsSection.UPDATES -> updateSummary
                    SettingsSection.CHANNEL -> channelSummary
                    SettingsSection.HOME_SCREEN -> homeScreenSummary
                    SettingsSection.ACCOUNT -> accountSummary
                    SettingsSection.ABOUT -> aboutSummary
                },
                summaryTag = section.summaryTag,
                isSelected = section == selectedSection,
                tag = section.tag,
                onClick = { onSectionSelected(section) },
                modifier = Modifier
                    .focusRequester(focusRequesterForSection(section))
                    .focusProperties {
                        right = contentRequesterForSection(section)
                        up = if (index > 0) focusRequesterForSection(sections[index - 1]) else focusRequesterForSection(sections.last())
                        down = if (index < sections.lastIndex) focusRequesterForSection(sections[index + 1]) else focusRequesterForSection(sections.first())
                    },
            )
        }
    }
}

@Composable
private fun SettingsOptionsSection(
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

@Composable
private fun UpdatesSectionContent(
    selectedUpdateMode: UpdateCheckMode,
    installedVersionText: String,
    latestVersionText: String?,
    statusText: String?,
    isCheckingForUpdates: Boolean,
    isDownloadingUpdate: Boolean,
    installUrl: String?,
    onUpdateModeSelected: (UpdateCheckMode) -> Unit,
    onCheckForUpdatesClick: () -> Unit,
    onInstallUpdateClick: () -> Unit,
    railRequester: FocusRequester,
    contentRequesters: Map<String, FocusRequester>,
    onActionFocused: (String) -> Unit,
) {
    val updateStatus = when {
        isDownloadingUpdate -> "Скачивание обновления…"
        else -> statusText ?: selectedUpdateMode.summaryLabel()
    }
    SettingsOverviewCard(
        title = "Обновления",
        subtitle = "Проверка и установка обновлений приложения.",
        modifier = Modifier.background(HomePanelSurface, RoundedCornerShape(22.dp)),
    ) {
        SettingsOverviewValue(text = "Установлена версия: $installedVersionText")
        SettingsOverviewValue(text = "Последняя версия: ${latestVersionText ?: "-"}")
        SettingsOverviewValue(text = updateStatus)
    }
    Column(
        modifier = Modifier.focusGroup(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        UpdateCheckMode.entries.forEach { mode ->
            SettingsTvButton(
                text = mode.label(),
                onClick = { onUpdateModeSelected(mode) },
                isSelected = mode == selectedUpdateMode,
                tag = mode.buttonTag(),
                onFocused = { onActionFocused(mode.buttonTag()) },
                modifier = Modifier
                    .focusRequester(contentRequesters.getValue(mode.buttonTag()))
                    .focusProperties {
                        left = railRequester
                    },
            )
        }
        SettingsTvButton(
            text = if (isCheckingForUpdates) "Проверяем..." else "Проверить обновления",
            onClick = onCheckForUpdatesClick,
            enabled = !isCheckingForUpdates,
            tag = CHECK_UPDATES_TAG,
            onFocused = { onActionFocused(CHECK_UPDATES_TAG) },
            modifier = Modifier
                .focusRequester(contentRequesters.getValue(CHECK_UPDATES_TAG))
                .focusProperties {
                    left = railRequester
                },
        )
        if (!installUrl.isNullOrBlank()) {
            SettingsTvButton(
                text = if (isDownloadingUpdate) "Скачивание…" else "Скачать и установить",
                onClick = onInstallUpdateClick,
                enabled = !isDownloadingUpdate,
                tag = INSTALL_UPDATE_TAG,
                onFocused = { onActionFocused(INSTALL_UPDATE_TAG) },
                modifier = Modifier
                    .focusRequester(contentRequesters.getValue(INSTALL_UPDATE_TAG))
                    .focusProperties {
                        left = railRequester
                    },
            )
        }
    }
}

private fun targetContentTag(
    section: SettingsSection,
    selectedQuality: PlaybackQualityPreference,
    selectedUpdateMode: UpdateCheckMode,
    selectedChannelMode: AndroidTvChannelMode,
    installUrl: String?,
    rememberedActionBySection: Map<String, String>,
): String {
    val rememberedTag = rememberedActionBySection[section.name]
    return when {
        rememberedTag != null && isActionAvailable(rememberedTag, installUrl) -> rememberedTag
        section == SettingsSection.QUALITY -> selectedQuality.buttonTag()
        section == SettingsSection.UPDATES -> selectedUpdateMode.buttonTag()
        section == SettingsSection.CHANNEL -> selectedChannelMode.buttonTag()
        section == SettingsSection.HOME_SCREEN -> if (rememberedActionBySection[section.name] != null) rememberedActionBySection[section.name]!! else HOME_FAVORITES_SHOW_TAG
        section == SettingsSection.ACCOUNT -> ACCOUNT_AUTH_ACTION_TAG
        section == SettingsSection.ABOUT -> ""
        else -> selectedQuality.buttonTag()
    }
}

private fun isActionAvailable(tag: String, installUrl: String?): Boolean {
    return tag != INSTALL_UPDATE_TAG || !installUrl.isNullOrBlank()
}

private fun updateSummary(
    selectedUpdateMode: UpdateCheckMode,
    statusText: String?,
    isCheckingForUpdates: Boolean,
    isDownloadingUpdate: Boolean,
    installUrl: String?,
): String {
    return when {
        isDownloadingUpdate -> "Скачивание"
        isCheckingForUpdates -> "Проверяем..."
        !statusText.isNullOrBlank() -> statusText
        !installUrl.isNullOrBlank() -> "Доступно обновление"
        else -> selectedUpdateMode.shortSummary()
    }
}

private fun PlaybackQualityPreference.label(): String {
    return when (this) {
        PlaybackQualityPreference.Q1080 -> "1080p"
        PlaybackQualityPreference.Q720 -> "720p"
        PlaybackQualityPreference.Q480 -> "480p / SD"
    }
}

private fun PlaybackQualityPreference.buttonTag(): String {
    return "settings-quality-${storageValue}"
}

private fun UpdateCheckMode.label(): String {
    return when (this) {
        UpdateCheckMode.MANUAL -> "Проверять вручную"
        UpdateCheckMode.QUIET_CHECK -> "Проверять тихо"
    }
}

private fun UpdateCheckMode.summaryLabel(): String {
    return when (this) {
        UpdateCheckMode.MANUAL -> "Ручная проверка"
        UpdateCheckMode.QUIET_CHECK -> "Тихая проверка"
    }
}

private fun UpdateCheckMode.shortSummary(): String {
    return when (this) {
        UpdateCheckMode.MANUAL -> "Вручную"
        UpdateCheckMode.QUIET_CHECK -> "Тихо"
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

private const val CHECK_UPDATES_TAG = "settings-action-check-updates"
private const val INSTALL_UPDATE_TAG = "settings-install-update"
private const val HOME_FAVORITES_SHOW_TAG = "settings-home-favorites-show"
private const val HOME_FAVORITES_HIDE_TAG = "settings-home-favorites-hide"
private const val ACCOUNT_AUTH_ACTION_TAG = "settings-account-auth-action"
