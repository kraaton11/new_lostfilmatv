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

@Composable
internal fun HomeScreenSettingsSection(
    isHomeFavoritesRailEnabled: Boolean,
    isHomeMoviesEnabled: Boolean,
    isHomeSeriesEnabled: Boolean,
    isHomeMenuLabelsEnabled: Boolean,
    onHomeFavoritesRailVisibilitySelected: (Boolean) -> Unit,
    onHomeMoviesVisibilitySelected: (Boolean) -> Unit,
    onHomeSeriesVisibilitySelected: (Boolean) -> Unit,
    onHomeMenuLabelsVisibilitySelected: (Boolean) -> Unit,
    railRequester: FocusRequester,
    contentRequesters: Map<String, FocusRequester>,
    onActionFocused: (SettingsFocusTarget) -> Unit,
) {
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
                onFocused = { onActionFocused(SettingsFocusTarget.HomeFavoritesToggle) },
                modifier = Modifier
                    .focusRequester(contentRequesters.getValue(SettingsFocusTarget.HomeFavoritesToggle.toTag()))
                    .focusProperties {
                        left = railRequester
                        up = railRequester
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
                onFocused = { onActionFocused(SettingsFocusTarget.HomeMoviesToggle) },
                modifier = Modifier
                    .focusRequester(contentRequesters.getValue(SettingsFocusTarget.HomeMoviesToggle.toTag()))
                    .focusProperties {
                        left = railRequester
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
                onFocused = { onActionFocused(SettingsFocusTarget.HomeSeriesToggle) },
                modifier = Modifier
                    .focusRequester(contentRequesters.getValue(SettingsFocusTarget.HomeSeriesToggle.toTag()))
                    .focusProperties {
                        left = railRequester
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
                onFocused = { onActionFocused(SettingsFocusTarget.HomeMenuLabelsToggle) },
                modifier = Modifier
                    .focusRequester(contentRequesters.getValue(SettingsFocusTarget.HomeMenuLabelsToggle.toTag()))
                    .focusProperties {
                        left = railRequester
                        up = contentRequesters.getValue(SettingsFocusTarget.HomeSeriesToggle.toTag())
                        down = FocusRequester.Default
                    },
            )
        }
    }
}
