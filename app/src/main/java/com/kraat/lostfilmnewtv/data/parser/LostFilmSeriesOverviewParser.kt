package com.kraat.lostfilmnewtv.data.parser

import com.kraat.lostfilmnewtv.data.model.SeriesOverview
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

private val labeledHtmlBreakRegex = Regex("""<br\s*/?>""", setOf(RegexOption.IGNORE_CASE))
private val plotSectionRegex = Regex(
    """<strong[^>]*>\s*Сюжет\s*</strong>\s*<br\s*/?>""",
    setOf(RegexOption.IGNORE_CASE),
)

class LostFilmSeriesOverviewParser {
    fun parse(
        html: String,
        seriesUrl: String,
    ): SeriesOverview {
        val document = Jsoup.parse(html, BASE_URL)
        val leftBox = document.selectFirst(".details-pane .left-box")
        val rightBox = document.selectFirst(".details-pane .right-box")
        val (descriptionRu, plotRu) = document.descriptionAndPlot()

        return SeriesOverview(
            seriesUrl = resolveUrl(seriesUrl),
            titleRu = document.selectFirst(".title-block .header .title-ru, h1.title-ru").textOrEmpty(),
            titleEn = document.selectFirst(".title-block .header .title-en, h2.title-en")
                ?.text()
                ?.normalizeOverviewText(),
            statusRu = document.selectFirst(".title-block .status")
                ?.text()
                ?.substringAfter("Статус:")
                ?.normalizeOverviewText(),
            posterUrl = document.selectFirst(".main_poster img, .image-block .main_poster img")
                .absoluteUrl("src")
                .ifBlank { null },
            premiereDateRu = leftBox.extractLabeledValue("Премьера:"),
            channelCountryRu = leftBox.extractLabeledValue("Канал, Страна:"),
            imdbRating = leftBox.extractLabeledValue("Рейтинг IMDb:"),
            genresRu = rightBox.extractLabeledValue("Жанр:"),
            typesRu = rightBox.extractLabeledValue("Тип:"),
            officialSiteUrl = rightBox
                ?.selectFirst("""a[href^=http], a[href^=https]""")
                ?.attr("href")
                ?.normalizeOverviewText(),
            descriptionRu = descriptionRu,
            plotRu = plotRu,
        )
    }
}

private fun Document.descriptionAndPlot(): Pair<String?, String?> {
    val rawHtml = selectFirst(".text-block.description .body .body")
        ?.html()
        .orEmpty()

    if (rawHtml.isBlank()) {
        return null to null
    }

    val parts = plotSectionRegex.split(rawHtml, limit = 2)
    val description = parts.getOrNull(0)?.htmlFragmentToText()
    val plot = parts.getOrNull(1)?.htmlFragmentToText()

    return description to plot
}

private fun Element?.extractLabeledValue(label: String): String? {
    val html = this?.html().orEmpty()
    if (html.isBlank()) {
        return null
    }

    val regex = Regex(
        """${Regex.escape(label)}\s*(.*?)${labeledHtmlBreakRegex.pattern}""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    val fragment = regex.find(html)?.groupValues?.getOrNull(1).orEmpty()
    return fragment.htmlFragmentToText()
}

private fun String?.htmlFragmentToText(): String? {
    val fragment = this.orEmpty().trim()
    if (fragment.isBlank()) {
        return null
    }

    val normalizedHtml = fragment
        .replace("&nbsp;", " ")
        .replace(labeledHtmlBreakRegex, "\n")

    return Jsoup.parseBodyFragment(normalizedHtml)
        .text()
        .normalizeOverviewText()
        .takeIf { it.isNotBlank() }
}

private fun String.normalizeOverviewText(): String = normalizeText().replace(Regex("""\s+"""), " ")
