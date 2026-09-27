package com.kraat.lostfilmnewtv.data.parser

/**
 * Runtime holder for the LostFilm base URL.
 *
 * The address used to be a compile-time constant (`BASE_URL`). It is now configurable from the
 * app Settings, so the current value lives here and is read by every request builder.
 * [DEFAULT] keeps the historical behaviour until the user changes the host.
 */
object LostFilmBaseUrl {
    const val DEFAULT = "https://www.lostfilm.today"

    @Volatile
    private var current: String = DEFAULT

    fun get(): String = current

    fun set(value: String) {
        current = value.trim().trimEnd('/').ifBlank { DEFAULT }
    }
}
