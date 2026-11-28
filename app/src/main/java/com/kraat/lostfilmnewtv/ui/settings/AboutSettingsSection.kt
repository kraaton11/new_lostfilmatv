package com.kraat.lostfilmnewtv.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kraat.lostfilmnewtv.BuildConfig

@Composable
internal fun AboutSettingsSection(
    railRequester: FocusRequester,
    contentRequesters: Map<String, FocusRequester>,
    onActionFocused: (SettingsFocusTarget) -> Unit,
) {
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
                onFocused = { onActionFocused(SettingsFocusTarget.AboutGitHubLink) },
                modifier = Modifier
                    .focusRequester(contentRequesters.getValue(gitHubTag))
                    .focusProperties {
                        left = railRequester
                        up = railRequester
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
                onFocused = { onActionFocused(SettingsFocusTarget.AboutTelegramLink) },
                modifier = Modifier
                    .focusRequester(contentRequesters.getValue(telegramTag))
                    .focusProperties {
                        left = railRequester
                        up = contentRequesters.getValue(gitHubTag)
                        down = FocusRequester.Default
                    },
            )
        }
    }
}
