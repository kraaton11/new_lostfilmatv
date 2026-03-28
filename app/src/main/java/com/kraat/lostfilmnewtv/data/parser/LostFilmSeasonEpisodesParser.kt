package com.kraat.lostfilmnewtv.data.parser

import com.kraat.lostfilmnewtv.data.model.SeriesGuideEpisode
import com.kraat.lostfilmnewtv.data.model.SeriesGuideSeason
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import com.kraat.lostfilmnewtv.data.model.ReleaseSummary
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

private val seasonEpisodeUrlRegex = Regex("""/series/([^/]+)/season_(\d+)/episode_(\d+)/?""")
private val goToUrlRegex = Regex("""goTo\('([^']+)'""")
private val ruReleaseDateRegex = Regex("""Ru:\s*(\d{2}\.\d{2}\.\d{4})""")
private val seasonSerialIdRegex = Regex("""\bserial_id\s*=\s*['"](\d+)['"]""")

class LostFilmSeasonEpisodesParser {
    fun parseSerialId(html: String): String? {
        return seasonSerialIdRegex.find(html)?.groupValues?.getOrNull(1)
    }

    fun parseWatchedEpisodeIdsFromPage(html: String): Set<String> {
        val document = Jsoup.parse(html, BASE_URL)

        return document.select(".haveseen-btn, .isawthat-btn")
            .mapNotNull { watchedElement ->
                val isWatched = watchedElement.hasClass("checked") ||
                    watchedElement.attr("title").contains("серия просмотрена", ignoreCase = true) ||
                    watchedElement.attr("title").contains("фильм просмотрен", ignoreCase = true) ||
                    watchedElement.text().contains("серия просмотрена", ignoreCase = true) ||
                    watchedElement.text().contains("фильм просмотрен", ignoreCase = true)

                watchedElement.attr("data-episode")
                    .trim()
                    .takeIf { isWatched && it.isNotEmpty() }
            }
            .toSet()
    }

    fun parseWatchedEpisodeIds(response: String): Set<String> {
        return runCatching {
            val jsonElement = Json.parseToJsonElement(response)
            val watchedIds = when (jsonElement) {
                is JsonArray -> jsonElement
                else -> jsonElement.jsonObject["data"]?.jsonArray
            }

            watchedIds
                ?.mapNotNull { element ->
                    element.jsonPrimitive.content
                        .trim()
                        .takeIf { it.isNotEmpty() }
                }
                ?.toSet()
                .orEmpty()
        }.getOrDefault(emptySet())
    }

    fun parseGuide(
        html: String,
        watchedEpisodeIds: Set<String> = emptySet(),
    ): List<SeriesGuideSeason> {
        val document = Jsoup.parse(html, BASE_URL)

        return document.select(".serie-block")
            .mapNotNull { seasonBlock ->
                val episodes = seasonBlock.select("table.movie-parts-list tr")
                    .mapNotNull { row -> parseGuideEpisodeRow(row, watchedEpisodeIds) }

                if (episodes.isEmpty()) {
                    null
                } else {
                    SeriesGuideSeason(
                        seasonNumber = episodes.first().seasonNumber,
                        episodes = episodes,
                    )
                }
            }
    }

    fun parse(
        html: String,
        series: FavoriteSeriesRef,
        fetchedAt: Long,
        watchedEpisodeIds: Set<String> = emptySet(),
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
                val watchedButton = row.selectFirst(".haveseen-btn")
                val isWatched = watchedButton?.classNames()?.contains("checked") == true ||
                    watchedButton?.attr("data-episode").orEmpty() in watchedEpisodeIds

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

    private fun parseGuideEpisodeRow(
        row: Element,
        watchedEpisodeIds: Set<String>,
    ): SeriesGuideEpisode? {
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
            ?: return null

        val match = seasonEpisodeUrlRegex.find(detailsUrl) ?: return null
        val releaseDateRu = deltaCell
            ?.selectFirst("span[data-released]")
            ?.attr("data-released")
            ?.normalizeText()
            ?.ifBlank { null }
            ?: ruReleaseDateRegex.find(deltaCell.textOrEmpty())
                ?.groupValues
                ?.getOrNull(1)
                ?.normalizeText()
                ?: return null

        val episodeTitleRu = gammaCell
            ?.selectFirst("div")
            ?.ownText()
            ?.normalizeText()
            ?.ifBlank { null }
            ?: gammaCell
                ?.ownText()
                .orEmpty()
                .normalizeText()
                .ifBlank { null }

        val watchedButton = row.selectFirst(".haveseen-btn")
        val episodeId = watchedButton
            ?.attr("data-episode")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        val isWatched = watchedButton?.classNames()?.contains("checked") == true ||
            episodeId in watchedEpisodeIds

        return SeriesGuideEpisode(
            detailsUrl = detailsUrl,
            episodeId = episodeId,
            seasonNumber = match.groupValues[2].toIntOrNull() ?: return null,
            episodeNumber = match.groupValues[3].toIntOrNull() ?: return null,
            episodeTitleRu = episodeTitleRu,
            releaseDateRu = releaseDateRu,
            isWatched = isWatched,
        )
    }
}
