package com.kraat.lostfilmnewtv.data.poster

import com.kraat.lostfilmnewtv.data.model.ReleaseDetails
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import com.kraat.lostfilmnewtv.data.model.ReleaseSummary
import com.kraat.lostfilmnewtv.data.model.TmdbImageUrls
import org.junit.Assert.assertEquals
import org.junit.Test

class TmdbPosterEnricherTest {

    @Test
    fun enrichDetails_usesSeriesOverviewWhenEpisodeOverviewIsMissing() {
        val details = ReleaseDetails(
            detailsUrl = "https://example.com/series/episode",
            kind = ReleaseKind.SERIES,
            titleRu = "Series",
            seasonNumber = 1,
            episodeNumber = 2,
            releaseDateRu = "16.03.2026",
            posterUrl = "https://example.com/poster.jpg",
            fetchedAt = 1L,
        )

        val enriched = TmdbPosterEnricher.enrichDetails(
            details = details,
            tmdbUrls = TmdbImageUrls(
                posterUrl = "",
                backdropUrl = "",
                seriesOverviewRu = "Описание сериала из TMDB.",
            ),
        )

        assertEquals("Описание сериала из TMDB.", enriched.episodeOverviewRu)
    }

    @Test
    fun enrichSummary_keepsExistingArtwork_whenResolverFoundNothing() {
        val summary = ReleaseSummary(
            id = "https://example.com/series/episode",
            kind = ReleaseKind.SERIES,
            titleRu = "Series",
            episodeTitleRu = null,
            seasonNumber = 1,
            episodeNumber = 2,
            releaseDateRu = "16.03.2026",
            posterUrl = "https://st.kp.yandex.net/images/film_big/79920.jpg",
            detailsUrl = "https://example.com/series/episode",
            pageNumber = 1,
            positionInPage = 0,
            fetchedAt = 1L,
            backdropUrl = "https://image.tmdb.org/t/p/w1280/backdrop.jpg",
            seriesOverviewRu = "Описание, которое уже есть.",
        )

        val enriched = TmdbPosterEnricher.enrichSummary(summary, tmdbUrls = null)

        assertEquals("Ответ «ничего не нашлось» не должен затирать постер", summary.posterUrl, enriched.posterUrl)
        assertEquals(summary.backdropUrl, enriched.backdropUrl)
        assertEquals(summary.seriesOverviewRu, enriched.seriesOverviewRu)
    }

    @Test
    fun enrichDetails_keepsExistingArtwork_whenResolverFoundNothing() {
        val details = ReleaseDetails(
            detailsUrl = "https://example.com/series/episode",
            kind = ReleaseKind.SERIES,
            titleRu = "Series",
            seasonNumber = 1,
            episodeNumber = 2,
            releaseDateRu = "16.03.2026",
            posterUrl = "https://st.kp.yandex.net/images/film_big/79920.jpg",
            fetchedAt = 1L,
            backdropUrl = "https://image.tmdb.org/t/p/w1280/backdrop.jpg",
            episodeOverviewRu = "Описание, которое уже есть.",
        )

        val enriched = TmdbPosterEnricher.enrichDetails(details, tmdbUrls = null)

        assertEquals("Ответ «ничего не нашлось» не должен затирать постер", details.posterUrl, enriched.posterUrl)
        assertEquals(details.backdropUrl, enriched.backdropUrl)
        assertEquals(details.episodeOverviewRu, enriched.episodeOverviewRu)
    }
}
