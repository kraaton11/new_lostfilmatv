package com.kraat.lostfilmnewtv.data.parser

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private val lostFilmMirrorHosts = setOf(
    "lostfilm.today",
    "lostfilm.tv",
    "lostfilm.download",
)

internal fun String.isKnownLostFilmMirror(): Boolean =
    removePrefix("www.").removePrefix("static.") in lostFilmMirrorHosts

/**
 * Runtime holder for the LostFilm base URL.
 *
 * The address used to be a compile-time constant (`BASE_URL`). It is now configurable from the
 * app Settings, so the current value lives here and is read by every request builder.
 * [DEFAULT] keeps the historical behaviour until the user changes the host.
 */
object LostFilmBaseUrl {
    const val DEFAULT = "https://www.lostfilm.today"

    private val current = MutableStateFlow(DEFAULT)
    val url: StateFlow<String> = current.asStateFlow()

    fun get(): String = current.value

    fun set(value: String) {
        current.value = value.trim().trimEnd('/').ifBlank { DEFAULT }
    }
}
