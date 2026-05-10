package com.kraat.lostfilmnewtv.data.db

import com.kraat.lostfilmnewtv.data.model.ReleaseDetails
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseDetailsEntityTest {

    @Test
    fun modelToEntityAndBack_preservesMovieOverview() {
        val model = ReleaseDetails(
            detailsUrl = "https://example.com/movie",
            kind = ReleaseKind.MOVIE,
            titleRu = "Movie",
            seasonNumber = null,
            episodeNumber = null,
            releaseDateRu = "16.03.2026",
            posterUrl = "https://example.com/poster.jpg",
            fetchedAt = 1L,
            episodeOverviewSource = "TMDB_EN",
            movieOverviewRu = "Описание фильма из TMDB.",
        )

        val restored = ReleaseDetailsEntity.fromModel(model).toModel()

        assertEquals("TMDB_EN", restored.episodeOverviewSource)
        assertEquals("Описание фильма из TMDB.", restored.movieOverviewRu)
    }
}
