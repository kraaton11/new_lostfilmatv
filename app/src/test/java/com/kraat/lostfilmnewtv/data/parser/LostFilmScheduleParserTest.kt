package com.kraat.lostfilmnewtv.data.parser

import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LostFilmScheduleParserTest {
    private val parser = LostFilmScheduleParser()

    @Test
    fun parse_readsDaysAndItemsFromScheduleTable() {
        val schedule = parser.parse(fixture("schedule.html"))

        assertEquals("май 2026 г.", schedule.title)
        assertEquals(3, schedule.days.size)

        val firstDay = schedule.days.first()
        assertEquals(LocalDate.of(2026, 4, 27), firstDay.date)
        assertEquals("Пн 27.04", firstDay.label)
        assertEquals("Грызня", firstDay.items.single().title)
        assertEquals("2х07", firstDay.items.single().episodeLabel)
        assertEquals("https://www.lostfilm.today/series/BEEF/season_2/episode_7/", firstDay.items.single().targetUrl)
    }

    @Test
    fun parse_marksTodayAndKeepsAdditionalEpisodes() {
        val today = parser.parse(fixture("schedule.html")).days.single { it.isToday }

        assertEquals(LocalDate.of(2026, 4, 29), today.date)
        assertTrue(today.isToday)
        assertEquals(2, today.items.size)
        assertEquals("1х7-8", today.items[0].episodeLabel)
        assertEquals("ДОПх01", today.items[1].episodeLabel)
    }

    @Test
    fun parse_detectsMovieItems() {
        val movie = parser.parse(fixture("schedule.html"))
            .days
            .flatMap { it.items }
            .single { it.title == "Звонок" }

        assertEquals(ReleaseKind.MOVIE, movie.kind)
        assertEquals("https://www.lostfilm.today/movies/Ringu", movie.targetUrl)
    }

    @Test
    fun parse_readsSectionsAndPostersFromScheduleList() {
        val schedule = parser.parse(fixture("schedule-list.html"))

        assertEquals("Расписание", schedule.title)
        assertEquals(
            listOf("Сегодня", "На этой неделе", "На следующей неделе", "Позже"),
            schedule.days.map { it.label },
        )

        val today = schedule.days.first()
        val firstItem = today.items.single()
        assertTrue(today.isToday)
        assertEquals(LocalDate.of(2026, 6, 2), today.date)
        assertEquals("Безумцы", firstItem.title)
        assertEquals("1х02", firstItem.episodeLabel)
        assertEquals("Ladies Room", firstItem.episodeTitle)
        assertEquals("Вт, 02.06.2026", firstItem.releaseDateLabel)
        assertEquals("https://www.lostfilm.today/series/Mad_Men/season_1/episode_2/", firstItem.targetUrl)
        assertEquals("https://www.lostfilm.today/Static/Images/1136/Posters/icon.jpg", firstItem.posterUrl)

        val nextItem = schedule.days[1].items.single()
        assertEquals("Бухта вдов", nextItem.title)
        assertEquals("через 3 дня", nextItem.relativeDateLabel)
    }
}
