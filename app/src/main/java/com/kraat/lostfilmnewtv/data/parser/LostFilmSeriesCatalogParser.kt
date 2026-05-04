package com.kraat.lostfilmnewtv.data.parser

import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import com.kraat.lostfilmnewtv.data.model.ReleaseSummary
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class LostFilmSeriesCatalogParser {
    fun parse(
        html: String,
        fetchedAt: Long = 0L,
    ): List<ReleaseSummary> {
        val document = Jsoup.parse(html, BASE_URL)

        return document.select("#serials_list .row").mapIndexedNotNull { index, row ->
            parseRow(row, index, fetchedAt)
        }
    }

    fun parseSearchJson(
        json: String,
        pageNumber: Int,
        fetchedAt: Long = 0L,
    ): List<ReleaseSummary> {
        val root = Json.parseToJsonElement(json).jsonObject
        val data = root["data"]?.jsonArray ?: return emptyList()

        return buildList {
            data.forEachIndexed { index, element ->
                val item = element.jsonObject
                val detailsUrl = resolveUrl(item.stringValue("link")).takeIf { it.isNotBlank() } ?: return@forEachIndexed
                val titleRu = item.stringValue("title").normalizeText().takeIf { it.isNotBlank() } ?: return@forEachIndexed
                val titleEn = item.stringValue("title_orig").normalizeText().takeIf { it.isNotBlank() }
                val posterUrl = item.stringValue("img").takeIf { item.booleanValue("has_image") }
                    ?.let(::resolveUrl)
                    .orEmpty()
                val year = item.stringValue("date").extractYear()

                add(
                    ReleaseSummary(
                        id = detailsUrl,
                        kind = ReleaseKind.SERIES,
                        titleRu = titleRu,
                        episodeTitleRu = titleEn,
                        seasonNumber = null,
                        episodeNumber = null,
                        releaseDateRu = year?.toString().orEmpty(),
                        posterUrl = posterUrl,
                        detailsUrl = detailsUrl,
                        pageNumber = pageNumber,
                        positionInPage = index,
                        fetchedAt = fetchedAt,
                        availabilityLabel = if (item.booleanValue("not_aired")) "Скоро" else null,
                        originalReleaseYear = year,
                    ),
                )
            }
        }
    }

    private fun parseRow(
        row: Element,
        positionInPage: Int,
        fetchedAt: Long,
    ): ReleaseSummary? {
        val contentLink = row.selectFirst("a[href].no-decoration")
            ?: row.selectFirst("a[href*=/series/]")
            ?: return null
        val detailsUrl = contentLink.absoluteUrl("href").takeIf { it.isNotBlank() } ?: return null
        val titleRu = contentLink.selectFirst(".name-ru").textOrEmpty().takeIf { it.isNotBlank() } ?: return null
        val titleEn = contentLink.selectFirst(".name-en").textOrEmpty().takeIf { it.isNotBlank() }
        val posterUrl = contentLink.selectFirst(".picture-box img.thumb").absoluteUrl("src")
        val availabilityLabel = contentLink.selectFirst(".picture-box .small-block")
            .textOrEmpty()
            .takeIf { it.isNotBlank() }
        val detailsText = contentLink.selectFirst(".details-pane").textOrEmpty()
        val year = detailsText.extractYear()

        return ReleaseSummary(
            id = detailsUrl,
            kind = ReleaseKind.SERIES,
            titleRu = titleRu,
            episodeTitleRu = titleEn,
            seasonNumber = null,
            episodeNumber = null,
            releaseDateRu = year?.toString().orEmpty(),
            posterUrl = posterUrl,
            detailsUrl = detailsUrl,
            pageNumber = 1,
            positionInPage = positionInPage,
            fetchedAt = fetchedAt,
            availabilityLabel = availabilityLabel,
            originalReleaseYear = year,
        )
    }
}

private fun Map<String, kotlinx.serialization.json.JsonElement>.stringValue(key: String): String =
    this[key]?.jsonPrimitive?.content.orEmpty()

private fun Map<String, kotlinx.serialization.json.JsonElement>.booleanValue(key: String): Boolean =
    this[key]?.jsonPrimitive?.booleanOrNull ?: false
