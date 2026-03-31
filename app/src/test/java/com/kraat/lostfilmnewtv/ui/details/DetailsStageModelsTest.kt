package com.kraat.lostfilmnewtv.ui.details

import com.kraat.lostfilmnewtv.data.model.ReleaseDetails
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import org.junit.Assert.assertEquals
import org.junit.Test

class DetailsStageModelsTest {
    @Test
    fun buildStageUi_usesResolvedPlaybackRow_asPrimaryWatchAction() {
        val playbackRow = DetailsTorrentRowUiModel(
            rowId = "row-1",
            label = "720p",
            url = "https://example.com/720",
            isTorrServeSupported = true,
        )

        val ui = buildDetailsStageUi(
            state = DetailsUiState(details = seriesDetails()),
            isAuthenticated = true,
            availableTorrentRowsCount = 2,
            playbackRow = playbackRow,
            activeTorrServeRowId = null,
            isTorrServeBusy = false,
        )

        assertEquals("row-1", ui.activeRowId)
        assertEquals("Смотреть", ui.primaryAction.label)
        assertEquals("row-1", ui.primaryAction.rowId)
        assertEquals("720p", ui.primaryAction.qualityLabel)
        assertEquals("720p", ui.primaryAction.subtitle)
        assertEquals(DetailsStageActionType.OPEN_TORRSERVE, ui.primaryAction.actionType)
    }

    @Test
    fun buildStageUi_addsGuideAction_forSeriesDetails() {
        val ui = buildDetailsStageUi(
            state = DetailsUiState(details = seriesDetails()),
            isAuthenticated = true,
            availableTorrentRowsCount = 1,
            playbackRow = DetailsTorrentRowUiModel("row-0", "1080p", "https://example.com/1080"),
            activeTorrServeRowId = null,
            isTorrServeBusy = false,
        )

        assertEquals(
            true,
            ui.secondaryActions.any { it.actionType == DetailsStageActionType.OPEN_SERIES_GUIDE },
        )
    }

    @Test
    fun buildStageUi_exposesCompactHeroMetaLines() {
        val ui = buildDetailsStageUi(
            state = DetailsUiState(
                details = seriesDetails(),
            ),
            isAuthenticated = true,
            availableTorrentRowsCount = 1,
            playbackRow = DetailsTorrentRowUiModel(
                rowId = "row-0",
                label = "1080p",
                url = "https://example.com/1080",
                isTorrServeSupported = true,
            ),
            activeTorrServeRowId = null,
            isTorrServeBusy = false,
        )

        assertEquals("The Engineer", ui.heroEpisodeTitle)
        assertEquals("Сезон 1 • Серия 5", ui.heroMetaLine)
    }

    @Test
    fun buildStageUi_usesMovieMetaInHero() {
        val ui = buildDetailsStageUi(
            state = DetailsUiState(
                details = movieDetails(),
            ),
            isAuthenticated = true,
            availableTorrentRowsCount = 1,
            playbackRow = DetailsTorrentRowUiModel(
                rowId = "row-0",
                label = "1080p",
                url = "https://example.com/1080",
                isTorrServeSupported = true,
            ),
            activeTorrServeRowId = null,
            isTorrServeBusy = false,
        )

        assertEquals("", ui.heroEpisodeTitle)
        assertEquals("Фильм", ui.heroMetaLine)
    }

    @Test
    fun buildStageUi_handlesTorrServeFallback() {
        val ui = buildDetailsStageUi(
            state = DetailsUiState(
                details = movieDetails(),
                showStaleBanner = true,
            ),
            isAuthenticated = false,
            availableTorrentRowsCount = 1,
            playbackRow = DetailsTorrentRowUiModel(
                rowId = "row-0",
                label = "1080p",
                url = "https://example.com/1080",
                isTorrServeSupported = true,
            ),
            activeTorrServeRowId = null,
            isTorrServeBusy = false,
            torrServeMessageText = "Не удалось открыть TorrServe",
        )

        assertEquals("Dune", ui.title)
    }

    @Test
    fun buildStageUi_disablesPrimaryPlaybackActionWhileBusy() {
        val ui = buildDetailsStageUi(
            state = DetailsUiState(details = seriesDetails()),
            isAuthenticated = true,
            availableTorrentRowsCount = 2,
            playbackRow = DetailsTorrentRowUiModel("row-0", "1080p", "https://example.com/1080", isTorrServeSupported = true),
            activeTorrServeRowId = "row-0",
            isTorrServeBusy = true,
        )

        assertEquals(false, ui.primaryAction.enabled)
        assertEquals("Сезон 1 • Серия 5", ui.heroMetaLine)
        assertEquals(
            true,
            ui.secondaryActions.any { it.actionType == DetailsStageActionType.OPEN_SERIES_GUIDE },
        )
    }

    @Test
    fun buildStageUi_usesDisabledPrimaryActionWhenNoPlaybackRowExists() {
        val ui = buildDetailsStageUi(
            state = DetailsUiState(details = seriesDetails()),
            isAuthenticated = true,
            availableTorrentRowsCount = 0,
            playbackRow = null,
            activeTorrServeRowId = null,
            isTorrServeBusy = false,
        )

        assertEquals(null, ui.activeRowId)
        assertEquals(DetailsStageActionType.NONE, ui.primaryAction.actionType)
        assertEquals(false, ui.primaryAction.enabled)
        assertEquals("Смотреть", ui.primaryAction.label)
        assertEquals("Сезон 1 • Серия 5", ui.heroMetaLine)
        assertEquals(
            true,
            ui.secondaryActions.any { it.actionType == DetailsStageActionType.OPEN_SERIES_GUIDE },
        )
    }

    @Test
    fun buildStageUi_splits_series_meta() {
        val ui = buildDetailsStageUi(
            state = DetailsUiState(details = seriesDetails()),
            isAuthenticated = true,
            availableTorrentRowsCount = 1,
            playbackRow = DetailsTorrentRowUiModel(
                rowId = "row-0",
                label = "1080p",
                url = "https://example.com/1080",
                isTorrServeSupported = true,
            ),
            activeTorrServeRowId = null,
            isTorrServeBusy = false,
        )

        assertEquals("Сезон 1 • Серия 5", ui.heroMetaLine)
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
    episodeTitleRu = "The Engineer",
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
