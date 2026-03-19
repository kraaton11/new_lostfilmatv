package com.kraat.lostfilmnewtv.ui.details

import com.kraat.lostfilmnewtv.data.model.ReleaseDetails
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import com.kraat.lostfilmnewtv.data.model.TorrentLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class DetailsTorrentModelsTest {
    @Test
    fun toTorrentRows_mapsLinksIntoStableUiRows() {
        val details = ReleaseDetails(
            detailsUrl = "https://example.com/details",
            kind = ReleaseKind.SERIES,
            titleRu = "9-1-1",
            seasonNumber = 1,
            episodeNumber = 1,
            releaseDateRu = "19 марта 2026",
            posterUrl = "https://example.com/poster.jpg",
            fetchedAt = 123L,
            torrentLinks = listOf(
                TorrentLink(label = "1080p", url = "https://example.com/1080"),
                TorrentLink(label = "720p", url = "https://example.com/720"),
            ),
        )

        val rows = details.toTorrentRows()

        assertEquals(
            listOf(
                DetailsTorrentRowUiModel(
                    rowId = "torrent-row-0",
                    label = "1080p",
                    url = "https://example.com/1080",
                    isTorrServeSupported = true,
                ),
                DetailsTorrentRowUiModel(
                    rowId = "torrent-row-1",
                    label = "720p",
                    url = "https://example.com/720",
                    isTorrServeSupported = true,
                ),
            ),
            rows,
        )
    }

    @Test
    fun toTorrentRows_returnsEmptyListWhenNoLinksAvailable() {
        val details = ReleaseDetails(
            detailsUrl = "https://example.com/details",
            kind = ReleaseKind.MOVIE,
            titleRu = "Movie",
            seasonNumber = null,
            episodeNumber = null,
            releaseDateRu = "19 марта 2026",
            posterUrl = "https://example.com/poster.jpg",
            fetchedAt = 123L,
        )

        val rows = details.toTorrentRows()

        assertFalse(rows.iterator().hasNext())
    }

    @Test
    fun qualityStatus_showsLoginHintWhenNotAuthenticatedAndNoRows() {
        val status = qualityStatusText(
            hasDetails = true,
            torrentRowsCount = 0,
            isAuthenticated = false,
        )

        assertEquals("Войдите в LostFilm, чтобы загрузить варианты качества", status)
    }

    @Test
    fun qualityStatus_showsMissingQualityHintWhenAuthenticatedAndNoRows() {
        val status = qualityStatusText(
            hasDetails = true,
            torrentRowsCount = 0,
            isAuthenticated = true,
        )

        assertEquals("Варианты качества не найдены", status)
    }

    @Test
    fun qualityStatus_showsFoundCountWhenRowsExist() {
        val status = qualityStatusText(
            hasDetails = true,
            torrentRowsCount = 3,
            isAuthenticated = true,
        )

        assertEquals("Найдено 3 варианта качества", status)
    }

    @Test
    fun qualityStatus_isHiddenWithoutLoadedDetails() {
        val status = qualityStatusText(
            hasDetails = false,
            torrentRowsCount = 0,
            isAuthenticated = false,
        )

        assertNull(status)
    }
}
