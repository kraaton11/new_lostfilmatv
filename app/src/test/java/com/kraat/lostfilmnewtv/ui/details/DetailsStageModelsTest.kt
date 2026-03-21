package com.kraat.lostfilmnewtv.ui.details

import com.kraat.lostfilmnewtv.data.model.ReleaseDetails
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import org.junit.Assert.assertEquals
import org.junit.Test

class DetailsStageModelsTest {
    @Test
    fun buildStageUi_usesFirstTorrentRowAsDefaultActivePlayback() {
        val ui = buildDetailsStageUi(
            state = DetailsUiState(details = seriesDetails()),
            isAuthenticated = true,
            torrentRows = listOf(
                DetailsTorrentRowUiModel("row-0", "1080p", "https://example.com/1080"),
                DetailsTorrentRowUiModel("row-1", "720p", "https://example.com/720"),
            ),
            activeRowId = null,
            activeTorrServeRowId = null,
            isTorrServeBusy = false,
        )

        assertEquals("row-0", ui.activeRowId)
        assertEquals("1080p", ui.primaryAction.qualityLabel)
        assertEquals(DetailsStageActionType.OPEN_TORRSERVE, ui.primaryAction.actionType)
    }

    @Test
    fun buildStageUi_usesDirectLinkAsPrimaryActionWhenTorrServeIsUnsupported() {
        val ui = buildDetailsStageUi(
            state = DetailsUiState(details = movieDetails()),
            isAuthenticated = true,
            torrentRows = listOf(
                DetailsTorrentRowUiModel(
                    rowId = "row-0",
                    label = "WEBRip",
                    url = "magnet:?xt=urn:btih:test",
                    isTorrServeSupported = false,
                ),
            ),
            activeRowId = "row-0",
            activeTorrServeRowId = null,
            isTorrServeBusy = false,
        )

        assertEquals(DetailsStageActionType.OPEN_LINK, ui.primaryAction.actionType)
        assertEquals("WEBRip", ui.primaryAction.qualityLabel)
    }

    @Test
    fun buildStageUi_usesOnlyExistingSignalsForTechCards() {
        val ui = buildDetailsStageUi(
            state = DetailsUiState(
                details = seriesDetails(),
                showStaleBanner = true,
            ),
            isAuthenticated = false,
            torrentRows = listOf(
                DetailsTorrentRowUiModel(
                    rowId = "row-0",
                    label = "1080p",
                    url = "https://example.com/1080",
                    isTorrServeSupported = false,
                ),
            ),
            activeRowId = "row-0",
            activeTorrServeRowId = null,
            isTorrServeBusy = false,
        )

        assertEquals(listOf("quality", "delivery", "release", "access", "freshness"), ui.techCards.map { it.cardId })
        assertEquals("1080p", ui.techCards.first { it.cardId == "quality" }.value)
        assertEquals("Требуется вход", ui.techCards.first { it.cardId == "access" }.value)
        assertEquals("Кэш", ui.techCards.first { it.cardId == "freshness" }.label)
    }

    @Test
    fun buildStageUi_disablesAllTorrServeQualityActionsWhileBusy() {
        val ui = buildDetailsStageUi(
            state = DetailsUiState(details = seriesDetails()),
            isAuthenticated = true,
            torrentRows = listOf(
                DetailsTorrentRowUiModel("row-0", "1080p", "https://example.com/1080", isTorrServeSupported = true),
                DetailsTorrentRowUiModel("row-1", "720p", "https://example.com/720", isTorrServeSupported = true),
                DetailsTorrentRowUiModel("row-2", "WEBRip", "magnet:?xt=urn:btih:test", isTorrServeSupported = false),
            ),
            activeRowId = "row-0",
            activeTorrServeRowId = "row-0",
            isTorrServeBusy = true,
        )

        assertEquals(false, ui.qualityActions.first { it.rowId == "row-0" }.enabled)
        assertEquals(false, ui.qualityActions.first { it.rowId == "row-1" }.enabled)
        assertEquals(true, ui.qualityActions.first { it.rowId == "row-2" }.enabled)
        assertEquals(true, ui.secondaryActions.first().enabled)
    }
}

private fun seriesDetails(): ReleaseDetails = ReleaseDetails(
    detailsUrl = "https://example.com/series",
    kind = ReleaseKind.SERIES,
    titleRu = "Silo",
    seasonNumber = 1,
    episodeNumber = 5,
    releaseDateRu = "21 марта 2026",
    posterUrl = "https://example.com/poster.jpg",
    fetchedAt = 0L,
)

private fun movieDetails(): ReleaseDetails = ReleaseDetails(
    detailsUrl = "https://example.com/movie",
    kind = ReleaseKind.MOVIE,
    titleRu = "Dune",
    seasonNumber = null,
    episodeNumber = null,
    releaseDateRu = "21 марта 2026",
    posterUrl = "https://example.com/poster.jpg",
    fetchedAt = 0L,
)
