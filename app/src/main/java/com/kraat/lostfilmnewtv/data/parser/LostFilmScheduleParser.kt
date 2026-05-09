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
            .ifBlank { "Расписание" }
        val activeMonth = parseActiveMonth(title)
        val dayBuilders = linkedMapOf<LocalDate, ScheduleDayBuilder>()
        var currentWeek: List<ScheduleDayBuilder?> = emptyList()

        document.select(".schedule-list-table tr").forEach { row ->
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

private fun String.isLostFilmScheduleImageUrl(): Boolean {
    val normalized = lowercase()
    return normalized.startsWith("$BASE_URL/static/") ||
        normalized.startsWith("https://static.lostfilm.") ||
        normalized.startsWith("http://static.lostfilm.")
}
