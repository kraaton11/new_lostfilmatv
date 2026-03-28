package com.kraat.lostfilmnewtv.playback

enum class PlaybackQualityPreference(
    val storageValue: String,
    val rank: Int,
) {
    Q480("480", 0),
    Q720("720", 1),
    Q1080("1080", 2),
    ;

    companion object {
        fun fromStorageValue(raw: String?): PlaybackQualityPreference {
            return entries.firstOrNull { it.storageValue == raw } ?: Q1080
        }
    }
}
