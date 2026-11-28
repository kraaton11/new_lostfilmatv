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
internal fun AccountSettingsSection(
    isAuthenticated: Boolean,
    onAuthClick: () -> Unit,
    accountSummary: String,
    railRequester: FocusRequester,
    contentRequesters: Map<String, FocusRequester>,
    onActionFocused: (SettingsFocusTarget) -> Unit,
) {
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
                onFocused = { onActionFocused(SettingsFocusTarget.AccountAuth) },
                modifier = Modifier
                    .focusRequester(contentRequesters.getValue(SettingsFocusTarget.AccountAuth.toTag()))
                    .focusProperties {
                        left = railRequester
                        up = railRequester
                        down = FocusRequester.Default
                    },
            )
        }
    }
}
