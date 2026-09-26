package com.kraat.lostfilmnewtv.data.poster

import com.kraat.lostfilmnewtv.data.model.ReleaseDetails
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import com.kraat.lostfilmnewtv.data.model.ReleaseSummary
import com.kraat.lostfilmnewtv.data.model.TmdbImageUrls

object TmdbPosterEnricher {
    fun enrichSummary(
        summary: ReleaseSummary,
        tmdbUrls: TmdbImageUrls?,
    ): ReleaseSummary {
        if (tmdbUrls == null) {
            // «Ничего не нашлось» — это отсутствие новых данных, а не повод
            // затереть то, что уже есть. Раньше здесь обнулялся posterUrl, и
            // карточка с постером Кинопоиска (у него всегда нет фона, поэтому
            // hasCompleteArt() ложно) теряла постер и записывала пустоту в Room.
            return summary
        }
        return summary.copy(
            posterUrl = tmdbUrls.posterUrl.ifBlank { "" },
            backdropUrl = tmdbUrls.backdropUrl.ifBlank { summary.backdropUrl },
            episodeOverviewRu = tmdbUrls.episodeOverviewRu?.ifBlank { null } ?: summary.episodeOverviewRu,
            episodeOverviewSource = tmdbUrls.episodeOverviewSource?.ifBlank { null } ?: summary.episodeOverviewSource,
            seriesOverviewRu = tmdbUrls.seriesOverviewRu?.ifBlank { null } ?: summary.seriesOverviewRu,
            movieOverviewRu = tmdbUrls.movieOverviewRu?.ifBlank { null } ?: summary.movieOverviewRu,
            tmdbRating = tmdbUrls.rating?.ifBlank { null } ?: summary.tmdbRating,
        )
    }

    fun enrichDetails(
        details: ReleaseDetails,
        tmdbUrls: TmdbImageUrls?,
    ): ReleaseDetails {
        if (tmdbUrls == null) {
            return details
        }
        return details.copy(
            posterUrl = tmdbUrls.posterUrl.ifBlank { "" },
            backdropUrl = tmdbUrls.backdropUrl.ifBlank { null },
            episodeOverviewRu = tmdbUrls.episodeOverviewRu?.ifBlank { null }
                ?: details.episodeOverviewRu
                ?: tmdbUrls.seriesOverviewRu?.takeIf { details.kind == ReleaseKind.SERIES }?.ifBlank { null },
            episodeOverviewSource = tmdbUrls.episodeOverviewSource?.ifBlank { null } ?: details.episodeOverviewSource,
            movieOverviewRu = if (details.kind == ReleaseKind.MOVIE) {
                tmdbUrls.movieOverviewRu?.ifBlank { null } ?: details.movieOverviewRu
            } else {
                details.movieOverviewRu
            },
            tmdbRating = tmdbUrls.rating?.ifBlank { null } ?: details.tmdbRating,
        )
    }
}
