package com.kraat.lostfilmnewtv.ui.settings

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.derivedStateOf
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
    currentSection: SettingsSection = SettingsSection.QUALITY,
    onSectionSelected: (SettingsSection) -> Unit = {},
    onSectionBack: () -> Unit = {},
    selectedQuality: PlaybackQualityPreference,
    onQualitySelected: (PlaybackQualityPreference) -> Unit,
    selectedUpdateMode: UpdateCheckMode,
    selectedChannelMode: AndroidTvChannelMode,
    isHomeFavoritesRailEnabled: Boolean = false,
    isHomeMenuLabelsEnabled: Boolean = true,
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
    onHomeMenuLabelsVisibilitySelected: (Boolean) -> Unit = {},
    onCheckForUpdatesClick: () -> Unit,
    onInstallUpdateClick: () -> Unit,
) {
    var selectedSectionName by rememberSaveable { mutableStateOf(currentSection.name) }
    LaunchedEffect(currentSection) {
        selectedSectionName = currentSection.name
    }
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
            SettingsFocusTarget.CheckForUpdates.toTag() to FocusRequester(),
            SettingsFocusTarget.InstallUpdate.toTag() to FocusRequester(),
            AndroidTvChannelMode.ALL_NEW.buttonTag() to FocusRequester(),
            AndroidTvChannelMode.UNWATCHED.buttonTag() to FocusRequester(),
            AndroidTvChannelMode.DISABLED.buttonTag() to FocusRequester(),
            SettingsFocusTarget.HomeFavoritesShow.toTag() to FocusRequester(),
            SettingsFocusTarget.HomeFavoritesHide.toTag() to FocusRequester(),
            SettingsFocusTarget.HomeMenuLabelsShow.toTag() to FocusRequester(),
            SettingsFocusTarget.HomeMenuLabelsHide.toTag() to FocusRequester(),
            SettingsFocusTarget.AccountAuth.toTag() to FocusRequester(),
            SettingsFocusTarget.WatchedMarking(WatchedMarkingMode.AUTO).toTag() to FocusRequester(),
            SettingsFocusTarget.WatchedMarking(WatchedMarkingMode.DISABLED).toTag() to FocusRequester(),
        )
    }
    var rememberedActionBySection by remember {
        mutableStateOf(
            mapOf(
                SettingsSection.QUALITY.name to SettingsFocusTarget.PlaybackQuality(selectedQuality),
                SettingsSection.UPDATES.name to SettingsFocusTarget.UpdateChannel(selectedUpdateMode),
                SettingsSection.CHANNEL.name to SettingsFocusTarget.ChannelMode(selectedChannelMode),
                SettingsSection.HOME_SCREEN.name to if (isHomeFavoritesRailEnabled) {
                    SettingsFocusTarget.HomeFavoritesShow
                } else {
                    SettingsFocusTarget.HomeFavoritesHide
                },
                SettingsSection.PLAYBACK.name to SettingsFocusTarget.WatchedMarking(selectedWatchedMarkingMode),
                SettingsSection.ACCOUNT.name to SettingsFocusTarget.AccountAuth,
            ),
        )
    }

    LaunchedEffect(selectedSection) {
        contentScrollState.scrollTo(0)
    }
    LaunchedEffect(Unit) {
        railRequesters.getValue(selectedSection).requestFocus()
    }
    BackHandler(enabled = selectedSection != SettingsSection.QUALITY) {
        selectedSectionName = SettingsSection.QUALITY.name
        onSectionBack()
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
    val homeScreenSummary = listOf(
        if (isHomeFavoritesRailEnabled) "Избранное: показывается" else "Избранное: скрыто",
        if (isHomeMenuLabelsEnabled) "Меню: иконки с надписями" else "Меню: только иконки",
    ).joinToString(" · ")
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
                onSectionSelected = {
                    selectedSectionName = it.name
                    onSectionSelected(it)
                },
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
                        if (tag == null) {
                            railRequesters.getValue(section)
                        } else {
                            contentRequesters.getValue(tag.toTag())
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
                                                    SettingsSection.QUALITY.name to SettingsFocusTarget.PlaybackQuality(quality)
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
                                                    SettingsSection.CHANNEL.name to SettingsFocusTarget.ChannelMode(mode)
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
                                    subtitle = "Вкладка «Избранное» и вид левого меню на главном экране.",
                                    modifier = Modifier.background(HomePanelSurfaceStrong, RoundedCornerShape(22.dp)),
                                ) {
                                    SettingsOverviewValue(
                                        text = homeScreenSummary,
                                    )
                                }
                                Column(modifier = Modifier.focusGroup(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    SettingsTvButton(
                                        text = "Показывать вкладку Избранное",
                                        onClick = { onHomeFavoritesRailVisibilitySelected(true) },
                                        isSelected = isHomeFavoritesRailEnabled,
                                        tag = SettingsFocusTarget.HomeFavoritesShow.toTag(),
                                        onFocused = {
                                            rememberedActionBySection = rememberedActionBySection + (
                                                SettingsSection.HOME_SCREEN.name to SettingsFocusTarget.HomeFavoritesShow
                                            )
                                        },
                                        modifier = Modifier
                                            .focusRequester(contentRequesters.getValue(SettingsFocusTarget.HomeFavoritesShow.toTag()))
                                            .focusProperties {
                                                left = railRequesters.getValue(SettingsSection.HOME_SCREEN)
                                                up = railRequesters.getValue(SettingsSection.HOME_SCREEN)
                                                down = contentRequesters.getValue(SettingsFocusTarget.HomeFavoritesHide.toTag())
                                            },
                                    )
                                    SettingsTvButton(
                                        text = "Скрывать вкладку Избранное",
                                        onClick = { onHomeFavoritesRailVisibilitySelected(false) },
                                        isSelected = !isHomeFavoritesRailEnabled,
                                        tag = SettingsFocusTarget.HomeFavoritesHide.toTag(),
                                        onFocused = {
                                            rememberedActionBySection = rememberedActionBySection + (
                                                SettingsSection.HOME_SCREEN.name to SettingsFocusTarget.HomeFavoritesHide
                                            )
                                        },
                                        modifier = Modifier
                                            .focusRequester(contentRequesters.getValue(SettingsFocusTarget.HomeFavoritesHide.toTag()))
                                            .focusProperties {
                                                left = railRequesters.getValue(SettingsSection.HOME_SCREEN)
                                                up = contentRequesters.getValue(SettingsFocusTarget.HomeFavoritesShow.toTag())
                                                down = contentRequesters.getValue(SettingsFocusTarget.HomeMenuLabelsShow.toTag())
                                            },
                                    )
                                    SettingsTvButton(
                                        text = "Меню: иконки с надписями",
                                        onClick = { onHomeMenuLabelsVisibilitySelected(true) },
                                        isSelected = isHomeMenuLabelsEnabled,
                                        tag = SettingsFocusTarget.HomeMenuLabelsShow.toTag(),
                                        onFocused = {
                                            rememberedActionBySection = rememberedActionBySection + (
                                                SettingsSection.HOME_SCREEN.name to SettingsFocusTarget.HomeMenuLabelsShow
                                            )
                                        },
                                        modifier = Modifier
                                            .focusRequester(contentRequesters.getValue(SettingsFocusTarget.HomeMenuLabelsShow.toTag()))
                                            .focusProperties {
                                                left = railRequesters.getValue(SettingsSection.HOME_SCREEN)
                                                up = contentRequesters.getValue(SettingsFocusTarget.HomeFavoritesHide.toTag())
                                                down = contentRequesters.getValue(SettingsFocusTarget.HomeMenuLabelsHide.toTag())
                                            },
                                    )
                                    SettingsTvButton(
                                        text = "Меню: только иконки",
                                        onClick = { onHomeMenuLabelsVisibilitySelected(false) },
                                        isSelected = !isHomeMenuLabelsEnabled,
                                        tag = SettingsFocusTarget.HomeMenuLabelsHide.toTag(),
                                        onFocused = {
                                            rememberedActionBySection = rememberedActionBySection + (
                                                SettingsSection.HOME_SCREEN.name to SettingsFocusTarget.HomeMenuLabelsHide
                                            )
                                        },
                                        modifier = Modifier
                                            .focusRequester(contentRequesters.getValue(SettingsFocusTarget.HomeMenuLabelsHide.toTag()))
                                            .focusProperties {
                                                left = railRequesters.getValue(SettingsSection.HOME_SCREEN)
                                                up = contentRequesters.getValue(SettingsFocusTarget.HomeMenuLabelsShow.toTag())
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
                                        tag = SettingsFocusTarget.WatchedMarking(WatchedMarkingMode.AUTO).toTag(),
                                        onFocused = {
                                            rememberedActionBySection = rememberedActionBySection + (
                                                SettingsSection.PLAYBACK.name to SettingsFocusTarget.WatchedMarking(WatchedMarkingMode.AUTO)
                                            )
                                        },
                                        modifier = Modifier
                                            .focusRequester(contentRequesters.getValue(SettingsFocusTarget.WatchedMarking(WatchedMarkingMode.AUTO).toTag()))
                                            .focusProperties {
                                                left = railRequesters.getValue(SettingsSection.PLAYBACK)
                                            },
                                    )
                                    SettingsTvButton(
                                        text = "Не отмечать",
                                        onClick = { onWatchedMarkingModeSelected(WatchedMarkingMode.DISABLED) },
                                        isSelected = selectedWatchedMarkingMode == WatchedMarkingMode.DISABLED,
                                        tag = SettingsFocusTarget.WatchedMarking(WatchedMarkingMode.DISABLED).toTag(),
                                        onFocused = {
                                            rememberedActionBySection = rememberedActionBySection + (
                                                SettingsSection.PLAYBACK.name to SettingsFocusTarget.WatchedMarking(WatchedMarkingMode.DISABLED)
                                            )
                                        },
                                        modifier = Modifier
                                            .focusRequester(contentRequesters.getValue(SettingsFocusTarget.WatchedMarking(WatchedMarkingMode.DISABLED).toTag()))
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
                                        tag = SettingsFocusTarget.AccountAuth.toTag(),
                                        onFocused = {
                                            rememberedActionBySection = rememberedActionBySection + (
                                                SettingsSection.ACCOUNT.name to SettingsFocusTarget.AccountAuth
                                            )
                                        },
                                        modifier = Modifier
                                            .focusRequester(contentRequesters.getValue(SettingsFocusTarget.AccountAuth.toTag()))
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

enum class SettingsSection(
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
                onFocused = { onSectionSelected(section) },
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
            add(SettingsFocusTarget.CheckForUpdates.toTag())
            if (isInstallVisible) add(SettingsFocusTarget.InstallUpdate.toTag())
        }
        UpdateCheckMode.entries.forEach { mode ->
            val tag = mode.buttonTag()
            val focusTarget = SettingsFocusTarget.UpdateChannel(mode)
            val index = orderedTags.indexOf(tag)
            SettingsTvButton(
                text = mode.label(),
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
        SettingsTvButton(
            text = if (isCheckingForUpdates) "Проверяем..." else "Проверить обновления",
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
            SettingsTvButton(
                text = if (isDownloadingUpdate) "Скачивание…" else "Скачать и установить",
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

private fun targetContentTag(
    section: SettingsSection,
    selectedQuality: PlaybackQualityPreference,
    selectedUpdateMode: UpdateCheckMode,
    selectedChannelMode: AndroidTvChannelMode,
    selectedWatchedMarkingMode: WatchedMarkingMode,
    installUrl: String?,
    rememberedActionBySection: Map<String, SettingsFocusTarget>,
): SettingsFocusTarget? {
    val rememberedTarget = rememberedActionBySection[section.name]
    return when {
        rememberedTarget != null && isActionAvailable(rememberedTarget, installUrl) -> rememberedTarget
        section == SettingsSection.QUALITY -> SettingsFocusTarget.PlaybackQuality(selectedQuality)
        section == SettingsSection.UPDATES -> SettingsFocusTarget.UpdateChannel(selectedUpdateMode)
        section == SettingsSection.CHANNEL -> SettingsFocusTarget.ChannelMode(selectedChannelMode)
        section == SettingsSection.HOME_SCREEN -> rememberedActionBySection[section.name] ?: SettingsFocusTarget.HomeFavoritesShow
        section == SettingsSection.PLAYBACK -> SettingsFocusTarget.WatchedMarking(selectedWatchedMarkingMode)
        section == SettingsSection.ACCOUNT -> SettingsFocusTarget.AccountAuth
        section == SettingsSection.ABOUT -> null
        else -> SettingsFocusTarget.PlaybackQuality(selectedQuality)
    }
}

private fun isActionAvailable(target: SettingsFocusTarget, installUrl: String?): Boolean {
    return target != SettingsFocusTarget.InstallUpdate || !installUrl.isNullOrBlank()
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
    return SettingsFocusTarget.PlaybackQuality(this).toTag()
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
    return SettingsFocusTarget.UpdateChannel(this).toTag()
}

private fun AndroidTvChannelMode.label(): String {
    return when (this) {
        AndroidTvChannelMode.ALL_NEW -> "Все новые релизы"
        AndroidTvChannelMode.UNWATCHED -> "Только непросмотренные"
        AndroidTvChannelMode.DISABLED -> "Не показывать (убрать с главного экрана TV)"
    }
}

private fun AndroidTvChannelMode.buttonTag(): String {
    return SettingsFocusTarget.ChannelMode(this).toTag()
}
