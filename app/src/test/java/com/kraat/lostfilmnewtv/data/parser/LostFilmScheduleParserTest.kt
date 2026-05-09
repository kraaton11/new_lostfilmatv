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
}
