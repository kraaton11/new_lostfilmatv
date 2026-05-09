package com.kraat.lostfilmnewtv.data.poster

import com.kraat.lostfilmnewtv.data.model.ReleaseDetails
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
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
}
