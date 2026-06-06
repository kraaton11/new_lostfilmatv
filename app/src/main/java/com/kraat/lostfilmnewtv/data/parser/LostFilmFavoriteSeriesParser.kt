package com.kraat.lostfilmnewtv.data.parser

import org.jsoup.Jsoup

data class FavoriteSeriesRef(
    val titleRu: String,
    val posterUrl: String,
    val seriesUrl: String,
)

class LostFilmFavoriteSeriesParser {
    fun parse(html: String): List<FavoriteSeriesRef> {
        val document = Jsoup.parse(html, BASE_URL)

        return document.select(".serials-list-box .serial-box")
            .mapNotNull { box ->
                if (box.selectFirst(".subscribe-box.active") == null) {
                    return@mapNotNull null
                }

                val titleRu = box.selectFirst(".title-ru").textOrEmpty()
                val seriesHref = box.selectFirst("a.body")?.attr("href").orEmpty().trim()
                val seriesUrl = if (seriesHref.isBlank()) "" else resolveUrl(seriesHref)
                val posterUrl = ""

                if (titleRu.isBlank() || seriesUrl.isBlank()) {
                    return@mapNotNull null
                }

                FavoriteSeriesRef(
                    titleRu = titleRu,
                    posterUrl = posterUrl,
                    seriesUrl = seriesUrl,
                )
            }
            .distinctBy { it.seriesUrl }
    }
}
