package com.kraat.lostfilmnewtv.data.parser

import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import com.kraat.lostfilmnewtv.data.model.ReleaseSummary
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

private val favoriteEpisodeRegex = Regex(""".*/series/([^/]+)/season_(\d+)/episode_(\d+)/?""")
private val favoriteReleaseDateRegex = Regex("""^\d{1,2}\.\d{1,2}\.\d{4}$""")
private val favoriteSeasonEpisodeLabelRegex = Regex(
    pattern = """^\d+\s*сезон\s+\d+\s*серия$""",
    option = RegexOption.IGNORE_CASE,
)
private val redirectWrapperRegex = Regex(
    pattern = """(?m)^[ \t]*top\.location\.replace\((["'])/\1\);?\s*$""",
)

class LostFilmFavoriteReleasesParser {
    fun parse(
        html: String,
        fetchedAt: Long,
    ): List<ReleaseSummary> {
        if (looksLikeRedirectWrapper(html)) {
            return emptyList()
        }

        val document = Jsoup.parse(html, BASE_URL)

        return document.select("a[href*=/series/]")
            .mapNotNull { element ->
                val detailsUrl = element.absUrl("href").ifBlank { resolveUrl(element.attr("href")) }
                val match = favoriteEpisodeRegex.matchEntire(detailsUrl) ?: return@mapNotNull null
                val titleRu = element.attr("title")
                    .ifBlank { element.selectFirst(".alpha")?.text().orEmpty() }
                    .normalizeText()
                val episodeTitleRu = element.extractEpisodeTitle(titleRu)
                val posterUrl = element.selectFirst("img")?.absUrl("src").orEmpty()
                val releaseDateRu = element.selectFirst(".date")?.text().orEmpty().normalizeText()

                if (titleRu.isBlank() || posterUrl.isBlank()) {
                    return@mapNotNull null
                }

                ReleaseSummary(
                    id = detailsUrl,
                    kind = ReleaseKind.SERIES,
                    titleRu = titleRu,
                    episodeTitleRu = episodeTitleRu,
                    seasonNumber = match.groupValues[2].toIntOrNull(),
                    episodeNumber = match.groupValues[3].toIntOrNull(),
                    releaseDateRu = releaseDateRu,
                    posterUrl = posterUrl,
                    detailsUrl = detailsUrl,
                    pageNumber = 0,
                    positionInPage = 0,
                    fetchedAt = fetchedAt,
                )
            }
            .mapIndexed { index, item -> item.copy(positionInPage = index) }
    }

    private fun looksLikeRedirectWrapper(html: String): Boolean {
        return redirectWrapperRegex.containsMatchIn(html)
    }

    private fun Element.extractEpisodeTitle(titleRu: String): String? {
        val candidates = buildList {
            add(attr("data-episode-title"))
            add(attr("data-title"))
            select(".episode-title, .episode, .beta, .details-pane .alpha, .details-pane .beta")
                .forEach { add(it.text()) }
        }

        return candidates
            .map { it.normalizeText() }
            .firstOrNull { value ->
                value.isNotBlank() &&
                    value != titleRu &&
                    !favoriteReleaseDateRegex.matches(value) &&
                    !favoriteSeasonEpisodeLabelRegex.matches(value)
            }
    }
}
