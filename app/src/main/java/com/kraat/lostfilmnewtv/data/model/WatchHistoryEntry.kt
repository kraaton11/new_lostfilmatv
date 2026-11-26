package com.kraat.lostfilmnewtv.data.model

data class WatchHistoryEntry(
    val titleRu: String,
    val titleEn: String,
    val posterUrl: String?,
    val seriesUrl: String,
    val watchedCount: Int,
    val totalCount: Int,
)
