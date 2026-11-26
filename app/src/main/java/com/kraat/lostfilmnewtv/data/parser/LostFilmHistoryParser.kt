package com.kraat.lostfilmnewtv.data.parser

import com.kraat.lostfilmnewtv.data.model.WatchHistoryEntry
import org.jsoup.Jsoup

class LostFilmHistoryParser {
    fun parse(html: String): List<WatchHistoryEntry>? {
        val list = Jsoup.parse(html, BASE_URL).selectFirst(".serials-list-box") ?: return null
        return list.select(".serial-box").mapNotNull { box ->
            val watched = box.selectFirst(".rate-box .bar-active .value").textOrEmpty().toIntOrNull()
                ?: return@mapNotNull null
            val total = box.selectFirst(".rate-box .bar .value").textOrEmpty().toIntOrNull()
                ?: return@mapNotNull null
            if (watched <= 0 || total < watched) return@mapNotNull null

            val link = box.selectFirst("a.body") ?: return@mapNotNull null
            val path = link.attr("href").trim().trimEnd('/')
            val titleRu = link.selectFirst(".title-ru").textOrEmpty()
            val titleEn = link.selectFirst(".title-en").textOrEmpty()
            if (!seriesPath.matches(path) || (titleRu.isBlank() && titleEn.isBlank())) {
                return@mapNotNull null
            }

            WatchHistoryEntry(
                titleRu = titleRu.ifBlank { titleEn },
                titleEn = titleEn,
                posterUrl = box.lostFilmImagePath("img.avatar"),
                seriesUrl = resolveUrl(path),
                watchedCount = watched,
                totalCount = total,
            )
        }.distinctBy { it.seriesUrl }
    }

    private companion object {
        val seriesPath = Regex("""/series/[^/?#]+""")
    }
}
