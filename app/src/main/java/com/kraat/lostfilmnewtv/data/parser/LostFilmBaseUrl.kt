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

    val failoverMirrors = listOf("https://www.lostfilm.download", "https://www.lostfilm.tv")
    val selectableMirrors = listOf(DEFAULT) + failoverMirrors

    internal data class Selection(val activeUrl: String, val automatic: Boolean, val revision: Long = 0)

    @Volatile
    internal var selection = Selection(DEFAULT, false)
        private set

    private var preferredUrl = DEFAULT
    private val current = MutableStateFlow(DEFAULT)
    val url: StateFlow<String> = current.asStateFlow()

    fun get(): String = selection.activeUrl

    @Synchronized
    fun set(value: String) {
        preferredUrl = value.trim().trimEnd('/').ifBlank { DEFAULT }
        publish(Selection(preferredUrl, selection.automatic, selection.revision + 1))
    }

    @Synchronized
    fun setAutomatic(enabled: Boolean) {
        publish(
            Selection(if (enabled) selection.activeUrl else preferredUrl, enabled, selection.revision + 1),
        )
    }

    @Synchronized
    internal fun switchMirror(expected: Selection, url: String): Boolean {
        if (selection !== expected || !expected.automatic || url !in failoverMirrors) return false
        publish(expected.copy(activeUrl = url))
        return true
    }

    private fun publish(value: Selection) {
        selection = value
        current.value = value.activeUrl
    }
}
