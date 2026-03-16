package com.kraat.lostfilmnewtv.data.db

import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import com.kraat.lostfilmnewtv.data.model.ReleaseSummary
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseSummaryEntityTest {

    @Test
    fun modelToEntityAndBack_preservesWatchedState() {
        val model = ReleaseSummary(
            id = "id",
            kind = ReleaseKind.SERIES,
            titleRu = "Title",
            episodeTitleRu = "Episode",
            seasonNumber = 1,
            episodeNumber = 2,
            releaseDateRu = "16.03.2026",
            posterUrl = "https://example.com/poster.jpg",
            detailsUrl = "https://example.com/details",
            pageNumber = 1,
            positionInPage = 0,
            fetchedAt = 1L,
            isWatched = true,
        )

        val restored = ReleaseSummaryEntity.fromModel(model).toModel()

        assertTrue(restored.isWatched)
    }
}
