package com.kraat.lostfilmnewtv.tvchannel

enum class AndroidTvChannelMode(
    val storageValue: String,
) {
    ALL_NEW("all_new"),
    UNWATCHED("unwatched"),
    DISABLED("disabled");

    companion object {
        fun fromStorageValue(value: String?): AndroidTvChannelMode {
            return entries.firstOrNull { it.storageValue == value } ?: ALL_NEW
        }
    }
}
