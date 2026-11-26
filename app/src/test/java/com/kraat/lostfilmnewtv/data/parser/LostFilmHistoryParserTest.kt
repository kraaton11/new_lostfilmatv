package com.kraat.lostfilmnewtv.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LostFilmHistoryParserTest {
    @Test
    fun keepsSiteOrderAndSkipsUnwatchedSeries() {
        val html = """
            <div class="serials-list-box">
                <div class="serial-box">
                    <img class="avatar" src="/Static/Images/123/Posters/icon.jpg">
                    <a class="body" href="/series/Beta">
                        <div class="title-ru">Бета</div><div class="title-en">Beta</div>
                    </a>
                    <a class="rate-box" href="/series/Beta/seasons">
                        <div class="bar-active"><div class="value">2</div></div>
                        <div class="bar"><div class="value">8</div></div>
                    </a>
                </div>
                <div class="serial-box">
                    <a class="body" href="/series/Unwatched"><div class="title-ru">Без просмотров</div></a>
                </div>
                <div class="serial-box">
                    <a class="body" href="/series/Alpha"><div class="title-ru">Альфа</div></a>
                    <a class="rate-box" href="/series/Alpha/seasons">
                        <div class="bar-active"><div class="value">1</div></div>
                        <div class="bar"><div class="value">3</div></div>
                    </a>
                </div>
            </div>
        """.trimIndent()

        val entries = checkNotNull(LostFilmHistoryParser().parse(html))
        assertEquals(listOf("Бета", "Альфа"), entries.map { it.titleRu })
        assertEquals("Beta", entries.first().titleEn)
        assertEquals("$BASE_URL/series/Beta", entries.first().seriesUrl)
        assertEquals("/Static/Images/123/Posters/icon.jpg", entries.first().posterUrl)
        assertEquals(2, entries.first().watchedCount)
        assertEquals(8, entries.first().totalCount)
    }

    @Test
    fun distinguishesEmptyHistoryFromRedirect() {
        val parser = LostFilmHistoryParser()
        assertEquals(emptyList<Any>(), parser.parse("""<div class="serials-list-box"></div>"""))
        assertNull(parser.parse("""<meta http-equiv="refresh" content="0; url=/">"""))
    }
}
