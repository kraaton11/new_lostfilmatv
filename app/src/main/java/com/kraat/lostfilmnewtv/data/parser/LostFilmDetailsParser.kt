package com.kraat.lostfilmnewtv.data.parser

import com.kraat.lostfilmnewtv.data.model.ReleaseDetails
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import com.kraat.lostfilmnewtv.data.model.TorrentLink
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

private val detailsSeasonEpisodeRegex = Regex(""".*/season_(\d+)/episode_(\d+)/?""")
private val playEpisodeRegex = Regex("""PlayEpisode\('?(\d+)'?\)""")
private val torrentRedirectRegex = Regex("""/V/\?[^"'\s<]+""")
private val userDataBlockRegex = Regex("""UserData\s*=\s*\{.*?\}""", setOf(RegexOption.DOT_MATCHES_ALL))
private val userDataSessionRegex = Regex("""["']session["']\s*:\s*["']([^"']+)["']""")
private val userDataSessionAssignmentRegex = Regex("""UserData\.session\s*=\s*["']([^"']+)["']""")

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
        val linksFromOptionsPage = document
            .select(".inner-box--item")
            .mapIndexedNotNull { index, item ->
                val mainLink = item.selectFirst(".inner-box--link.main a")
                val href = item.selectFirst(".inner-box--link.main a")
                    ?.absUrl("href")
                    .orEmpty()
                if (href.isBlank()) {
                    return@mapIndexedNotNull null
                }

                TorrentLink(
                    label = item.selectFirst(".inner-box--label")
                        ?.text()
                        .orEmpty()
                        .normalizeTorrentLabel(
                            index = index,
                            fallbackText = mainLink?.text().orEmpty(),
                        ),
                    url = href,
                )
            }
            .distinctBy { it.url }

        if (linksFromOptionsPage.isNotEmpty()) {
            return linksFromOptionsPage
        }

        val linksFromAnchors = document
            .select("a[href*=/V/?]")
            .mapIndexedNotNull { index, element ->
                val href = element.absUrl("href").ifBlank { resolveUrl(element.attr("href")) }
                if (href.isBlank()) {
                    return@mapIndexedNotNull null
                }

                TorrentLink(
                    label = element.text().normalizeTorrentLabel(index),
                    url = href,
                )
            }
            .distinctBy { it.url }

        if (linksFromAnchors.isNotEmpty()) {
            return linksFromAnchors
        }

        return parseTorrentRedirect(html)?.let { url ->
            listOf(TorrentLink(label = "Вариант 1", url = url))
        }.orEmpty()
    }

    fun parseAjaxSessionToken(html: String): String? {
        userDataSessionAssignmentRegex.find(html)?.groupValues?.getOrNull(1)?.let { return it }
        val userDataBlock = userDataBlockRegex.find(html)?.value ?: return null
        return userDataSessionRegex.find(userDataBlock)?.groupValues?.getOrNull(1)
    }

    fun parseWatchedState(html: String): Boolean? {
        val watchedElement = Jsoup.parse(html, BASE_URL)
            .selectFirst(".isawthat-btn, .haveseen-btn")
            ?: return null

        val normalizedText = watchedElement.text().lowercase()
        return when {
            watchedElement.hasClass("checked") -> true
            normalizedText.contains("не просмотрена") -> false
            normalizedText.contains("не просмотрен") -> false
            normalizedText.contains("not watched") -> false
            normalizedText.contains("просмотрена") -> true
            normalizedText.contains("просмотрен") -> true
            normalizedText.contains("watched") -> true
            else -> null
        }
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

private fun String.normalizeTorrentLabel(index: Int, fallbackText: String = ""): String {
    val normalized = normalizeText()
    return when {
        normalized.isBlank() -> "Вариант ${index + 1}"
        normalized.equals("эту ссылку", ignoreCase = true) -> "Вариант ${index + 1}"
        normalized.equals("this link", ignoreCase = true) -> "Вариант ${index + 1}"
        normalized.equals("скачать", ignoreCase = true) -> "Вариант ${index + 1}"
        normalized.equals("download", ignoreCase = true) -> "Вариант ${index + 1}"
        normalized == "1080" -> "1080p"
        normalized == "MP4" -> fallbackText.extractQualityFromTitle() ?: "720p"
        else -> normalized
    }
}

private fun String.extractQualityFromTitle(): String? {
    val normalized = normalizeText()
    return Regex("""\b(\d{3,4}p)\b""", RegexOption.IGNORE_CASE)
        .find(normalized)
        ?.groupValues
        ?.getOrNull(1)
        ?.lowercase()
        ?.replaceFirstChar { it.uppercase() }
}
