package com.kraat.lostfilmnewtv.data.parser

import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import com.kraat.lostfilmnewtv.data.model.ReleaseSummary
import org.jsoup.Jsoup

private val favoriteEpisodeRegex = Regex(""".*/series/([^/]+)/season_(\d+)/episode_(\d+)/?""")
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
                val posterUrl = element.selectFirst("img")?.absUrl("src").orEmpty()
                val releaseDateRu = element.selectFirst(".date")?.text().orEmpty().normalizeText()

                if (titleRu.isBlank() || posterUrl.isBlank()) {
                    return@mapNotNull null
                }

                ReleaseSummary(
                    id = detailsUrl,
                    kind = ReleaseKind.SERIES,
                    titleRu = titleRu,
                    episodeTitleRu = null,
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
}
