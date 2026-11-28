package com.kraat.lostfilmnewtv.playback

enum class WatchedMarkingMode(val storageValue: String) {
    /** Автоматически отмечать просмотренным при запуске воспроизведения. */
    AUTO("auto"),

    /** Запрашивать подтверждение после возвращения из плеера. */
    ASK_AFTER_RETURN("ask_after_return"),

    /** Никогда не отмечать просмотренным автоматически. */
    DISABLED("disabled"),
    ;

    companion object {
        fun fromStorageValue(value: String?): WatchedMarkingMode =
            entries.firstOrNull { it.storageValue == value } ?: AUTO
    }
}
