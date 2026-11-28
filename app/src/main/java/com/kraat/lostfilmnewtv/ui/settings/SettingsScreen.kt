package com.kraat.lostfilmnewtv.ui.settings

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kraat.lostfilmnewtv.BuildConfig
import com.kraat.lostfilmnewtv.data.parser.LostFilmBaseUrl
import com.kraat.lostfilmnewtv.playback.DEFAULT_TORRSERVE_BASE_URL
import com.kraat.lostfilmnewtv.playback.PlaybackQualityPreference
import com.kraat.lostfilmnewtv.playback.WatchedMarkingMode
import com.kraat.lostfilmnewtv.tvchannel.AndroidTvChannelMode
import com.kraat.lostfilmnewtv.ui.home.requestFocusWhenReady
import com.kraat.lostfilmnewtv.ui.theme.AppTheme
import com.kraat.lostfilmnewtv.ui.theme.BackgroundPrimary
import com.kraat.lostfilmnewtv.ui.theme.BackgroundSurface
import com.kraat.lostfilmnewtv.ui.theme.HomeTextMuted
import com.kraat.lostfilmnewtv.ui.theme.LocalLostFilmColors
import com.kraat.lostfilmnewtv.ui.theme.TextPrimary
import com.kraat.lostfilmnewtv.updates.UpdateCheckMode

