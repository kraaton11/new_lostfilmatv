package com.kraat.lostfilmnewtv.data.network

object LostFilmConcurrencyLimits {
    const val FAVORITE_SERIES_LOAD_CONCURRENCY = 6
    const val FAVORITE_PUBLISH_CHECK_CONCURRENCY = 6
    const val WATCHED_MARKS_LOAD_CONCURRENCY = 4
    const val SCHEDULE_IMAGE_ENRICHMENT_CONCURRENCY = 4
    const val SEARCH_ENRICHMENT_CONCURRENCY = 6
    const val SUMMARY_ENRICHMENT_CONCURRENCY = 6

    // Limits okhttp maxRequestsPerHost app-wide for lostfilm.today
    const val LOSTFILM_MAX_CONCURRENT_REQUESTS = 8
}
