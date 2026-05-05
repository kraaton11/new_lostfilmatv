package com.kraat.lostfilmnewtv.data.poster

import com.kraat.lostfilmnewtv.data.model.ReleaseDetails
import com.kraat.lostfilmnewtv.data.model.ReleaseSummary
import com.kraat.lostfilmnewtv.data.model.TmdbImageUrls

object TmdbPosterEnricher {
    fun enrichSummary(
        summary: ReleaseSummary,
        tmdbUrls: TmdbImageUrls?,
    ): ReleaseSummary {
        if (tmdbUrls == null) {
            return summary
        }
        return summary.copy(
            posterUrl = tmdbUrls.posterUrl.ifBlank { summary.posterUrl },
            backdropUrl = tmdbUrls.backdropUrl.ifBlank { summary.backdropUrl },
            episodeOverviewRu = tmdbUrls.episodeOverviewRu?.ifBlank { null } ?: summary.episodeOverviewRu,
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
            posterUrl = tmdbUrls.posterUrl.ifBlank { details.posterUrl },
            backdropUrl = tmdbUrls.backdropUrl.ifBlank { null },
            episodeOverviewRu = tmdbUrls.episodeOverviewRu?.ifBlank { null } ?: details.episodeOverviewRu,
        )
    }
}
