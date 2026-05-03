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
import com.kraat.lostfilmnewtv.playback.WatchedMarkingMode
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
    selectedWatchedMarkingMode: WatchedMarkingMode = WatchedMarkingMode.AUTO,
    onWatchedMarkingModeSelected: (WatchedMarkingMode) -> Unit = {},
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
            WATCHED_MARKING_AUTO_TAG to FocusRequester(),
            WATCHED_MARKING_DISABLED_TAG to FocusRequester(),
        )
    }
    var rememberedActionBySection by rememberSaveable {
        mutableStateOf(
            mapOf(
                SettingsSection.QUALITY.name to selectedQuality.buttonTag(),
                SettingsSection.UPDATES.name to selectedUpdateMode.buttonTag(),
                SettingsSection.CHANNEL.name to selectedChannelMode.buttonTag(),
                SettingsSection.HOME_SCREEN.name to if (isHomeFavoritesRailEnabled) HOME_FAVORITES_SHOW_TAG else HOME_FAVORITES_HIDE_TAG,
                SettingsSection.PLAYBACK.name to if (selectedWatchedMarkingMode == WatchedMarkingMode.AUTO) WATCHED_MARKING_AUTO_TAG else WATCHED_MARKING_DISABLED_TAG,
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

    val qualitySummary = selectedQuality.shortLabel()
    val updateSummary = updateSummary(
        selectedUpdateMode = selectedUpdateMode,
        statusText = statusText,
        isCheckingForUpdates = isCheckingForUpdates,
        isDownloadingUpdate = isDownloadingUpdate,
        installUrl = installUrl,
    )
    val channelSummary = selectedChannelMode.label()
    val homeScreenSummary = if (isHomeFavoritesRailEnabled) "Избранное: показывается" else "Избранное: скрыто"
    val watchedSummary = if (selectedWatchedMarkingMode == WatchedMarkingMode.AUTO) "Автоматически" else "Не отмечать"
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
                watchedSummary = watchedSummary,
                accountSummary = accountSummary,
                aboutSummary = aboutSummary,
                onSectionSelected = { selectedSectionName = it.name },
                focusRequesterForSection = { railRequesters.getValue(it) },
                contentRequesterForSection = { section ->
                    if (section == SettingsSection.ABOUT) {
                        railRequesters.getValue(section)
                    } else {
                        val tag = targetContentTag(
                            section = section,
                            selectedQuality = selectedQuality,
                            selectedUpdateMode = selectedUpdateMode,
                            selectedChannelMode = selectedChannelMode,
                            selectedWatchedMarkingMode = selectedWatchedMarkingMode,
                            installUrl = installUrl,
                            rememberedActionBySection = rememberedActionBySection,
                        )
                        if (tag.isBlank()) {
                            railRequesters.getValue(section)
                        } else {
                            contentRequesters.getValue(tag)
                        }
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
                                    title = "Качество видео",
                                    subtitle = "Применяется при запуске торрента. Если выбранное качество недоступно — используется ближайшее.",
                                    modifier = Modifier.background(HomePanelSurfaceStrong, RoundedCornerShape(22.dp)),
                                ) {
                                    SettingsOverviewValue(text = "Сейчас выбрано: ${selectedQuality.shortLabel()}")
                                }
                                val qualityOptions = PlaybackQualityPreference.entries
                                Column(modifier = Modifier.focusGroup(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    qualityOptions.forEachIndexed { index, quality ->
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
                                                    up = if (index > 0) {
                                                        contentRequesters.getValue(qualityOptions[index - 1].buttonTag())
                                                    } else {
                                                        railRequesters.getValue(SettingsSection.QUALITY)
                                                    }
                                                    down = if (index < qualityOptions.lastIndex) {
                                                        contentRequesters.getValue(qualityOptions[index + 1].buttonTag())
                                                    } else {
                                                        FocusRequester.Default
                                                    }
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
                                    subtitle = "Карточки на главном экране Android TV (вне приложения). Можно показывать все новинки или только непросмотренные.",
                                    modifier = Modifier.background(HomePanelSurfaceStrong, RoundedCornerShape(22.dp)),
                                ) {
                                    SettingsOverviewValue(text = "Сейчас выбрано: $channelSummary")
                                }
                                val channelModes = AndroidTvChannelMode.entries
                                Column(modifier = Modifier.focusGroup(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    channelModes.forEachIndexed { index, mode ->
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
                                                    up = if (index > 0) {
                                                        contentRequesters.getValue(channelModes[index - 1].buttonTag())
                                                    } else {
                                                        railRequesters.getValue(SettingsSection.CHANNEL)
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

                        SettingsSection.HOME_SCREEN -> {
                            SettingsOptionsSection {
                                SettingsOverviewCard(
                                    title = "Главный экран",
                                    subtitle = "Вкладка «Избранное» — быстрый доступ к сериалам из вашего списка. Требует входа в аккаунт.",
                                    modifier = Modifier.background(HomePanelSurfaceStrong, RoundedCornerShape(22.dp)),
                                ) {
                                    SettingsOverviewValue(
                                        text = if (isHomeFavoritesRailEnabled) {
                                            "Сейчас: вкладка Избранное показывается"
                                        } else {
                                            "Сейчас: вкладка Избранное скрыта"
                                        },
                                    )
                                }
                                Column(modifier = Modifier.focusGroup(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    SettingsTvButton(
                                        text = "Показывать вкладку Избранное",
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
                                                up = railRequesters.getValue(SettingsSection.HOME_SCREEN)
                                                down = contentRequesters.getValue(HOME_FAVORITES_HIDE_TAG)
                                            },
                                    )
                                    SettingsTvButton(
                                        text = "Скрывать вкладку Избранное",
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
                                                up = contentRequesters.getValue(HOME_FAVORITES_SHOW_TAG)
                                                down = FocusRequester.Default
                                            },
                                    )
                                }
                            }
                        }

                        SettingsSection.PLAYBACK -> {
                            SettingsOptionsSection {
                                SettingsOverviewCard(
                                    title = "Отметка просмотренного",
                                    subtitle = "Эпизод помечается просмотренным сразу при запуске воспроизведения. Синхронизируется с аккаунтом LostFilm.",
                                    modifier = Modifier.background(HomePanelSurfaceStrong, RoundedCornerShape(22.dp)),
                                ) {
                                    SettingsOverviewValue(text = "Сейчас: $watchedSummary")
                                }
                                Column(modifier = Modifier.focusGroup(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    SettingsTvButton(
                                        text = "Отмечать автоматически",
                                        onClick = { onWatchedMarkingModeSelected(WatchedMarkingMode.AUTO) },
                                        isSelected = selectedWatchedMarkingMode == WatchedMarkingMode.AUTO,
                                        tag = WATCHED_MARKING_AUTO_TAG,
                                        onFocused = {
                                            rememberedActionBySection = rememberedActionBySection + (
                                                SettingsSection.PLAYBACK.name to WATCHED_MARKING_AUTO_TAG
                                            )
                                        },
                                        modifier = Modifier
                                            .focusRequester(contentRequesters.getValue(WATCHED_MARKING_AUTO_TAG))
                                            .focusProperties {
                                                left = railRequesters.getValue(SettingsSection.PLAYBACK)
                                            },
                                    )
                                    SettingsTvButton(
                                        text = "Не отмечать",
                                        onClick = { onWatchedMarkingModeSelected(WatchedMarkingMode.DISABLED) },
                                        isSelected = selectedWatchedMarkingMode == WatchedMarkingMode.DISABLED,
                                        tag = WATCHED_MARKING_DISABLED_TAG,
                                        onFocused = {
                                            rememberedActionBySection = rememberedActionBySection + (
                                                SettingsSection.PLAYBACK.name to WATCHED_MARKING_DISABLED_TAG
                                            )
                                        },
                                        modifier = Modifier
                                            .focusRequester(contentRequesters.getValue(WATCHED_MARKING_DISABLED_TAG))
                                            .focusProperties {
                                                left = railRequesters.getValue(SettingsSection.PLAYBACK)
                                            },
                                    )
                                }
                            }
                        }

                        SettingsSection.ACCOUNT -> {
                            SettingsOptionsSection {
                                SettingsOverviewCard(
                                    title = "Аккаунт LostFilm",
                                    subtitle = "Войдите через браузер по QR-коду. После входа станет доступно Избранное и синхронизация просмотренного.",
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
                                                up = railRequesters.getValue(SettingsSection.ACCOUNT)
                                                down = FocusRequester.Default
                                            },
                                    )
                                }
                            }
                        }

                        SettingsSection.ABOUT -> {
                            SettingsOptionsSection {
                                SettingsOverviewCard(
                                    title = "О приложении",
                                    subtitle = "Неофициальный клиент LostFilm для Android TV.",
                                    modifier = Modifier.background(HomePanelSurfaceStrong, RoundedCornerShape(22.dp)),
                                ) {
                                    SettingsOverviewValue(text = "Версия: ${BuildConfig.VERSION_NAME} (сборка ${BuildConfig.VERSION_CODE})")
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
    PLAYBACK("Воспроизведение", "settings-section-playback", "settings-section-playback-summary"),
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
    watchedSummary: String,
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
                    SettingsSection.PLAYBACK -> watchedSummary
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
        subtitle = "Приложение обновляется вручную через GitHub. Выберите режим проверки и при необходимости установите новую версию.",
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
        val orderedTags = buildList {
            UpdateCheckMode.entries.forEach { add(it.buttonTag()) }
            add(CHECK_UPDATES_TAG)
            if (!installUrl.isNullOrBlank()) add(INSTALL_UPDATE_TAG)
        }
        UpdateCheckMode.entries.forEach { mode ->
            val tag = mode.buttonTag()
            val index = orderedTags.indexOf(tag)
            SettingsTvButton(
                text = mode.label(),
                onClick = { onUpdateModeSelected(mode) },
                isSelected = mode == selectedUpdateMode,
                tag = tag,
                onFocused = { onActionFocused(tag) },
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
        val checkUpdatesIndex = orderedTags.indexOf(CHECK_UPDATES_TAG)
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
        if (!installUrl.isNullOrBlank()) {
            val installUpdateIndex = orderedTags.indexOf(INSTALL_UPDATE_TAG)
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

private fun targetContentTag(
    section: SettingsSection,
    selectedQuality: PlaybackQualityPreference,
    selectedUpdateMode: UpdateCheckMode,
    selectedChannelMode: AndroidTvChannelMode,
    selectedWatchedMarkingMode: WatchedMarkingMode,
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
        section == SettingsSection.PLAYBACK -> if (selectedWatchedMarkingMode == WatchedMarkingMode.AUTO) WATCHED_MARKING_AUTO_TAG else WATCHED_MARKING_DISABLED_TAG
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

private fun PlaybackQualityPreference.shortLabel(): String {
    return when (this) {
        PlaybackQualityPreference.Q1080 -> "1080p"
        PlaybackQualityPreference.Q720 -> "720p"
        PlaybackQualityPreference.Q480 -> "480p"
    }
}

private fun PlaybackQualityPreference.label(): String {
    return when (this) {
        PlaybackQualityPreference.Q1080 -> "1080p — Максимальное"
        PlaybackQualityPreference.Q720 -> "720p — Оптимальное (рекомендуется)"
        PlaybackQualityPreference.Q480 -> "480p — Экономия трафика"
    }
}

private fun PlaybackQualityPreference.buttonTag(): String {
    return "settings-quality-${storageValue}"
}

private fun UpdateCheckMode.label(): String {
    return when (this) {
        UpdateCheckMode.MANUAL -> "Проверять вручную — обновления не ищутся автоматически"
        UpdateCheckMode.QUIET_CHECK -> "Проверять автоматически — уведомление при наличии новой версии"
    }
}

private fun UpdateCheckMode.summaryLabel(): String {
    return when (this) {
        UpdateCheckMode.MANUAL -> "Ручная проверка"
        UpdateCheckMode.QUIET_CHECK -> "Автоматически"
    }
}

private fun UpdateCheckMode.shortSummary(): String {
    return when (this) {
        UpdateCheckMode.MANUAL -> "Вручную"
        UpdateCheckMode.QUIET_CHECK -> "Авто"
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
        AndroidTvChannelMode.DISABLED -> "Не показывать (убрать с главного экрана TV)"
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
private const val WATCHED_MARKING_AUTO_TAG = "settings-watched-marking-auto"
private const val WATCHED_MARKING_DISABLED_TAG = "settings-watched-marking-disabled"
