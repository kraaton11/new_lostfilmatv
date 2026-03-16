package com.kraat.lostfilmnewtv.data.parser

import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import com.kraat.lostfilmnewtv.data.model.ReleaseSummary
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

const val BASE_URL = "https://www.lostfilm.today"
private val seasonEpisodeRegex = Regex("""(\d+)\s+сезон\s+(\d+)\s+серия""")

class LostFilmListParser {
    fun parse(
        html: String,
        pageNumber: Int,
        fetchedAt: Long = 0L,
    ): List<ReleaseSummary> {
        val document = Jsoup.parse(html, BASE_URL)

        return document.select(".serials-list .row").mapIndexed { index, row ->
            parseRow(
                row = row,
                pageNumber = pageNumber,
                positionInPage = index,
                fetchedAt = fetchedAt,
            )
        }
    }

    private fun parseRow(
        row: Element,
        pageNumber: Int,
        positionInPage: Int,
        fetchedAt: Long,
    ): ReleaseSummary {
        val contentLink = checkNotNull(row.selectFirst("a[href]:not(.comment-blue-box)")) {
            "Missing content link for row"
        }
        val overlayLabel = contentLink.selectFirst(".overlay .left-part").textOrEmpty()
        val isMovie = overlayLabel.contains("Фильм", ignoreCase = true)
        val detailsAlphaValues = contentLink.select(".details-pane .alpha")
            .map { it.text().normalizeText() }

        val detailsUrl = contentLink.absoluteUrl("href")
        val posterUrl = contentLink.selectFirst(".picture-box img.thumb").absoluteUrl("src")
        val isWatched = row.selectFirst(".haveseen-btn.checked") != null
        val releaseDateRu = detailsAlphaValues
            .firstOrNull { it.startsWith("Дата выхода Ru:") }
            ?.substringAfter(':')
            ?.trim()
            .orEmpty()

        val (seasonNumber, episodeNumber) = if (isMovie) {
            null to null
        } else {
            val match = checkNotNull(seasonEpisodeRegex.find(overlayLabel)) {
                "Cannot parse season/episode from '$overlayLabel'"
            }
            match.groupValues[1].toInt() to match.groupValues[2].toInt()
        }

        val episodeTitleRu = detailsAlphaValues
            .firstOrNull { value ->
                value.isNotBlank() && !value.startsWith("Дата выхода Ru:")
            }
            ?.takeUnless { it.isBlank() }

        return ReleaseSummary(
            id = detailsUrl,
            kind = if (isMovie) ReleaseKind.MOVIE else ReleaseKind.SERIES,
            titleRu = contentLink.selectFirst(".name-ru").textOrEmpty(),
            episodeTitleRu = if (isMovie) null else episodeTitleRu,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            releaseDateRu = releaseDateRu,
            posterUrl = posterUrl,
            detailsUrl = detailsUrl,
            pageNumber = pageNumber,
            positionInPage = positionInPage,
            fetchedAt = fetchedAt,
            isWatched = isWatched,
        )
    }
}

fun Element?.absoluteUrl(attributeName: String): String {
    val value = this?.attr("abs:$attributeName").orEmpty()

    return value.ifBlank {
        resolveUrl(this?.attr(attributeName).orEmpty())
    }
}

fun Element?.textOrEmpty(): String = this?.text()?.normalizeText().orEmpty()

fun String.normalizeText(): String = trim().replace('\u00A0', ' ')

fun resolveUrl(url: String): String =
    when {
        url.startsWith("http://") || url.startsWith("https://") -> url
        url.startsWith("//") -> "https:$url"
        url.startsWith("/") -> "$BASE_URL$url"
        url.isBlank() -> ""
        else -> "$BASE_URL/$url"
    }
