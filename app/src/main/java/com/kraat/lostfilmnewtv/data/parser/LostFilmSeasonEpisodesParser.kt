package com.kraat.lostfilmnewtv.data.parser

import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import com.kraat.lostfilmnewtv.data.model.ReleaseSummary
import org.jsoup.Jsoup

private val seasonEpisodeUrlRegex = Regex("""/series/([^/]+)/season_(\d+)/episode_(\d+)/?""")
private val goToUrlRegex = Regex("""goTo\('([^']+)'""")
private val ruReleaseDateRegex = Regex("""Ru:\s*(\d{2}\.\d{2}\.\d{4})""")

class LostFilmSeasonEpisodesParser {
    fun parse(
        html: String,
        series: FavoriteSeriesRef,
        fetchedAt: Long,
    ): List<ReleaseSummary> {
        val document = Jsoup.parse(html, BASE_URL)

        return document.select("tr")
            .mapNotNull { row ->
                val betaCell = row.selectFirst("td.beta")
                val gammaCell = row.selectFirst("td.gamma")
                val deltaCell = row.selectFirst("td.delta")

                val detailsUrl = sequenceOf(
                    betaCell?.attr("onclick"),
                    gammaCell?.attr("onclick"),
                    row.attr("onclick"),
                )
                    .mapNotNull { onclick -> goToUrlRegex.find(onclick.orEmpty())?.groupValues?.getOrNull(1) }
                    .map(::resolveUrl)
                    .firstOrNull()
                    ?: return@mapNotNull null

                val match = seasonEpisodeUrlRegex.find(detailsUrl) ?: return@mapNotNull null
                val releaseDateRu = ruReleaseDateRegex.find(deltaCell.textOrEmpty())
                    ?.groupValues
                    ?.getOrNull(1)
                    .orEmpty()
                    .normalizeText()

                if (releaseDateRu.isBlank()) {
                    return@mapNotNull null
                }

                val episodeTitleRu = gammaCell
                    ?.ownText()
                    .orEmpty()
                    .normalizeText()
                    .ifBlank { null }
                val isWatched = row.selectFirst(".haveseen-btn")?.classNames()?.contains("checked") == true

                ReleaseSummary(
                    id = detailsUrl,
                    kind = ReleaseKind.SERIES,
                    titleRu = series.titleRu,
                    episodeTitleRu = episodeTitleRu,
                    seasonNumber = match.groupValues[2].toIntOrNull(),
                    episodeNumber = match.groupValues[3].toIntOrNull(),
                    releaseDateRu = releaseDateRu,
                    posterUrl = series.posterUrl,
                    detailsUrl = detailsUrl,
                    pageNumber = 0,
                    positionInPage = 0,
                    fetchedAt = fetchedAt,
                    isWatched = isWatched,
                )
            }
            .distinctBy { it.detailsUrl }
    }
}
