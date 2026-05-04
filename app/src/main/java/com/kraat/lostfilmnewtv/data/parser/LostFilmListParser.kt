package com.kraat.lostfilmnewtv.data.parser

import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import com.kraat.lostfilmnewtv.data.model.ReleaseSummary
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

const val BASE_URL = "https://www.lostfilm.today"
private val seasonEpisodeRegex = Regex("""(\d+)\s+сезон\s+(\d+)\s+серия""")
private val yearRegex = Regex("""\b(19|20)\d{2}\b""")

data class ReleaseWatchMarker(
    val detailsUrl: String,
    val episodeId: String,
    val serialId: String,
)

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

    fun parseWatchMarkers(html: String): List<ReleaseWatchMarker> {
        val document = Jsoup.parse(html, BASE_URL)

        return document.select(".serials-list .row").mapNotNull { row ->
            val detailsUrl = row.selectFirst("a[href]:not(.comment-blue-box)")
                .absoluteUrl("href")
                .takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val watchedButton = row.selectFirst(".haveseen-btn, .isawthat-btn")
                ?: return@mapNotNull null
            val episodeId = watchedButton.attr("data-episode")
                .trim()
                .takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            val serialId = watchedButton.attr("data-code")
                .substringBefore('-')
                .trim()
                .takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null

            ReleaseWatchMarker(
                detailsUrl = detailsUrl,
                episodeId = episodeId,
                serialId = serialId,
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
        val detailsUrl = contentLink.absoluteUrl("href")
        val isMovie = overlayLabel.contains("Фильм", ignoreCase = true) || detailsUrl.contains("/movies/")
        val availabilityLabel = contentLink.selectFirst(".picture-box .small-block")
            .textOrEmpty()
            .takeIf { it.isNotBlank() }
        val detailsPaneValues = contentLink.select(".details-pane .alpha, .details-pane .beta")
            .map { it.text().normalizeText() }

        val posterUrl = contentLink.selectFirst(".picture-box img.thumb").absoluteUrl("src")
        val isWatched = row.selectFirst(".haveseen-btn.checked") != null
        val releaseDateRu = detailsPaneValues
            .firstOrNull { it.startsWith("Дата выхода Ru:") }
            ?.substringAfter(':')
            ?.trim()
            .orEmpty()
        val originalReleaseYear = detailsPaneValues
            .firstOrNull { it.startsWith("Дата выхода Eng:", ignoreCase = true) }
            ?.extractYear()

        val (seasonNumber, episodeNumber) = if (isMovie) {
            null to null
        } else {
            val match = checkNotNull(seasonEpisodeRegex.find(overlayLabel)) {
                "Cannot parse season/episode from '$overlayLabel'"
            }
            match.groupValues[1].toInt() to match.groupValues[2].toInt()
        }

        val episodeTitleRu = detailsPaneValues
            .firstOrNull { value ->
                value.isNotBlank() && !value.startsWith("Дата выхода", ignoreCase = true)
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
            availabilityLabel = availabilityLabel,
            originalReleaseYear = originalReleaseYear,
        )
    }
}

fun String.extractYear(): Int? =
    yearRegex.find(this)
        ?.value
        ?.toIntOrNull()

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
