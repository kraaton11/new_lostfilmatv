package com.kraat.lostfilmnewtv.data.parser

import com.kraat.lostfilmnewtv.data.model.LostFilmSearchItem
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

private val searchDetailsBreakRegex = Regex("""<br\s*/?>""", setOf(RegexOption.IGNORE_CASE))

class LostFilmSearchParser {
    fun parse(html: String): List<LostFilmSearchItem> {
        val document = Jsoup.parse(html, BASE_URL)

        return document.select(".search-result .row-search")
            .mapNotNull(::parseRow)
    }

    private fun parseRow(row: Element): LostFilmSearchItem? {
        val contentLink = row.selectFirst("a[href].no-decoration") ?: return null
        val targetUrl = contentLink.absoluteUrl("href")
        val kind = when {
            targetUrl.contains("/movies/", ignoreCase = true) -> ReleaseKind.MOVIE
            targetUrl.contains("/series/", ignoreCase = true) -> ReleaseKind.SERIES
            else -> return null
        }
        val subtitle = contentLink.selectFirst(".details-pane")
            ?.html()
            .orEmpty()
            .replace("&nbsp;", " ")
            .replace(searchDetailsBreakRegex, " • ")
            .let { Jsoup.parseBodyFragment(it).text().normalizeText() }
            .takeIf { it.isNotBlank() }

        return LostFilmSearchItem(
            titleRu = contentLink.selectFirst(".name-ru").textOrEmpty(),
            titleEn = contentLink.selectFirst(".name-en")
                ?.text()
                ?.normalizeText()
                ?.takeIf { it.isNotBlank() },
            subtitle = subtitle,
            posterUrl = contentLink.selectFirst(".picture-box img.thumb")
                .absoluteUrl("src")
                .ifBlank { null },
            targetUrl = targetUrl,
            kind = kind,
        )
    }
}
