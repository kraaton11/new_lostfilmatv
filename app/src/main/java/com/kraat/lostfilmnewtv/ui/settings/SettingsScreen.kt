package com.kraat.lostfilmnewtv.ui.settings

import androidx.activity.compose.BackHandler
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.kraat.lostfilmnewtv.ui.theme.HomeAccentGold
import com.kraat.lostfilmnewtv.ui.theme.HomeTextMuted
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.kraat.lostfilmnewtv.BuildConfig
import com.kraat.lostfilmnewtv.ui.home.requestFocusWhenReady
import com.kraat.lostfilmnewtv.playback.DEFAULT_TORRSERVE_BASE_URL
import com.kraat.lostfilmnewtv.playback.PlaybackQualityPreference
import com.kraat.lostfilmnewtv.playback.WatchedMarkingMode
import com.kraat.lostfilmnewtv.tvchannel.AndroidTvChannelMode
import com.kraat.lostfilmnewtv.ui.theme.BackgroundPrimary
import com.kraat.lostfilmnewtv.ui.theme.BackgroundSurface
import com.kraat.lostfilmnewtv.ui.theme.TextPrimary
import com.kraat.lostfilmnewtv.updates.UpdateCheckMode

@Composable
fun SettingsScreen(
    currentSection: SettingsSection = SettingsSection.PLAYBACK,
    onSectionSelected: (SettingsSection) -> Unit = {},
    onSectionBack: () -> Unit = {},
    onNavigateBack: () -> Unit = {},
    selectedQuality: PlaybackQualityPreference,
    onQualitySelected: (PlaybackQualityPreference) -> Unit,
    selectedUpdateMode: UpdateCheckMode,
    selectedChannelMode: AndroidTvChannelMode,
    isHomeFavoritesRailEnabled: Boolean = false,
    isHomeMoviesEnabled: Boolean = true,
    isHomeSeriesEnabled: Boolean = true,
    isHomeMenuLabelsEnabled: Boolean = true,
    selectedWatchedMarkingMode: WatchedMarkingMode = WatchedMarkingMode.AUTO,
    onWatchedMarkingModeSelected: (WatchedMarkingMode) -> Unit = {},
    torrServeBaseUrl: String = "",
    torrServeLogin: String = "",
    torrServePassword: String = "",
    isTorrServeConnected: Boolean = false,
    onTorrServeLoginChanged: (String) -> Unit = {},
    onTorrServePasswordChanged: (String) -> Unit = {},
    torrServeStatusText: String? = null,
    isCheckingTorrServe: Boolean = false,
    onTorrServeBaseUrlChanged: (String) -> Unit = {},
    onSaveTorrServeClick: () -> Unit = {},
    onResetTorrServeClick: () -> Unit = {},
    onCheckTorrServeClick: () -> Unit = {},
    lostFilmHost: String = "",
    lostFilmHostStatusText: String? = null,
    onLostFilmHostChanged: (String) -> Unit = {},
    onSaveLostFilmHostClick: () -> Unit = {},
    onResetLostFilmHostClick: () -> Unit = {},
    isAuthenticated: Boolean = false,
    onAuthClick: () -> Unit = {},
    installedVersionText: String,
    latestVersionText: String?,
    statusText: String?,
    isCheckingForUpdates: Boolean,
    isDownloadingUpdate: Boolean = false,
    installDownloadProgress: Int? = null,
    installUrl: String?,
    onUpdateModeSelected: (UpdateCheckMode) -> Unit,
    onChannelModeSelected: (AndroidTvChannelMode) -> Unit,
    onHomeFavoritesRailVisibilitySelected: (Boolean) -> Unit = {},
    onHomeMoviesVisibilitySelected: (Boolean) -> Unit = {},
    onHomeSeriesVisibilitySelected: (Boolean) -> Unit = {},
    onHomeMenuLabelsVisibilitySelected: (Boolean) -> Unit = {},
    onCheckForUpdatesClick: () -> Unit,
    onInstallUpdateClick: () -> Unit,
) {
    // Секции разработчика удалены вместе с Prowlarr и Диагностикой (f0ccbacd),
    // так что фильтровать больше нечего: список всегда полный.
    val visibleSections = SettingsSection.entries
    var selectedSectionName by rememberSaveable { mutableStateOf(currentSection.name) }
    LaunchedEffect(currentSection, visibleSections) {
        selectedSectionName = currentSection.takeIf { it in visibleSections }?.name ?: SettingsSection.PLAYBACK.name
    }
    val selectedSection = SettingsSection.fromName(selectedSectionName).takeIf { it in visibleSections }
        ?: SettingsSection.PLAYBACK
    val qualityOptions = remember { listOf(PlaybackQualityPreference.Q1080, PlaybackQualityPreference.Q720, PlaybackQualityPreference.Q480) }
    val updateModes = remember { listOf(UpdateCheckMode.QUIET_CHECK, UpdateCheckMode.MANUAL) }
    val channelModes = remember { listOf(AndroidTvChannelMode.ALL_NEW, AndroidTvChannelMode.UNWATCHED, AndroidTvChannelMode.DISABLED) }
    val contentScrollState = rememberScrollState()
    val railRequesters = remember(visibleSections) {
        visibleSections.associateWith { FocusRequester() }
    }
    val contentRequesters = remember {
        buildMap {
            qualityOptions.forEach { put(it.buttonTag(), FocusRequester()) }
            updateModes.forEach { put(it.buttonTag(), FocusRequester()) }
            channelModes.forEach { put(it.buttonTag(), FocusRequester()) }
            put(SettingsFocusTarget.WatchedMarkingToggle.toTag(), FocusRequester())
            put(SettingsFocusTarget.HomeFavoritesToggle.toTag(), FocusRequester())
            put(SettingsFocusTarget.HomeMoviesToggle.toTag(), FocusRequester())
            put(SettingsFocusTarget.HomeSeriesToggle.toTag(), FocusRequester())
            put(SettingsFocusTarget.HomeMenuLabelsToggle.toTag(), FocusRequester())
            put(SettingsFocusTarget.TorrServeBaseUrl.toTag(), FocusRequester())
            put(SettingsFocusTarget.TorrServeSave.toTag(), FocusRequester())
            put(SettingsFocusTarget.TorrServeCheck.toTag(), FocusRequester())
            put(SettingsFocusTarget.TorrServeReset.toTag(), FocusRequester())
            put(SettingsFocusTarget.LostFilmHost.toTag(), FocusRequester())
            put(SettingsFocusTarget.LostFilmHostSave.toTag(), FocusRequester())
            put(SettingsFocusTarget.LostFilmHostReset.toTag(), FocusRequester())
            put(SettingsFocusTarget.CheckForUpdates.toTag(), FocusRequester())
            put(SettingsFocusTarget.InstallUpdate.toTag(), FocusRequester())
            put(SettingsFocusTarget.AccountAuth.toTag(), FocusRequester())
            put(SettingsFocusTarget.AboutGitHubLink.toTag(), FocusRequester())
            put(SettingsFocusTarget.AboutTelegramLink.toTag(), FocusRequester())
        }
    }
    var contentHasFocus by remember { mutableStateOf(false) }
    var rememberedActionBySection by remember {
        mutableStateOf(
            mapOf(
                SettingsSection.PLAYBACK.name to SettingsFocusTarget.PlaybackQuality(selectedQuality),
                SettingsSection.HOME_SCREEN.name to SettingsFocusTarget.HomeFavoritesToggle,
                SettingsSection.CHANNEL.name to SettingsFocusTarget.ChannelMode(selectedChannelMode),
                SettingsSection.TORRSERVE.name to SettingsFocusTarget.TorrServeBaseUrl,
                SettingsSection.SERVER.name to SettingsFocusTarget.LostFilmHost,
                SettingsSection.UPDATES.name to SettingsFocusTarget.UpdateChannel(selectedUpdateMode),
                SettingsSection.ACCOUNT.name to SettingsFocusTarget.AccountAuth,
            ),
        )
    }

    LaunchedEffect(selectedSection) {
        contentScrollState.scrollTo(0)
    }
    LaunchedEffect(Unit) {
        requestFocusWhenReady(railRequesters.getValue(selectedSection))
    }
    BackHandler {
        if (contentHasFocus) {
            railRequesters.getValue(selectedSection).requestFocus()
            contentHasFocus = false
            onSectionBack()
        } else {
            onNavigateBack()
        }
    }

    val qualitySummary = selectedQuality.shortLabel()
    val watchedSummary = if (selectedWatchedMarkingMode == WatchedMarkingMode.AUTO) "Авто" else "Выкл"
    val playbackSummary = "$qualitySummary · ${if (selectedWatchedMarkingMode == WatchedMarkingMode.AUTO) "авто-отметка" else "ручная отметка"}"
    val updateSummary = updateSummary(
        selectedUpdateMode = selectedUpdateMode,
        statusText = statusText,
        isCheckingForUpdates = isCheckingForUpdates,
        isDownloadingUpdate = isDownloadingUpdate,
        installDownloadProgress = installDownloadProgress,
        installUrl = installUrl,
    )
    val channelSummary = selectedChannelMode.shortLabel()
    val homeScreenSummary = listOf(
        "Избранное ${if (isHomeFavoritesRailEnabled) "вкл" else "выкл"}",
        "фильмы ${if (isHomeMoviesEnabled) "вкл" else "выкл"}",
        "сериалы ${if (isHomeSeriesEnabled) "вкл" else "выкл"}",
        "подписи ${if (isHomeMenuLabelsEnabled) "вкл" else "выкл"}",
    ).joinToString(" · ")
    val usesDefaultTorrServeAddress =
        torrServeBaseUrl == DEFAULT_TORRSERVE_BASE_URL &&
            torrServeLogin.isEmpty() && torrServePassword.isEmpty()
    val hasTorrServeAddressError =
        !usesDefaultTorrServeAddress && !isCheckingTorrServe &&
            !isTorrServeConnected && torrServeStatusText != null
    val serverSummary = lostFilmHostStatusText?.takeIf { it.isNotBlank() }
        ?: lostFilmHost.ifBlank { "По умолчанию" }
    val accountSummary = if (isAuthenticated) "Вход выполнен" else "Без входа"
    val aboutSummary = BuildConfig.VERSION_NAME

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundPrimary)
            .padding(horizontal = 36.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Настройки",
            color = TextPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsSectionRail(
                selectedSection = selectedSection,
                playbackSummary = playbackSummary,
                updateSummary = updateSummary,
                channelSummary = channelSummary,
                homeScreenSummary = homeScreenSummary,
                isTorrServeConnected = isTorrServeConnected,
                hasTorrServeAddressError = hasTorrServeAddressError,
                serverSummary = serverSummary,
                accountSummary = accountSummary,
                aboutSummary = aboutSummary,
                sections = visibleSections,
                onSectionSelected = {
                    selectedSectionName = it.name
                    onSectionSelected(it)
                },
                focusRequesterForSection = { railRequesters.getValue(it) },
                contentRequesterForSection = { section ->
                    targetContentTag(
                        section = section,
                        selectedQuality = selectedQuality,
                        selectedUpdateMode = selectedUpdateMode,
                        selectedChannelMode = selectedChannelMode,
                        installUrl = installUrl,
                        rememberedActionBySection = rememberedActionBySection,
                    )?.let { contentRequesters.getValue(it.toTag()) } ?: railRequesters.getValue(section)
                },
                modifier = Modifier
                    .fillMaxWidth(0.37f)
                    .fillMaxHeight(),
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(BackgroundSurface, RoundedCornerShape(18.dp))
                    .onFocusChanged { contentHasFocus = it.hasFocus }
                    .padding(16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(contentScrollState),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when (selectedSection) {
                        SettingsSection.PLAYBACK -> {
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
                                            onFocused = {
                                                rememberedActionBySection = rememberedActionBySection + (
                                                    SettingsSection.PLAYBACK.name to SettingsFocusTarget.PlaybackQuality(quality)
                                                )
                                            },
                                            modifier = Modifier
                                                .focusRequester(contentRequesters.getValue(quality.buttonTag()))
                                                .focusProperties {
                                                    left = railRequesters.getValue(SettingsSection.PLAYBACK)
                                                    up = if (index > 0) {
                                                        contentRequesters.getValue(qualityOptions[index - 1].buttonTag())
                                                    } else {
                                                        railRequesters.getValue(SettingsSection.PLAYBACK)
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
                                        onFocused = {
                                            rememberedActionBySection = rememberedActionBySection + (
                                                SettingsSection.PLAYBACK.name to SettingsFocusTarget.WatchedMarkingToggle
                                            )
                                        },
                                        modifier = Modifier
                                            .focusRequester(contentRequesters.getValue(SettingsFocusTarget.WatchedMarkingToggle.toTag()))
                                            .focusProperties {
                                                left = railRequesters.getValue(SettingsSection.PLAYBACK)
                                                up = contentRequesters.getValue(qualityOptions.last().buttonTag())
                                                down = FocusRequester.Default
                                            },
                                    )
                                }
                            }
                        }

                        SettingsSection.HOME_SCREEN -> {
                            SettingsOptionsSection {
                                SettingsOverviewCard(
                                    title = "Главный экран",
                                    subtitle = "Вкладки и подписи в боковом меню.",
                                )
                                Column(modifier = Modifier.focusGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    SettingsRowButton(
                                        title = "Вкладка Избранное",
                                        description = "Показывает отдельную вкладку с сохраненными релизами.",
                                        value = "",
                                        toggle = true,
                                        onClick = {
                                            onHomeFavoritesRailVisibilitySelected(!isHomeFavoritesRailEnabled)
                                            contentRequesters.getValue(SettingsFocusTarget.HomeFavoritesToggle.toTag()).requestFocus()
                                        },
                                        isSelected = isHomeFavoritesRailEnabled,
                                        tag = SettingsFocusTarget.HomeFavoritesToggle.toTag(),
                                        onFocused = {
                                            rememberedActionBySection = rememberedActionBySection + (
                                                SettingsSection.HOME_SCREEN.name to SettingsFocusTarget.HomeFavoritesToggle
                                            )
                                        },
                                        modifier = Modifier
                                            .focusRequester(contentRequesters.getValue(SettingsFocusTarget.HomeFavoritesToggle.toTag()))
                                            .focusProperties {
                                                left = railRequesters.getValue(SettingsSection.HOME_SCREEN)
                                                up = railRequesters.getValue(SettingsSection.HOME_SCREEN)
                                                down = contentRequesters.getValue(SettingsFocusTarget.HomeMoviesToggle.toTag())
                                            },
                                    )
                                    SettingsRowButton(
                                        title = "Фильмы",
                                        description = "Показывает вкладку с фильмами LostFilm.",
                                        value = "",
                                        toggle = true,
                                        onClick = {
                                            onHomeMoviesVisibilitySelected(!isHomeMoviesEnabled)
                                            contentRequesters.getValue(SettingsFocusTarget.HomeMoviesToggle.toTag()).requestFocus()
                                        },
                                        isSelected = isHomeMoviesEnabled,
                                        tag = SettingsFocusTarget.HomeMoviesToggle.toTag(),
                                        onFocused = {
                                            rememberedActionBySection = rememberedActionBySection + (
                                                SettingsSection.HOME_SCREEN.name to SettingsFocusTarget.HomeMoviesToggle
                                            )
                                        },
                                        modifier = Modifier
                                            .focusRequester(contentRequesters.getValue(SettingsFocusTarget.HomeMoviesToggle.toTag()))
                                            .focusProperties {
                                                left = railRequesters.getValue(SettingsSection.HOME_SCREEN)
                                                up = contentRequesters.getValue(SettingsFocusTarget.HomeFavoritesToggle.toTag())
                                                down = contentRequesters.getValue(SettingsFocusTarget.HomeSeriesToggle.toTag())
                                            },
                                    )
                                    SettingsRowButton(
                                        title = "Сериалы",
                                        description = "Показывает вкладку общего каталога сериалов.",
                                        value = "",
                                        toggle = true,
                                        onClick = {
                                            onHomeSeriesVisibilitySelected(!isHomeSeriesEnabled)
                                            contentRequesters.getValue(SettingsFocusTarget.HomeSeriesToggle.toTag()).requestFocus()
                                        },
                                        isSelected = isHomeSeriesEnabled,
                                        tag = SettingsFocusTarget.HomeSeriesToggle.toTag(),
                                        onFocused = {
                                            rememberedActionBySection = rememberedActionBySection + (
                                                SettingsSection.HOME_SCREEN.name to SettingsFocusTarget.HomeSeriesToggle
                                            )
                                        },
                                        modifier = Modifier
                                            .focusRequester(contentRequesters.getValue(SettingsFocusTarget.HomeSeriesToggle.toTag()))
                                            .focusProperties {
                                                left = railRequesters.getValue(SettingsSection.HOME_SCREEN)
                                                up = contentRequesters.getValue(SettingsFocusTarget.HomeMoviesToggle.toTag())
                                                down = contentRequesters.getValue(SettingsFocusTarget.HomeMenuLabelsToggle.toTag())
                                            },
                                    )
                                    SettingsRowButton(
                                        title = "Подписи в меню",
                                        description = "Показывает текст рядом с иконками бокового меню.",
                                        value = "",
                                        toggle = true,
                                        onClick = {
                                            onHomeMenuLabelsVisibilitySelected(!isHomeMenuLabelsEnabled)
                                            contentRequesters.getValue(SettingsFocusTarget.HomeMenuLabelsToggle.toTag()).requestFocus()
                                        },
                                        isSelected = isHomeMenuLabelsEnabled,
                                        tag = SettingsFocusTarget.HomeMenuLabelsToggle.toTag(),
                                        onFocused = {
                                            rememberedActionBySection = rememberedActionBySection + (
                                                SettingsSection.HOME_SCREEN.name to SettingsFocusTarget.HomeMenuLabelsToggle
                                            )
                                        },
                                        modifier = Modifier
                                            .focusRequester(contentRequesters.getValue(SettingsFocusTarget.HomeMenuLabelsToggle.toTag()))
                                            .focusProperties {
                                                left = railRequesters.getValue(SettingsSection.HOME_SCREEN)
                                                up = contentRequesters.getValue(SettingsFocusTarget.HomeSeriesToggle.toTag())
                                                down = FocusRequester.Default
                                            },
                                    )
                                }
                            }
                        }

                        SettingsSection.CHANNEL -> {
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

                        SettingsSection.TORRSERVE -> {
                            SettingsOptionsSection {
                                SettingsOverviewCard(
                                    title = "TorrServe",
                                    subtitle = "Здесь проверяется указанный адрес. Для просмотра используются настройки приложения TorrServe.",
                                ) {
                                    torrServeStatusText?.let { SettingsOverviewValue(text = it) }
                                }
                                Column(modifier = Modifier.focusGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    SettingsTextField(
                                        value = torrServeBaseUrl,
                                        onValueChange = onTorrServeBaseUrlChanged,
                                        label = "Адрес TorrServe",
                                        tag = SettingsFocusTarget.TorrServeBaseUrl.toTag(),
                                        onFocused = {
                                            rememberedActionBySection = rememberedActionBySection + (
                                                SettingsSection.TORRSERVE.name to SettingsFocusTarget.TorrServeBaseUrl
                                            )
                                        },
                                        modifier = Modifier.focusRequester(contentRequesters.getValue(SettingsFocusTarget.TorrServeBaseUrl.toTag())),
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        SettingsTextField(
                                            value = torrServeLogin,
                                            onValueChange = onTorrServeLoginChanged,
                                            label = "Логин",
                                            modifier = Modifier.weight(1f),
                                            tag = "settings-torrserve-login",
                                        )
                                        SettingsTextField(
                                            value = torrServePassword,
                                            onValueChange = onTorrServePasswordChanged,
                                            label = "Пароль",
                                            modifier = Modifier.weight(1f),
                                            tag = "settings-torrserve-password",
                                            isPassword = true,
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        SettingsRowButton(
                                            title = "Сохранить",
                                            description = "Применить настройки и проверить подключение.",
                                            value = "",
                                            onClick = onSaveTorrServeClick,
                                            tag = SettingsFocusTarget.TorrServeSave.toTag(),
                                            onFocused = {
                                                rememberedActionBySection = rememberedActionBySection + (
                                                    SettingsSection.TORRSERVE.name to SettingsFocusTarget.TorrServeSave
                                                )
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .focusRequester(contentRequesters.getValue(SettingsFocusTarget.TorrServeSave.toTag())),
                                        )
                                        SettingsRowButton(
                                            title = "Сбросить",
                                            description = "Вернуть настройки по умолчанию.",
                                            value = "",
                                            onClick = onResetTorrServeClick,
                                            tag = SettingsFocusTarget.TorrServeReset.toTag(),
                                            onFocused = {
                                                rememberedActionBySection = rememberedActionBySection + (
                                                    SettingsSection.TORRSERVE.name to SettingsFocusTarget.TorrServeReset
                                                )
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .focusRequester(contentRequesters.getValue(SettingsFocusTarget.TorrServeReset.toTag())),
                                        )
                                    }
                                    SettingsRowButton(
                                        title = "Проверить подключение",
                                        value = if (isCheckingTorrServe) "Проверяем…" else "",
                                        onClick = onCheckTorrServeClick,
                                        enabled = !isCheckingTorrServe,
                                        tag = SettingsFocusTarget.TorrServeCheck.toTag(),
                                        onFocused = {
                                            rememberedActionBySection = rememberedActionBySection + (
                                                SettingsSection.TORRSERVE.name to SettingsFocusTarget.TorrServeCheck
                                            )
                                        },
                                        modifier = Modifier.focusRequester(contentRequesters.getValue(SettingsFocusTarget.TorrServeCheck.toTag())),
                                    )
                                }
                            }
                        }

                        SettingsSection.SERVER -> {
                            SettingsOptionsSection {
                                SettingsOverviewCard(
                                    title = "Адрес хоста",
                                    subtitle = "Адрес сервера LostFilm, к которому обращается приложение. По умолчанию https://www.lostfilm.today.",
                                ) {
                                    SettingsOverviewValue(text = lostFilmHostStatusText ?: "Сейчас: ${lostFilmHost.ifBlank { "по умолчанию" }}")
                                }
                                Column(modifier = Modifier.focusGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    SettingsTextField(
                                        value = lostFilmHost,
                                        onValueChange = onLostFilmHostChanged,
                                        label = "Адрес хоста",
                                        tag = SettingsFocusTarget.LostFilmHost.toTag(),
                                        onFocused = {
                                            rememberedActionBySection = rememberedActionBySection + (
                                                SettingsSection.SERVER.name to SettingsFocusTarget.LostFilmHost
                                            )
                                        },
                                        modifier = Modifier.focusRequester(contentRequesters.getValue(SettingsFocusTarget.LostFilmHost.toTag())),
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        SettingsRowButton(
                                            title = "Сохранить",
                                            description = "Применить указанный адрес хоста.",
                                            value = "Сохранить",
                                            onClick = onSaveLostFilmHostClick,
                                            tag = SettingsFocusTarget.LostFilmHostSave.toTag(),
                                            onFocused = {
                                                rememberedActionBySection = rememberedActionBySection + (
                                                    SettingsSection.SERVER.name to SettingsFocusTarget.LostFilmHostSave
                                                )
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .focusRequester(contentRequesters.getValue(SettingsFocusTarget.LostFilmHostSave.toTag())),
                                        )
                                        SettingsRowButton(
                                            title = "Сбросить",
                                            description = "Вернуть адрес по умолчанию (lostfilm.today).",
                                            value = "Сбросить",
                                            onClick = onResetLostFilmHostClick,
                                            tag = SettingsFocusTarget.LostFilmHostReset.toTag(),
                                            onFocused = {
                                                rememberedActionBySection = rememberedActionBySection + (
                                                    SettingsSection.SERVER.name to SettingsFocusTarget.LostFilmHostReset
                                                )
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .focusRequester(contentRequesters.getValue(SettingsFocusTarget.LostFilmHostReset.toTag())),
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
                                    installDownloadProgress = installDownloadProgress,
                                    installUrl = installUrl,
                                    updateModes = updateModes,
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

                        SettingsSection.ACCOUNT -> {
                            SettingsOptionsSection {
                                SettingsOverviewCard(
                                    title = "Аккаунт LostFilm",
                                    subtitle = "Вход нужен для Избранного и синхронизации просмотренного.",
                                ) {
                                    SettingsOverviewValue(text = "Статус: $accountSummary")
                                }
                                Column(modifier = Modifier.focusGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    SettingsRowButton(
                                        title = "Аккаунт",
                                        description = if (isAuthenticated) "Завершить текущую сессию." else "Войти через QR-код в браузере.",
                                        value = if (isAuthenticated) "Выйти" else "Войти",
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
                                val context = LocalContext.current
                                SettingsOverviewCard(
                                    title = "О приложении",
                                    subtitle = "Неофициальный клиент LostFilm для Android TV.",
                                ) {
                                    SettingsOverviewValue(text = "Версия: ${BuildConfig.VERSION_NAME} (сборка ${BuildConfig.VERSION_CODE})")
                                }
                                Column(modifier = Modifier.focusGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    val gitHubTag = SettingsFocusTarget.AboutGitHubLink.toTag()
                                    val telegramTag = SettingsFocusTarget.AboutTelegramLink.toTag()
                                    SettingsRowButton(
                                        title = "Исходный код на GitHub",
                                        description = "Репозиторий kraaton11/new_lostfilmatv",
                                        value = "Открыть",
                                        onClick = {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/kraaton11/new_lostfilmatv")).apply {
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                },
                                            )
                                        },
                                        tag = gitHubTag,
                                        onFocused = {
                                            rememberedActionBySection = rememberedActionBySection + (
                                                SettingsSection.ABOUT.name to SettingsFocusTarget.AboutGitHubLink
                                            )
                                        },
                                        modifier = Modifier
                                            .focusRequester(contentRequesters.getValue(gitHubTag))
                                            .focusProperties {
                                                left = railRequesters.getValue(SettingsSection.ABOUT)
                                                up = railRequesters.getValue(SettingsSection.ABOUT)
                                                down = contentRequesters.getValue(telegramTag)
                                            },
                                    )
                                    SettingsRowButton(
                                        title = "Telegram-канал",
                                        description = "t.me/lostfilmatv_new",
                                        value = "Открыть",
                                        onClick = {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/lostfilmatv_new")).apply {
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                },
                                            )
                                        },
                                        tag = telegramTag,
                                        onFocused = {
                                            rememberedActionBySection = rememberedActionBySection + (
                                                SettingsSection.ABOUT.name to SettingsFocusTarget.AboutTelegramLink
                                            )
                                        },
                                        modifier = Modifier
                                            .focusRequester(contentRequesters.getValue(telegramTag))
                                            .focusProperties {
                                                left = railRequesters.getValue(SettingsSection.ABOUT)
                                                up = contentRequesters.getValue(gitHubTag)
                                                down = FocusRequester.Default
                                            },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        Text(
            text = if (contentHasFocus) "OK  Выбрать     ←  К разделам     Назад  К разделам"
                else "→  К настройкам     Назад  Выйти",
            color = HomeTextMuted,
            fontSize = 13.sp,
        )
    }
}

enum class SettingsSection(
    val railTitle: String,
    val tag: String,
    val summaryTag: String,
) {
    PLAYBACK("Воспроизведение", "settings-section-playback", "settings-section-playback-summary"),
    HOME_SCREEN("Главный экран", "settings-section-home-screen", "settings-section-home-screen-summary"),
    TORRSERVE("TorrServe", "settings-section-torrserve", "settings-section-torrserve-summary"),
    SERVER("Зеркало LostFilm", "settings-section-server", "settings-section-server-summary"),
    ACCOUNT("Аккаунт", "settings-section-account", "settings-section-account-summary"),
    CHANNEL("Канал на главном экране", "settings-section-channel", "settings-section-channel-summary"),
    UPDATES("Обновления", "settings-section-updates", "settings-section-updates-summary"),
    ABOUT("О приложении", "settings-section-about", "settings-section-about-summary");

    companion object {
        fun fromName(name: String): SettingsSection {
            return entries.firstOrNull { it.name == name }
                ?: if (name == "QUALITY") PLAYBACK else PLAYBACK
        }
    }
}

@Composable
private fun SettingsSectionRail(
    selectedSection: SettingsSection,
    playbackSummary: String,
    updateSummary: String,
    channelSummary: String,
    homeScreenSummary: String,
    isTorrServeConnected: Boolean,
    hasTorrServeAddressError: Boolean,
    serverSummary: String,
    accountSummary: String,
    aboutSummary: String,
    sections: List<SettingsSection>,
    onSectionSelected: (SettingsSection) -> Unit,
    focusRequesterForSection: (SettingsSection) -> FocusRequester,
    contentRequesterForSection: (SettingsSection) -> FocusRequester,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(BackgroundSurface, RoundedCornerShape(18.dp))
            .padding(8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        sections.forEachIndexed { index, section ->
            when (section) {
                SettingsSection.PLAYBACK -> SettingsGroupLabel("ПРИЛОЖЕНИЕ")
                SettingsSection.TORRSERVE -> SettingsGroupLabel("ПОДКЛЮЧЕНИЯ")
                SettingsSection.CHANNEL -> SettingsGroupLabel("СИСТЕМА")
                else -> Unit
            }
            SettingsTvButton(
                text = section.railTitle,
                iconPath = section.iconPath(),
                summary = when (section) {
                    SettingsSection.PLAYBACK -> playbackSummary
                    SettingsSection.HOME_SCREEN -> homeScreenSummary
                    SettingsSection.CHANNEL -> channelSummary
                    SettingsSection.TORRSERVE -> when {
                        isTorrServeConnected -> "Подключён"
                        hasTorrServeAddressError -> "Ошибка проверки адреса"
                        else -> "Через внешний клиент"
                    }
                    SettingsSection.UPDATES -> updateSummary
                    SettingsSection.ACCOUNT -> accountSummary
                    SettingsSection.SERVER -> serverSummary
                    SettingsSection.ABOUT -> aboutSummary
                },
                summaryTag = section.summaryTag,
                summaryIndicatorColor = when {
                    section != SettingsSection.TORRSERVE -> null
                    isTorrServeConnected -> Color(0xFF59D58B)
                    hasTorrServeAddressError -> Color(0xFFFF636B)
                    else -> null
                },
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

private fun SettingsSection.iconPath(): String = when (this) {
    SettingsSection.PLAYBACK -> "M8 4 L20 12 L8 20 Z"
    SettingsSection.HOME_SCREEN -> "M3 10 L12 3 L21 10 V21 H3 Z"
    SettingsSection.TORRSERVE -> "M3 5 H21 V11 H3 Z M3 14 H21 V20 H3 Z"
    SettingsSection.SERVER -> "M3 12 A9 9 0 1 0 21 12 A9 9 0 1 0 3 12 M3 12 H21 M12 3 C7 6 7 18 12 21 C17 18 17 6 12 3"
    SettingsSection.ACCOUNT -> "M8 8 A4 4 0 1 0 16 8 A4 4 0 1 0 8 8 M3 21 C3 13 21 13 21 21"
    SettingsSection.CHANNEL -> "M3 4 H21 V17 H3 Z M8 21 H16"
    SettingsSection.UPDATES -> "M20 8 A9 9 0 1 0 21 14 M20 3 V9 H14"
    SettingsSection.ABOUT -> "M3 12 A9 9 0 1 0 21 12 A9 9 0 1 0 3 12 M12 11 V17 M12 7 V8"
}

@Composable
private fun SettingsGroupLabel(text: String, color: Color = HomeTextMuted) {
    Text(
        text = text,
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.4.sp,
        modifier = Modifier.padding(top = 4.dp, start = 8.dp),
    )
}

@Composable
private fun SettingsOptionsSection(
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
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

private fun targetContentTag(
    section: SettingsSection,
    selectedQuality: PlaybackQualityPreference,
    selectedUpdateMode: UpdateCheckMode,
    selectedChannelMode: AndroidTvChannelMode,
    installUrl: String?,
    rememberedActionBySection: Map<String, SettingsFocusTarget>,
): SettingsFocusTarget? {
    val rememberedTarget = rememberedActionBySection[section.name]
    return when {
        rememberedTarget != null && isActionAvailable(rememberedTarget, installUrl) -> rememberedTarget
        section == SettingsSection.PLAYBACK -> SettingsFocusTarget.PlaybackQuality(selectedQuality)
        section == SettingsSection.HOME_SCREEN -> SettingsFocusTarget.HomeFavoritesToggle
        section == SettingsSection.CHANNEL -> SettingsFocusTarget.ChannelMode(selectedChannelMode)
        section == SettingsSection.TORRSERVE -> SettingsFocusTarget.TorrServeBaseUrl
        section == SettingsSection.UPDATES -> SettingsFocusTarget.UpdateChannel(selectedUpdateMode)
        section == SettingsSection.ACCOUNT -> SettingsFocusTarget.AccountAuth
        section == SettingsSection.SERVER -> SettingsFocusTarget.LostFilmHost
        section == SettingsSection.ABOUT -> SettingsFocusTarget.AboutGitHubLink
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
    installDownloadProgress: Int?,
    installUrl: String?,
): String {
    return when {
        isDownloadingUpdate -> installDownloadProgress?.let { "Скачивание $it%" } ?: "Скачивание"
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

private fun PlaybackQualityPreference.description(): String {
    return when (this) {
        PlaybackQualityPreference.Q1080 -> "Максимальное качество, если доступно."
        PlaybackQualityPreference.Q720 -> "Оптимальный баланс качества и размера."
        PlaybackQualityPreference.Q480 -> "Меньше трафика и быстрее запуск."
    }
}

private fun PlaybackQualityPreference.buttonTag(): String {
    return SettingsFocusTarget.PlaybackQuality(this).toTag()
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

private fun UpdateCheckMode.shortSummary(): String {
    return when (this) {
        UpdateCheckMode.MANUAL -> "Вручную"
        UpdateCheckMode.QUIET_CHECK -> "Авто"
    }
}

private fun UpdateCheckMode.buttonTag(): String {
    return SettingsFocusTarget.UpdateChannel(this).toTag()
}

private fun AndroidTvChannelMode.shortLabel(): String {
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

private fun AndroidTvChannelMode.buttonTag(): String {
    return SettingsFocusTarget.ChannelMode(this).toTag()
}
