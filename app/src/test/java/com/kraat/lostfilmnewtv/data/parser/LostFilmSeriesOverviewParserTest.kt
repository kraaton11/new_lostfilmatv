package com.kraat.lostfilmnewtv.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LostFilmSeriesOverviewParserTest {
    @Test
    fun parsesOverviewSections_fromSeriesRootPage() {
        val html = """
            <html>
                <body>
                    <div class="title-block">
                        <div class="header">
                            <h1 class="title-ru">Пацаны</h1>
                            <h2 class="title-en">The Boys</h2>
                        </div>
                        <div class="status">Статус: Идет 5 сезон. Следующая серия: 13 апреля 2026 года</div>
                    </div>
                    <div class="title-block">
                        <div class="image-block">
                            <div class="main_poster">
                                <img src="/Static/Images/442/Posters/poster.jpg" />
                            </div>
                        </div>
                        <div class="details-pane">
                            <div class="left-box">
                                Премьера: <a href="/series/The_Boys/season_1/episode_1">26 июля 2019</a><meta itemprop="dateCreated" content="2019-07-26" /><br />
                                Канал, Страна:
                                <a href="/series/?type=search&c=85&s=2&t=0">Amazon Prime Video</a>
                                (США)
                                <br />
                                Рейтинг IMDb: 8.6<br />
                            </div>
                            <div class="right-box">
                                Жанр: <span itemprop="genre"><a href="/series/?g=2">Драма</a>, <a href="/series/?g=25">Фантастика</a></span><br />
                                Тип: <a href="/series/?r=55">По комиксам</a>, <a href="/series/?r=20">Сверхспособности</a><br />
                                Официальный сайт: <a href="https://www.amazon.com/dp/B07QQQ52B3">https://www.amazon.com/dp/B07QQQ52B3</a><br />
                            </div>
                        </div>
                    </div>
                    <div class="text-block description">
                        <div class="body">
                            <div class="body" itemprop="description">
                                Сериал «Пацаны» выбивается из супергеройской волны.<br />&nbsp;<br />
                                <strong class="bb">Сюжет</strong><br />
                                Билли Мясник собирает команду против «Семерки».
                            </div>
                        </div>
                    </div>
                </body>
            </html>
        """.trimIndent()

        val overview = LostFilmSeriesOverviewParser().parse(
            html = html,
            seriesUrl = "/series/The_Boys/",
        )

        assertEquals("Пацаны", overview.titleRu)
        assertEquals("The Boys", overview.titleEn)
        assertEquals("Идет 5 сезон. Следующая серия: 13 апреля 2026 года", overview.statusRu)
        assertEquals("26 июля 2019", overview.premiereDateRu)
        assertEquals("Amazon Prime Video (США)", overview.channelCountryRu)
        assertEquals("8.6", overview.imdbRating)
        assertEquals("Драма, Фантастика", overview.genresRu)
        assertEquals("По комиксам, Сверхспособности", overview.typesRu)
        assertEquals("https://www.amazon.com/dp/B07QQQ52B3", overview.officialSiteUrl)
        assertTrue(overview.posterUrl?.endsWith("/Static/Images/442/Posters/poster.jpg") == true)
        assertEquals("Сериал «Пацаны» выбивается из супергеройской волны.", overview.descriptionRu)
        assertEquals("Билли Мясник собирает команду против «Семерки».", overview.plotRu)
    }
}
