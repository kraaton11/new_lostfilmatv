package com.kraat.lostfilmnewtv.data.parser

import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import com.kraat.lostfilmnewtv.data.model.ScheduleDay
import com.kraat.lostfilmnewtv.data.model.ScheduleItem
import com.kraat.lostfilmnewtv.data.model.ScheduleMonth
import java.time.LocalDate
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

private val activeMonthRegex = Regex("""([А-Яа-яёЁ]+)\s+(\d{4})""")
private val dayHeaderRegex = Regex("""([А-Яа-яёЁ]{2})\s+(\d{2})\.(\d{2})""")
private val listDateRegex = Regex("""([А-Яа-яёЁ]{2}),\s*(\d{2})\.(\d{2})\.(\d{4})""")
private val goToUrlRegex = Regex("""goTo\(['"]([^'"]+)['"]""")
private val seasonEpisodeRegex = Regex("""(\d+)\s*сезон\s*(\d+)\s*серия""", RegexOption.IGNORE_CASE)
private val monthNumbers = mapOf(
    "январь" to 1,
    "февраль" to 2,
    "март" to 3,
    "апрель" to 4,
    "май" to 5,
    "июнь" to 6,
    "июль" to 7,
    "август" to 8,
    "сентябрь" to 9,
    "октябрь" to 10,
    "ноябрь" to 11,
    "декабрь" to 12,
)

class LostFilmScheduleParser {
    fun parse(html: String): ScheduleMonth {
        val document = Jsoup.parse(html, BASE_URL)
        val title = document.selectFirst(".schedule-list-page .active-month")
            .textOrEmpty()
            .ifBlank { document.selectFirst(".schedule-list-page .title-ru").textOrEmpty() }
            .ifBlank { "Расписание" }
        val listSchedule = parseListSchedule(document.selectFirst("table.schedule-list"), title)
        if (listSchedule != null) {
            return listSchedule
        }

        return parseTableSchedule(document.select(".schedule-list-table tr"), title)
    }

    private fun parseTableSchedule(rows: Iterable<Element>, title: String): ScheduleMonth {
        val activeMonth = parseActiveMonth(title)
        val dayBuilders = linkedMapOf<LocalDate, ScheduleDayBuilder>()
        var currentWeek: List<ScheduleDayBuilder?> = emptyList()

        rows.forEach { row ->
            val headers = row.select("th")
            if (headers.isNotEmpty()) {
                currentWeek = headers
                    .drop(1)
                    .dropLast(1)
                    .mapNotNullHeader(activeMonth, dayBuilders)
                return@forEach
            }

            if (currentWeek.isEmpty()) return@forEach
            row.select("td")
                .drop(1)
                .dropLast(1)
                .forEachIndexed { index, cell ->
                    val day = currentWeek.getOrNull(index) ?: return@forEachIndexed
                    parseCellItems(cell).forEach(day.items::add)
                }
        }

        return ScheduleMonth(
            title = title,
            days = dayBuilders.values
                .map { builder ->
                    ScheduleDay(
                        date = builder.date,
                        label = builder.label,
                        isToday = builder.isToday,
                        items = builder.items,
                    )
                }
                .filter { it.items.isNotEmpty() }
                .sortedBy { it.date },
        )
    }

    private fun parseListSchedule(table: Element?, title: String): ScheduleMonth? {
        if (table == null) return null

        val sectionBuilders = linkedMapOf<String, ScheduleListSectionBuilder>()
        var currentSection = ""
        table.select("tr").forEach { row ->
            row.selectFirst("th")?.let { header ->
                currentSection = header.text().normalizeText().lowercase()
                return@forEach
            }

            val alpha = row.selectFirst("td.alpha") ?: return@forEach
            val delta = row.selectFirst("td.delta") ?: return@forEach
            val dateMatch = listDateRegex.find(delta.text().normalizeText()) ?: return@forEach
            val day = dateMatch.groupValues[2].toIntOrNull() ?: return@forEach
            val month = dateMatch.groupValues[3].toIntOrNull() ?: return@forEach
            val year = dateMatch.groupValues[4].toIntOrNull() ?: return@forEach
            val date = LocalDate.of(year, month, day)
            val dateLabel = dateMatch.value.normalizeText()

            val targetUrl = listOfNotNull(
                row.selectFirst("td.beta")?.onclickGoToUrl(),
                row.selectFirst("td.gamma")?.onclickGoToUrl(),
                delta.onclickGoToUrl(),
                alpha.onclickGoToUrl(),
            ).firstOrNull().orEmpty()
            val kind = when {
                targetUrl.contains("/movies/", ignoreCase = true) -> ReleaseKind.MOVIE
                targetUrl.contains("/series/", ignoreCase = true) -> ReleaseKind.SERIES
                else -> return@forEach
            }
            val titleText = alpha.selectFirst(".title-block .ru")
                .textOrEmpty()
                .ifBlank { alpha.attr("title").normalizeText() }
                .takeIf { it.isNotBlank() }
                ?: return@forEach
            val episodeLabel = row.selectFirst("td.beta .count")
                .textOrEmpty()
                .toCompactEpisodeLabel()
                .takeIf { it.isNotBlank() }
            val episodeTitle = row.selectFirst("td.gamma")
                ?.ownText()
                ?.normalizeText()
                ?.takeIf { it.isNotBlank() }
            val relativeDateLabel = delta.selectFirst(".when")
                .textOrEmpty()
                .takeIf { it.isNotBlank() }
            val posterUrl = alpha.selectFirst("img")
                .absoluteUrl("src")
                .takeIf { it.isLostFilmScheduleImageUrl() }

            val sectionKey = currentSection.ifBlank { dateLabel }.lowercase()
            val builder = sectionBuilders.getOrPut(sectionKey) {
                ScheduleListSectionBuilder(
                    date = date,
                    label = currentSection.toScheduleSectionLabel().ifBlank { dateLabel },
                    isToday = currentSection == "сегодня" || delta.text().contains("сегодня", ignoreCase = true),
                )
            }
            if (currentSection == "сегодня" || delta.text().contains("сегодня", ignoreCase = true)) {
                builder.isToday = true
            }
            builder.items += ScheduleItem(
                title = titleText,
                episodeLabel = episodeLabel,
                targetUrl = targetUrl,
                kind = kind,
                posterUrl = posterUrl,
                releaseDateLabel = dateLabel,
                episodeTitle = episodeTitle,
                relativeDateLabel = relativeDateLabel,
            )
        }

        return ScheduleMonth(
            title = title,
            days = sectionBuilders.values
                .map { builder ->
                    ScheduleDay(
                        date = builder.date,
                        label = builder.label,
                        isToday = builder.isToday,
                        items = builder.items,
                    )
                }
                .filter { it.items.isNotEmpty() }
        )
    }

