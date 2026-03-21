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
    fun buildStageUi_hidesSecondaryOpenLinkAction_inReadableMode() {
        val ui = buildDetailsStageUi(
            state = DetailsUiState(details = seriesDetails()),
            isAuthenticated = true,
            torrentRows = listOf(
                DetailsTorrentRowUiModel("row-0", "1080p", "https://example.com/1080"),
            ),
            activeRowId = "row-0",
            activeTorrServeRowId = null,
            isTorrServeBusy = false,
        )

        assertEquals(emptyList<DetailsStageActionUiModel>(), ui.secondaryActions)
    }

    @Test
    fun buildStageUi_exposesCompactHeroMetaAndStatusLines() {
        val ui = buildDetailsStageUi(
            state = DetailsUiState(
                details = seriesDetails(),
            ),
            isAuthenticated = true,
            torrentRows = listOf(
                DetailsTorrentRowUiModel(
                    rowId = "row-0",
                    label = "1080p",
                    url = "https://example.com/1080",
                    isTorrServeSupported = true,
                ),
            ),
            activeRowId = "row-0",
            activeTorrServeRowId = null,
            isTorrServeBusy = false,
        )

        assertEquals("Сезон 1, серия 5", ui.heroMetaLine)
        assertEquals("1080p • TorrServe • свежие данные", ui.heroStatusLine)
    }

    @Test
    fun buildStageUi_usesSingleErrorStatusLine_forRowScopedTorrServeFallback() {
        val ui = buildDetailsStageUi(
            state = DetailsUiState(
                details = movieDetails(),
                showStaleBanner = true,
            ),
            isAuthenticated = false,
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
            torrServeMessageText = "Не удалось открыть TorrServe",
        )

        assertEquals("21 марта 2026", ui.heroMetaLine)
        assertEquals("Не удалось открыть TorrServe", ui.heroStatusLine)
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
        assertEquals(emptyList<DetailsStageActionUiModel>(), ui.secondaryActions)
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
