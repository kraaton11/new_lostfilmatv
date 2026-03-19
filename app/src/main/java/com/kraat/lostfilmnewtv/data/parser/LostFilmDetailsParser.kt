package com.kraat.lostfilmnewtv.data.parser

import com.kraat.lostfilmnewtv.data.model.ReleaseDetails
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import com.kraat.lostfilmnewtv.data.model.TorrentLink
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

private val detailsSeasonEpisodeRegex = Regex(""".*/season_(\d+)/episode_(\d+)/?""")
private val playEpisodeRegex = Regex("""PlayEpisode\('?(\d+)'?\)""")
private val torrentRedirectRegex = Regex("""/V/\?[^"'\s<]+""")

class LostFilmDetailsParser {
    fun parseSeries(
        html: String,
        detailsUrl: String,
        fetchedAt: Long = 0L,
    ): ReleaseDetails {
        val document = Jsoup.parse(html, BASE_URL)
        val absoluteDetailsUrl = resolveUrl(detailsUrl)
        val match = checkNotNull(detailsSeasonEpisodeRegex.matchEntire(absoluteDetailsUrl)) {
            "Cannot parse season/episode from '$detailsUrl'"
        }

        return ReleaseDetails(
            detailsUrl = absoluteDetailsUrl,
            kind = ReleaseKind.SERIES,
            titleRu = document.selectFirst(".breadcrumbs-pane a[href^=/series/]").textOrEmpty(),
            seasonNumber = match.groupValues[1].toInt(),
            episodeNumber = match.groupValues[2].toInt(),
            releaseDateRu = document.releaseDateRu(),
            posterUrl = document.posterUrl(),
            fetchedAt = fetchedAt,
            playEpisodeId = document.playEpisodeId(),
        )
    }

    fun parseMovie(
        html: String,
        detailsUrl: String,
        fetchedAt: Long = 0L,
    ): ReleaseDetails {
        val document = Jsoup.parse(html, BASE_URL)

        return ReleaseDetails(
            detailsUrl = resolveUrl(detailsUrl),
            kind = ReleaseKind.MOVIE,
            titleRu = document.selectFirst("h1.title-ru").textOrEmpty(),
            seasonNumber = null,
            episodeNumber = null,
            releaseDateRu = document.releaseDateRu(),
            posterUrl = document.posterUrl(),
            fetchedAt = fetchedAt,
            playEpisodeId = document.playEpisodeId(),
        )
    }

    fun parseTorrentRedirect(html: String): String? {
        val match = torrentRedirectRegex.find(html) ?: return null
        return resolveUrl(match.value)
    }

    fun parseTorrentLinks(html: String): List<TorrentLink> {
        val document = Jsoup.parse(html, BASE_URL)
        return document.select(".inner-box--item").mapNotNull { item ->
            val label = item.selectFirst(".inner-box--label").textOrEmpty()
            val url = item.selectFirst(".inner-box--link a[href]")?.absUrl("href").orEmpty()
            if (label.isBlank() || url.isBlank()) {
                null
            } else {
                TorrentLink(label = label, url = url)
            }
        }.distinctBy { it.url }
    }
}

private fun Document.posterUrl(): String =
    selectFirst(".main_poster img[rel=image_src], .main_poster img").absoluteUrl("src")

private fun Document.releaseDateRu(): String =
    select(".details-pane span[data-released]")
        .firstOrNull()
        ?.attr("data-released")
        ?.normalizeText()
        .orEmpty()

private fun Document.playEpisodeId(): String? {
    val onClick = selectFirst(".external-btn[onClick]")?.attr("onClick").orEmpty()
    return playEpisodeRegex.find(onClick)?.groupValues?.getOrNull(1)
}
