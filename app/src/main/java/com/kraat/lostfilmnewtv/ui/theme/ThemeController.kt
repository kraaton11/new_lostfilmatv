package com.kraat.lostfilmnewtv.ui.theme

import com.kraat.lostfilmnewtv.playback.PlaybackPreferencesStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class ThemeController @Inject constructor(
    private val preferencesStore: PlaybackPreferencesStore,
) {
    private val selectedTheme = MutableStateFlow(preferencesStore.readAppTheme())
    val theme: StateFlow<AppTheme> = selectedTheme.asStateFlow()

    @Synchronized
    fun selectTheme(theme: AppTheme) {
        if (selectedTheme.value == theme) return
        preferencesStore.writeAppTheme(theme)
        selectedTheme.value = theme
    }
}
