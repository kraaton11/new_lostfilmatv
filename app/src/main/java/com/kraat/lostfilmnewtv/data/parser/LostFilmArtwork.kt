package com.kraat.lostfilmnewtv.data.parser

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Element

/** Keeps the image address independent of the currently selected mirror. */
fun lostFilmArtworkPath(raw: String?, baseUrl: String = BASE_URL): String? {
    val source = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val base = baseUrl.toHttpUrlOrNull() ?: return null
    val image = base.resolve(source) ?: return null
    val sameHost = image.host.removePrefix("www.") == base.host.removePrefix("www.")
    if (!sameHost && !image.host.isKnownLostFilmMirror()) return null
    if (!image.encodedPath.startsWith("/Static/Images/", ignoreCase = true)) return null

    return image.encodedQuery?.let { "${image.encodedPath}?$it" } ?: image.encodedPath
}

/** Resolves a stored image path on the selected LostFilm server. */
fun lostFilmArtworkUrl(raw: String?, baseUrl: String = BASE_URL): String? {
    val path = lostFilmArtworkPath(raw, baseUrl) ?: return null
    return baseUrl.toHttpUrlOrNull()?.resolve(path)?.toString()
}

internal fun Element.lostFilmImagePath(selector: String): String? =
    select(selector).firstNotNullOfOrNull { lostFilmArtworkPath(it.attr("src")) }

internal fun hasLegacyArtwork(poster: String, backdrop: String?): Boolean =
    (poster.isNotBlank() && lostFilmArtworkPath(poster) == null) ||
        (!backdrop.isNullOrBlank() && lostFilmArtworkPath(backdrop) == null)

private val lostFilmSeasonThumbnail =
    Regex("""(/Static/Images/\d+/Posters/)(?:image|icon|t_shmoster)(_s\d+\.jpg)""", RegexOption.IGNORE_CASE)

/** The full-size portrait shares the season suffix of its published thumbnails. */
internal fun lostFilmPortraitUrl(raw: String, baseUrl: String = BASE_URL): String? {
    val path = lostFilmArtworkPath(raw, baseUrl) ?: return null
    val match = lostFilmSeasonThumbnail.matchEntire(path.substringBefore('?')) ?: return null
    val (directory, seasonFileSuffix) = match.destructured
    return lostFilmArtworkUrl("${directory}shmoster$seasonFileSuffix", baseUrl)
}
