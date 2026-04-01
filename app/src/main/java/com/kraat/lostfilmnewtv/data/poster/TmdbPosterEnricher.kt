package com.kraat.lostfilmnewtv.data.poster

import com.kraat.lostfilmnewtv.data.model.ReleaseDetails
import com.kraat.lostfilmnewtv.data.model.ReleaseSummary
import com.kraat.lostfilmnewtv.data.model.TmdbImageUrls

object TmdbPosterEnricher {
    fun enrichSummary(
        summary: ReleaseSummary,
        tmdbUrls: TmdbImageUrls?,
    ): ReleaseSummary {
        if (tmdbUrls == null || tmdbUrls.posterUrl.isBlank()) {
            return summary
        }
        return summary.copy(posterUrl = tmdbUrls.posterUrl)
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
        )
    }
}