@Composable
fun SettingsScreen(
    currentSection: SettingsSection = SettingsSection.PLAYBACK,
    selectedQuality: PlaybackQualityPreference,
    selectedUpdateMode: UpdateCheckMode,
    selectedChannelMode: AndroidTvChannelMode,
    isHomeFavoritesRailEnabled: Boolean = false,
    isHomeMoviesEnabled: Boolean = true,
    isHomeSeriesEnabled: Boolean = true,
    isHomeMenuLabelsEnabled: Boolean = true,
    selectedWatchedMarkingMode: WatchedMarkingMode = WatchedMarkingMode.AUTO,
    torrServeBaseUrl: String = "",
    torrServeLogin: String = "",
    torrServePassword: String = "",
    isTorrServeConnected: Boolean = false,
    isTorrServeClientAvailable: Boolean? = null,
    torrServeStatusText: String? = null,
    isCheckingTorrServe: Boolean = false,
    lostFilmHost: String = "",
    lostFilmHostStatusText: String? = null,
    activeLostFilmHost: String = LostFilmBaseUrl.DEFAULT,
    isCheckingLostFilmHost: Boolean = false,
    isLostFilmAutomaticMirror: Boolean = false,
    isAuthenticated: Boolean = false,
    installedVersionText: String,
    latestVersionText: String?,
    statusText: String?,
    isCheckingForUpdates: Boolean,
    isDownloadingUpdate: Boolean = false,
    installDownloadProgress: Int? = null,
    installUrl: String?,
    actions: SettingsActions,
    selectedTheme: AppTheme = AppTheme.CLASSIC,
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
            AppTheme.entries.forEach { put(SettingsFocusTarget.Theme(it).toTag(), FocusRequester()) }
            channelModes.forEach { put(it.buttonTag(), FocusRequester()) }
            WatchedMarkingMode.entries.forEach { put(SettingsFocusTarget.WatchedMarking(it).toTag(), FocusRequester()) }
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
            put(SettingsFocusTarget.LostFilmHostCheck.toTag(), FocusRequester())
            put(SettingsFocusTarget.LostFilmAutomaticMirror.toTag(), FocusRequester())
            LostFilmBaseUrl.selectableMirrors.forEach { url ->
                put(SettingsFocusTarget.LostFilmMirror(url).toTag(), FocusRequester())
            }
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
                SettingsSection.APPEARANCE.name to SettingsFocusTarget.Theme(selectedTheme),
                SettingsSection.CHANNEL.name to SettingsFocusTarget.ChannelMode(selectedChannelMode),
                SettingsSection.TORRSERVE.name to SettingsFocusTarget.TorrServeBaseUrl,
                SettingsSection.SERVER.name to SettingsFocusTarget.LostFilmHostCheck,
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
            actions.onSectionBack()
        } else {
            actions.onNavigateBack()
        }
    }

    val qualitySummary = selectedQuality.shortLabel()
    val watchedSummary = when (selectedWatchedMarkingMode) {
        WatchedMarkingMode.AUTO -> "авто-отметка"
        WatchedMarkingMode.ASK_AFTER_RETURN -> "спросить после возврата"
        WatchedMarkingMode.DISABLED -> "ручная отметка"
    }
    val playbackSummary = "$qualitySummary · $watchedSummary"
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
                appearanceSummary = selectedTheme.label(),
                homeScreenSummary = homeScreenSummary,
                isTorrServeConnected = isTorrServeConnected,
                hasTorrServeAddressError = hasTorrServeAddressError,
                isTorrServeClientAvailable = isTorrServeClientAvailable,
                serverSummary = serverSummary,
                accountSummary = accountSummary,
                aboutSummary = aboutSummary,
                sections = visibleSections,
                onSectionSelected = {
                    selectedSectionName = it.name
                    actions.onSectionSelected(it)
                },
                focusRequesterForSection = { railRequesters.getValue(it) },
                contentRequesterForSection = { section ->
                    targetContentTag(
                        section = section,
                        selectedQuality = selectedQuality,
                        selectedUpdateMode = selectedUpdateMode,
                        selectedChannelMode = selectedChannelMode,
                        selectedTheme = selectedTheme,
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
                    val rememberAction: (SettingsFocusTarget) -> Unit = { target ->
                        rememberedActionBySection = rememberedActionBySection + (selectedSection.name to target)
                    }
                    when (selectedSection) {
                        SettingsSection.PLAYBACK -> PlaybackSettingsSection(
                            selectedQuality = selectedQuality,
                            onQualitySelected = actions.onQualitySelected,
                            selectedWatchedMarkingMode = selectedWatchedMarkingMode,
                            onWatchedMarkingModeSelected = actions.onWatchedMarkingModeSelected,
                            qualityOptions = qualityOptions,
                            railRequester = railRequesters.getValue(SettingsSection.PLAYBACK),
                            contentRequesters = contentRequesters,
                            onActionFocused = rememberAction,
                        )

                        SettingsSection.HOME_SCREEN -> HomeScreenSettingsSection(
                            isHomeFavoritesRailEnabled = isHomeFavoritesRailEnabled,
                            isHomeMoviesEnabled = isHomeMoviesEnabled,
                            isHomeSeriesEnabled = isHomeSeriesEnabled,
                            isHomeMenuLabelsEnabled = isHomeMenuLabelsEnabled,
                            onHomeFavoritesRailVisibilitySelected = actions.onHomeFavoritesRailVisibilitySelected,
                            onHomeMoviesVisibilitySelected = actions.onHomeMoviesVisibilitySelected,
                            onHomeSeriesVisibilitySelected = actions.onHomeSeriesVisibilitySelected,
                            onHomeMenuLabelsVisibilitySelected = actions.onHomeMenuLabelsVisibilitySelected,
                            railRequester = railRequesters.getValue(SettingsSection.HOME_SCREEN),
                            contentRequesters = contentRequesters,
                            onActionFocused = rememberAction,
                        )

                        SettingsSection.APPEARANCE -> AppearanceSettingsSection(
                            selectedTheme = selectedTheme,
                            onThemeSelected = actions.onThemeSelected,
                            railRequester = railRequesters.getValue(SettingsSection.APPEARANCE),
                            contentRequesters = contentRequesters,
                            onActionFocused = rememberAction,
                        )

                        SettingsSection.CHANNEL -> ChannelSettingsSection(
                            selectedChannelMode = selectedChannelMode,
                            onChannelModeSelected = actions.onChannelModeSelected,
                            channelModes = channelModes,
                            railRequester = railRequesters.getValue(SettingsSection.CHANNEL),
                            contentRequesters = contentRequesters,
                            onActionFocused = rememberAction,
                        )

                        SettingsSection.TORRSERVE -> TorrServeSettingsSection(
                            torrServeBaseUrl = torrServeBaseUrl,
                            torrServeLogin = torrServeLogin,
                            torrServePassword = torrServePassword,
                            isTorrServeClientAvailable = isTorrServeClientAvailable,
                            onTorrServeLoginChanged = actions.onTorrServeLoginChanged,
                            onTorrServePasswordChanged = actions.onTorrServePasswordChanged,
                            torrServeStatusText = torrServeStatusText,
                            isCheckingTorrServe = isCheckingTorrServe,
                            onTorrServeBaseUrlChanged = actions.onTorrServeBaseUrlChanged,
                            onSaveTorrServeClick = actions.onSaveTorrServeClick,
                            onResetTorrServeClick = actions.onResetTorrServeClick,
                            onCheckTorrServeClick = actions.onCheckTorrServeClick,
                            contentRequesters = contentRequesters,
                            onActionFocused = rememberAction,
                        )

                        SettingsSection.SERVER -> LostFilmServerSettingsSection(
                            lostFilmHost = lostFilmHost,
                            lostFilmHostStatusText = lostFilmHostStatusText,
                            activeLostFilmHost = activeLostFilmHost,
                            isCheckingLostFilmHost = isCheckingLostFilmHost,
                            isLostFilmAutomaticMirror = isLostFilmAutomaticMirror,
                            onCheckLostFilmHostClick = actions.onCheckLostFilmHostClick,
                            onSelectLostFilmMirror = actions.onSelectLostFilmMirror,
                            onToggleLostFilmAutomaticMirror = actions.onToggleLostFilmAutomaticMirror,
                            onLostFilmHostChanged = actions.onLostFilmHostChanged,
                            onSaveLostFilmHostClick = actions.onSaveLostFilmHostClick,
                            onResetLostFilmHostClick = actions.onResetLostFilmHostClick,
                            contentRequesters = contentRequesters,
                            onActionFocused = rememberAction,
                        )

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
                                    onUpdateModeSelected = actions.onUpdateModeSelected,
                                    onCheckForUpdatesClick = actions.onCheckForUpdatesClick,
                                    onInstallUpdateClick = actions.onInstallUpdateClick,
                                    railRequester = railRequesters.getValue(SettingsSection.UPDATES),
                                    contentRequesters = contentRequesters,
                                    onActionFocused = rememberAction,
                                )
                            }
                        }

                        SettingsSection.ACCOUNT -> AccountSettingsSection(
                            isAuthenticated = isAuthenticated,
                            onAuthClick = actions.onAuthClick,
                            accountSummary = accountSummary,
                            railRequester = railRequesters.getValue(SettingsSection.ACCOUNT),
                            contentRequesters = contentRequesters,
                            onActionFocused = rememberAction,
                        )

                        SettingsSection.ABOUT -> AboutSettingsSection(
                            railRequester = railRequesters.getValue(SettingsSection.ABOUT),
                            contentRequesters = contentRequesters,
                            onActionFocused = rememberAction,
                        )
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
    APPEARANCE("Внешний вид", "settings-section-appearance", "settings-section-appearance-summary"),
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
    appearanceSummary: String,
    homeScreenSummary: String,
    isTorrServeConnected: Boolean,
    hasTorrServeAddressError: Boolean,
    isTorrServeClientAvailable: Boolean?,
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
                    SettingsSection.APPEARANCE -> appearanceSummary
                    SettingsSection.TORRSERVE -> when {
                        isTorrServeConnected -> "Подключён"
                        hasTorrServeAddressError -> "Ошибка проверки адреса"
                        isTorrServeClientAvailable == true -> "Клиент доступен"
                        isTorrServeClientAvailable == false -> "Клиент недоступен"
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
                    isTorrServeConnected -> LocalLostFilmColors.current.statusSuccess
                    hasTorrServeAddressError || isTorrServeClientAvailable == false -> LocalLostFilmColors.current.statusDanger
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
    SettingsSection.APPEARANCE -> "M12 3 A9 9 0 1 0 21 12 A9 9 0 0 0 12 3 M12 3 V21"
    SettingsSection.CHANNEL -> "M3 4 H21 V17 H3 Z M8 21 H16"
    SettingsSection.UPDATES -> "M20 8 A9 9 0 1 0 21 14 M20 3 V9 H14"
    SettingsSection.ABOUT -> "M3 12 A9 9 0 1 0 21 12 A9 9 0 1 0 3 12 M12 11 V17 M12 7 V8"
}

