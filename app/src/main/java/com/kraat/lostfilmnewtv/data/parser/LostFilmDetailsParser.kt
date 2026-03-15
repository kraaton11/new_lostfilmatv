package com.kraat.lostfilmnewtv.data.parser

import com.kraat.lostfilmnewtv.data.model.ReleaseDetails
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

private val detailsSeasonEpisodeRegex = Regex(""".*/season_(\d+)/episode_(\d+)/?""")

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
        )
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
