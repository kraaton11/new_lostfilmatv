package com.kraat.lostfilmnewtv.ui.settings

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp

@Composable
internal fun TorrServeSettingsSection(
    torrServeBaseUrl: String,
    torrServeLogin: String,
    torrServePassword: String,
    isTorrServeClientAvailable: Boolean?,
    onTorrServeLoginChanged: (String) -> Unit,
    onTorrServePasswordChanged: (String) -> Unit,
    torrServeStatusText: String?,
    isCheckingTorrServe: Boolean,
    onTorrServeBaseUrlChanged: (String) -> Unit,
    onSaveTorrServeClick: () -> Unit,
    onResetTorrServeClick: () -> Unit,
    onCheckTorrServeClick: () -> Unit,
    contentRequesters: Map<String, FocusRequester>,
    onActionFocused: (SettingsFocusTarget) -> Unit,
) {
    SettingsOptionsSection {
        SettingsOverviewCard(
            title = "TorrServe",
            subtitle = "Здесь проверяется указанный адрес. Для просмотра используются настройки приложения TorrServe.",
        ) {
            val status = torrServeStatusText ?: when (isTorrServeClientAvailable) {
                true -> "Клиент TorrServe доступен для воспроизведения"
                false -> "Android не может открыть клиент TorrServe"
                null -> null
            }
            status?.let { SettingsOverviewValue(text = it) }
        }
        Column(modifier = Modifier.focusGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SettingsTextField(
                value = torrServeBaseUrl,
                onValueChange = onTorrServeBaseUrlChanged,
                label = "Адрес TorrServe",
                tag = SettingsFocusTarget.TorrServeBaseUrl.toTag(),
                onFocused = { onActionFocused(SettingsFocusTarget.TorrServeBaseUrl) },
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
                    onFocused = { onActionFocused(SettingsFocusTarget.TorrServeSave) },
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
                    onFocused = { onActionFocused(SettingsFocusTarget.TorrServeReset) },
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
                onFocused = { onActionFocused(SettingsFocusTarget.TorrServeCheck) },
                modifier = Modifier.focusRequester(contentRequesters.getValue(SettingsFocusTarget.TorrServeCheck.toTag())),
            )
        }
    }
}