@Composable
internal fun SettingsGroupLabel(text: String, color: Color = HomeTextMuted) {
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
internal fun SettingsOptionsSection(
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

private fun targetContentTag(
    section: SettingsSection,
    selectedQuality: PlaybackQualityPreference,
    selectedUpdateMode: UpdateCheckMode,
    selectedChannelMode: AndroidTvChannelMode,
    selectedTheme: AppTheme,
    installUrl: String?,
    rememberedActionBySection: Map<String, SettingsFocusTarget>,
): SettingsFocusTarget? {
    val rememberedTarget = rememberedActionBySection[section.name]
    return when {
        rememberedTarget != null && isActionAvailable(rememberedTarget, installUrl) -> rememberedTarget
        section == SettingsSection.PLAYBACK -> SettingsFocusTarget.PlaybackQuality(selectedQuality)
        section == SettingsSection.HOME_SCREEN -> SettingsFocusTarget.HomeFavoritesToggle
        section == SettingsSection.APPEARANCE -> SettingsFocusTarget.Theme(selectedTheme)
        section == SettingsSection.CHANNEL -> SettingsFocusTarget.ChannelMode(selectedChannelMode)
        section == SettingsSection.TORRSERVE -> SettingsFocusTarget.TorrServeBaseUrl
        section == SettingsSection.UPDATES -> SettingsFocusTarget.UpdateChannel(selectedUpdateMode)
        section == SettingsSection.ACCOUNT -> SettingsFocusTarget.AccountAuth
        section == SettingsSection.SERVER -> SettingsFocusTarget.LostFilmHostCheck
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
