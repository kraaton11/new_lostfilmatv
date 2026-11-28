package com.kraat.lostfilmnewtv.ui.settings

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.kraat.lostfilmnewtv.data.parser.LostFilmBaseUrl

@Composable
internal fun LostFilmServerSettingsSection(
    lostFilmHost: String,
    lostFilmHostStatusText: String?,
    activeLostFilmHost: String,
    isCheckingLostFilmHost: Boolean,
    isLostFilmAutomaticMirror: Boolean,
    onCheckLostFilmHostClick: () -> Unit,
    onSelectLostFilmMirror: (String) -> Unit,
    onToggleLostFilmAutomaticMirror: () -> Unit,
    onLostFilmHostChanged: (String) -> Unit,
    onSaveLostFilmHostClick: () -> Unit,
    onResetLostFilmHostClick: () -> Unit,
    contentRequesters: Map<String, FocusRequester>,
    onActionFocused: (SettingsFocusTarget) -> Unit,
) {
    SettingsOptionsSection {
        SettingsOverviewCard(
            title = "Адрес хоста",
            subtitle = "Активный сервер: ${activeLostFilmHost.substringAfter("://")}",
        ) {
            lostFilmHostStatusText?.let { SettingsOverviewValue(text = it) }
        }
        Column(modifier = Modifier.focusGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val checkTarget = SettingsFocusTarget.LostFilmHostCheck
            SettingsRowButton(
                title = "Проверить доступность",
                value = if (isCheckingLostFilmHost) "Проверяем…" else "",
                onClick = onCheckLostFilmHostClick,
                tag = checkTarget.toTag(),
                onFocused = { onActionFocused(checkTarget) },
                modifier = Modifier.focusRequester(contentRequesters.getValue(checkTarget.toTag())),
            )
            val automaticTarget = SettingsFocusTarget.LostFilmAutomaticMirror
            SettingsRowButton(
                title = "Автопереключение",
                description = "При сбое загрузки публичных страниц проверить другой сервер.",
                value = if (isLostFilmAutomaticMirror) "Включено" else "Выключено",
                toggle = true,
                isSelected = isLostFilmAutomaticMirror,
                onClick = onToggleLostFilmAutomaticMirror,
                tag = automaticTarget.toTag(),
                onFocused = { onActionFocused(automaticTarget) },
                modifier = Modifier.focusRequester(contentRequesters.getValue(automaticTarget.toTag())),
            )
            LostFilmBaseUrl.selectableMirrors.forEach { url ->
                val target = SettingsFocusTarget.LostFilmMirror(url)
                SettingsRowButton(
                    title = url.substringAfter("://").removePrefix("www."),
                    value = if (url == activeLostFilmHost) "Активен" else "",
                    radio = true,
                    isSelected = url == activeLostFilmHost,
                    onClick = { onSelectLostFilmMirror(url) },
                    tag = target.toTag(),
                    onFocused = { onActionFocused(target) },
                    modifier = Modifier.focusRequester(contentRequesters.getValue(target.toTag())),
                )
            }
            SettingsTextField(
                value = lostFilmHost,
                onValueChange = onLostFilmHostChanged,
                label = "Адрес хоста",
                tag = SettingsFocusTarget.LostFilmHost.toTag(),
                onFocused = { onActionFocused(SettingsFocusTarget.LostFilmHost) },
                modifier = Modifier
                    .focusRequester(contentRequesters.getValue(SettingsFocusTarget.LostFilmHost.toTag()))
                    .onPreviewKeyEvent { event ->
                        val target = when (event.key) {
                            Key.DirectionUp -> SettingsFocusTarget.LostFilmMirror(
                                LostFilmBaseUrl.selectableMirrors.last(),
                            )
                            Key.DirectionDown -> SettingsFocusTarget.LostFilmHostSave
                            else -> null
                        }
                        if (target != null && event.type == KeyEventType.KeyDown) {
                            contentRequesters.getValue(target.toTag()).requestFocus()
                        } else {
                            false
                        }
                    },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SettingsRowButton(
                    title = "Сохранить",
                    description = "Применить указанный адрес хоста.",
                    value = "Сохранить",
                    onClick = onSaveLostFilmHostClick,
                    tag = SettingsFocusTarget.LostFilmHostSave.toTag(),
                    onFocused = { onActionFocused(SettingsFocusTarget.LostFilmHostSave) },
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
                    onFocused = { onActionFocused(SettingsFocusTarget.LostFilmHostReset) },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(contentRequesters.getValue(SettingsFocusTarget.LostFilmHostReset.toTag())),
                )
            }
        }
    }
}