    private fun List<Element>.mapNotNullHeader(
        activeMonth: ActiveMonth?,
        dayBuilders: LinkedHashMap<LocalDate, ScheduleDayBuilder>,
    ): List<ScheduleDayBuilder?> = map { header ->
        val text = header.text().normalizeText()
        val match = dayHeaderRegex.find(text) ?: return@map null
        val dayOfWeek = match.groupValues[1]
        val day = match.groupValues[2].toIntOrNull() ?: return@map null
        val month = match.groupValues[3].toIntOrNull() ?: return@map null
        val year = activeMonth?.yearFor(month) ?: return@map null
        val date = LocalDate.of(year, month, day)
        val isToday = header.hasClass("today") || text.contains("Сегодня", ignoreCase = true)
        dayBuilders.getOrPut(date) {
            ScheduleDayBuilder(
                date = date,
                label = "$dayOfWeek ${match.groupValues[2]}.${match.groupValues[3]}",
                isToday = isToday,
            )
        }.also { builder ->
            if (isToday) builder.isToday = true
        }
    }

    private fun parseCellItems(cell: Element): List<ScheduleItem> {
        return cell.select("a.title[href]").mapNotNull { link ->
            val targetUrl = link.absoluteUrl("href")
            val kind = when {
                targetUrl.contains("/movies/", ignoreCase = true) -> ReleaseKind.MOVIE
                targetUrl.contains("/series/", ignoreCase = true) -> ReleaseKind.SERIES
                else -> return@mapNotNull null
            }
            val title = link.ownText().normalizeText().takeIf { it.isNotBlank() }
                ?: link.attr("title").normalizeText().takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val episodeLabel = link.selectFirst("span")
                ?.text()
                ?.normalizeText()
                ?.takeIf { it.isNotBlank() }
            val posterUrl = cell.selectFirst("img")
                .absoluteUrl("src")
                .takeIf { it.isLostFilmScheduleImageUrl() }

            ScheduleItem(
                title = title,
                episodeLabel = episodeLabel,
                targetUrl = targetUrl,
                kind = kind,
                posterUrl = posterUrl,
            )
        }
    }

    private fun parseActiveMonth(title: String): ActiveMonth? {
        val match = activeMonthRegex.find(title.normalizeText()) ?: return null
        val monthName = match.groupValues[1].lowercase().replace("ё", "е")
        val month = monthNumbers[monthName] ?: return null
        val year = match.groupValues[2].toIntOrNull() ?: return null
        return ActiveMonth(month = month, year = year)
    }
}

private fun Element.onclickGoToUrl(): String =
    goToUrlRegex.find(attr("onclick"))
        ?.groupValues
        ?.getOrNull(1)
        ?.let(::resolveUrl)
        .orEmpty()

private fun String.toCompactEpisodeLabel(): String {
    val normalized = normalizeText()
    val match = seasonEpisodeRegex.find(normalized) ?: return normalized
    val season = match.groupValues[1].toIntOrNull() ?: return normalized
    val episode = match.groupValues[2].toIntOrNull() ?: return normalized
    return "${season}х${episode.toString().padStart(2, '0')}"
}

private fun String.toScheduleSectionLabel(): String =
    when (normalizeText().lowercase()) {
        "сегодня" -> "Сегодня"
        "на этой неделе" -> "На этой неделе"
        "на следующей неделе" -> "На следующей неделе"
        "позже" -> "Позже"
        else -> normalizeText()
    }

private data class ActiveMonth(
    val month: Int,
    val year: Int,
) {
    fun yearFor(itemMonth: Int): Int = when {
        month == 1 && itemMonth == 12 -> year - 1
        month == 12 && itemMonth == 1 -> year + 1
        else -> year
    }
}

private data class ScheduleDayBuilder(
    val date: LocalDate,
    val label: String,
    var isToday: Boolean,
    val items: MutableList<ScheduleItem> = mutableListOf(),
)

private data class ScheduleListSectionBuilder(
    val date: LocalDate,
    val label: String,
    var isToday: Boolean,
    val items: MutableList<ScheduleItem> = mutableListOf(),
)

private fun String.isLostFilmScheduleImageUrl(): Boolean {
    val normalized = lowercase()
    return normalized.startsWith("$BASE_URL/static/") ||
        normalized.startsWith("https://static.lostfilm.") ||
        normalized.startsWith("http://static.lostfilm.")
}
