package com.kraat.lostfilmnewtv.playback

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kraat.lostfilmnewtv.data.model.CatalogSort
import com.kraat.lostfilmnewtv.data.model.ReleaseWatchFilter
import com.kraat.lostfilmnewtv.data.model.SeriesStatus
import com.kraat.lostfilmnewtv.data.model.WatchHistoryFilter
import com.kraat.lostfilmnewtv.data.parser.LostFilmBaseUrl
import com.kraat.lostfilmnewtv.tvchannel.AndroidTvChannelMode
import com.kraat.lostfilmnewtv.ui.home.HomeFeedMode
import com.kraat.lostfilmnewtv.ui.theme.AppTheme
import com.kraat.lostfilmnewtv.updates.UpdateCheckMode

internal const val DEFAULT_TORRSERVE_BASE_URL = "http://127.0.0.1:8090"

class PlaybackPreferencesStore(
    context: Context,
    prefsName: String = DEFAULT_PREFS_NAME,
) {
    private val prefs = PlaybackPreferencesDataStore.get(context, prefsName)

    fun readAppTheme(): AppTheme = AppTheme.fromStorageValue(prefs[KEY_APP_THEME])

    fun writeAppTheme(value: AppTheme) {
        prefs.update { this[KEY_APP_THEME] = value.storageValue }
    }

    fun readDefaultQuality(): PlaybackQualityPreference {
        return PlaybackQualityPreference.fromStorageValue(
            prefs[KEY_DEFAULT_QUALITY],
        )
    }

    fun writeDefaultQuality(value: PlaybackQualityPreference) {
        prefs.update { this[KEY_DEFAULT_QUALITY] = value.storageValue }
    }

    fun readUpdateCheckMode(): UpdateCheckMode {
        return UpdateCheckMode.fromStorageValue(
            prefs[KEY_UPDATE_CHECK_MODE],
        )
    }

    fun writeUpdateCheckMode(value: UpdateCheckMode) {
        prefs.update { this[KEY_UPDATE_CHECK_MODE] = value.storageValue }
    }

    fun readAndroidTvChannelMode(): AndroidTvChannelMode {
        return AndroidTvChannelMode.fromStorageValue(
            prefs[KEY_ANDROID_TV_CHANNEL_MODE],
        )
    }

    fun writeAndroidTvChannelMode(value: AndroidTvChannelMode) {
        prefs.update { this[KEY_ANDROID_TV_CHANNEL_MODE] = value.storageValue }
    }

    fun readAndroidTvChannelId(): Long? {
        return prefs[KEY_ANDROID_TV_CHANNEL_ID]
    }

    fun writeAndroidTvChannelId(value: Long) {
        prefs.update { this[KEY_ANDROID_TV_CHANNEL_ID] = value }
    }

    fun clearAndroidTvChannelId() {
        prefs.update { remove(KEY_ANDROID_TV_CHANNEL_ID) }
    }

    fun readAndroidTvFavoritesChannelId(): Long? {
        return prefs[KEY_ANDROID_TV_FAVORITES_CHANNEL_ID]
    }

    fun writeAndroidTvFavoritesChannelId(value: Long) {
        prefs.update { this[KEY_ANDROID_TV_FAVORITES_CHANNEL_ID] = value }
    }

    fun clearAndroidTvFavoritesChannelId() {
        prefs.update { remove(KEY_ANDROID_TV_FAVORITES_CHANNEL_ID) }
    }

    fun readHomeFavoritesRailEnabled(): Boolean {
        return prefs[KEY_HOME_FAVORITES_RAIL_ENABLED] ?: true
    }

    fun writeHomeFavoritesRailEnabled(value: Boolean) {
        prefs.update { this[KEY_HOME_FAVORITES_RAIL_ENABLED] = value }
    }

    fun readHomeFavoriteSeriesEnabled(): Boolean {
        return prefs[KEY_HOME_FAVORITE_SERIES_ENABLED] ?: true
    }

    fun writeHomeFavoriteSeriesEnabled(value: Boolean) {
        prefs.update { this[KEY_HOME_FAVORITE_SERIES_ENABLED] = value }
    }

    fun readHomeMoviesEnabled(): Boolean {
        return prefs[KEY_HOME_MOVIES_ENABLED] ?: true
    }

    fun writeHomeMoviesEnabled(value: Boolean) {
        prefs.update { this[KEY_HOME_MOVIES_ENABLED] = value }
    }

    fun readHomeSeriesEnabled(): Boolean {
        return prefs[KEY_HOME_SERIES_ENABLED] ?: true
    }

    fun writeHomeSeriesEnabled(value: Boolean) {
        prefs.update { this[KEY_HOME_SERIES_ENABLED] = value }
    }

    fun readHomeMenuLabelsEnabled(): Boolean {
        return prefs[KEY_HOME_MENU_LABELS_ENABLED] ?: true
    }

    fun writeHomeMenuLabelsEnabled(value: Boolean) {
        prefs.update { this[KEY_HOME_MENU_LABELS_ENABLED] = value }
    }

    fun readWatchedMarkingMode(): WatchedMarkingMode {
        return WatchedMarkingMode.fromStorageValue(
            prefs[KEY_WATCHED_MARKING_MODE],
        )
    }

    fun writeWatchedMarkingMode(value: WatchedMarkingMode) {
        prefs.update { this[KEY_WATCHED_MARKING_MODE] = value.storageValue }
    }

    fun readTorrServeBaseUrl(): String {
        return prefs[KEY_TORRSERVE_BASE_URL]
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_TORRSERVE_BASE_URL
    }

    fun readTorrServeLogin(): String = prefs[KEY_TORRSERVE_LOGIN].orEmpty()

    fun readTorrServePassword(): String = prefs[KEY_TORRSERVE_PASSWORD].orEmpty()

    fun writeTorrServeBaseUrl(
        value: String,
        login: String? = null,
        password: String? = null,
    ) {
        prefs.update {
            this[KEY_TORRSERVE_BASE_URL] = value
            if (login != null) this[KEY_TORRSERVE_LOGIN] = login
            if (password != null) this[KEY_TORRSERVE_PASSWORD] = password
        }
    }

    fun resetTorrServeBaseUrl() {
        prefs.update {
            remove(KEY_TORRSERVE_BASE_URL)
            remove(KEY_TORRSERVE_LOGIN)
            remove(KEY_TORRSERVE_PASSWORD)
        }
    }

    fun readLostFilmAutomaticMirror(): Boolean = prefs[KEY_LOSTFILM_AUTOMATIC_MIRROR] ?: false

    fun writeLostFilmAutomaticMirror(enabled: Boolean) {
        prefs.update { this[KEY_LOSTFILM_AUTOMATIC_MIRROR] = enabled }
    }

    fun readLostFilmBaseUrl(): String {
        return prefs[KEY_LOSTFILM_BASE_URL]
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_LOSTFILM_BASE_URL
    }

    fun writeLostFilmBaseUrl(value: String) {
        prefs.update { this[KEY_LOSTFILM_BASE_URL] = value }
    }

    fun resetLostFilmBaseUrl() {
        prefs.update { remove(KEY_LOSTFILM_BASE_URL) }
    }

    fun readHomeSelectedFeedMode(): HomeFeedMode {
        return HomeFeedMode.fromStorageValue(
            prefs[KEY_HOME_SELECTED_FEED_MODE],
        )
    }

    fun writeHomeSelectedFeedMode(value: HomeFeedMode) {
        prefs.update { this[KEY_HOME_SELECTED_FEED_MODE] = value.storageValue }
    }

    fun readReleaseFilter(mode: HomeFeedMode): ReleaseWatchFilter =
        readEnum(stringPreferencesKey("release_filter_${mode.storageValue}"), ReleaseWatchFilter.All)

    fun writeReleaseFilter(mode: HomeFeedMode, filter: ReleaseWatchFilter) {
        prefs.update { this[stringPreferencesKey("release_filter_${mode.storageValue}")] = filter.name }
    }

    fun readCatalogSort(mode: HomeFeedMode): CatalogSort =
        readEnum(stringPreferencesKey("catalog_sort_${mode.storageValue}"), CatalogSort.Added)

    fun readSeriesStatus(): SeriesStatus = readEnum(KEY_SERIES_STATUS, SeriesStatus.All)

    fun writeCatalogSelection(mode: HomeFeedMode, sort: CatalogSort, status: SeriesStatus) {
        prefs.update {
            this[stringPreferencesKey("catalog_sort_${mode.storageValue}")] = sort.name
            if (mode == HomeFeedMode.Series) this[KEY_SERIES_STATUS] = status.name
        }
    }

    fun readHistoryFilter(): WatchHistoryFilter = readEnum(KEY_HISTORY_FILTER, WatchHistoryFilter.All)

    fun writeHistoryFilter(filter: WatchHistoryFilter) {
        prefs.update { this[KEY_HISTORY_FILTER] = filter.name }
    }

    fun readScheduleFavoritesOnly(): Boolean = prefs[KEY_SCHEDULE_FAVORITES_ONLY] ?: false

    fun writeScheduleFavoritesOnly(favoritesOnly: Boolean) {
        prefs.update { this[KEY_SCHEDULE_FAVORITES_ONLY] = favoritesOnly }
    }

    private inline fun <reified T : Enum<T>> readEnum(key: Preferences.Key<String>, default: T): T {
        val storedName = prefs[key]
        return enumValues<T>().firstOrNull { it.name == storedName } ?: default
    }

    private companion object {
        const val DEFAULT_PREFS_NAME = "lostfilm_playback_prefs"
        val KEY_APP_THEME = stringPreferencesKey("app_theme")
        val KEY_SERIES_STATUS = stringPreferencesKey("series_status")
        val KEY_HISTORY_FILTER = stringPreferencesKey("history_filter")
        val KEY_SCHEDULE_FAVORITES_ONLY = booleanPreferencesKey("schedule_favorites_only")
        val KEY_DEFAULT_QUALITY = stringPreferencesKey("default_quality")
        val KEY_UPDATE_CHECK_MODE = stringPreferencesKey("update_check_mode")
        val KEY_ANDROID_TV_CHANNEL_MODE = stringPreferencesKey("android_tv_channel_mode")
        val KEY_ANDROID_TV_CHANNEL_ID = longPreferencesKey("android_tv_channel_id")
        val KEY_ANDROID_TV_FAVORITES_CHANNEL_ID = longPreferencesKey("android_tv_favorites_channel_id")
        val KEY_HOME_FAVORITES_RAIL_ENABLED = booleanPreferencesKey("home_favorites_rail_enabled")
        val KEY_HOME_FAVORITE_SERIES_ENABLED = booleanPreferencesKey("home_favorite_series_enabled")
        val KEY_HOME_MOVIES_ENABLED = booleanPreferencesKey("home_movies_enabled")
        val KEY_HOME_SERIES_ENABLED = booleanPreferencesKey("home_series_enabled")
        val KEY_HOME_MENU_LABELS_ENABLED = booleanPreferencesKey("home_menu_labels_enabled")
        val KEY_HOME_SELECTED_FEED_MODE = stringPreferencesKey("home_selected_feed_mode")
        val KEY_WATCHED_MARKING_MODE = stringPreferencesKey("watched_marking_mode")
        val KEY_TORRSERVE_LOGIN = stringPreferencesKey("torrserve_login")
        val KEY_TORRSERVE_PASSWORD = stringPreferencesKey("torrserve_password")
        val KEY_LOSTFILM_AUTOMATIC_MIRROR = booleanPreferencesKey("lostfilm_automatic_mirror")
        val KEY_TORRSERVE_BASE_URL = stringPreferencesKey("torrserve_base_url")
        val KEY_LOSTFILM_BASE_URL = stringPreferencesKey("lostfilm_base_url")
        const val DEFAULT_LOSTFILM_BASE_URL = LostFilmBaseUrl.DEFAULT
    }
}
