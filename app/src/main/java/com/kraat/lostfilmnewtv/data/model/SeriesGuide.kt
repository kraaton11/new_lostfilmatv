package com.kraat.lostfilmnewtv.data.model

data class SeriesGuide(
    val seriesTitleRu: String,
    val posterUrl: String?,
    val selectedEpisodeDetailsUrl: String?,
    val seasons: List<SeriesGuideSeason>,
)

data class SeriesGuideSeason(
    val seasonNumber: Int,
    val episodes: List<SeriesGuideEpisode>,
)

data class SeriesGuideEpisode(
    val detailsUrl: String,
    val episodeId: String?,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val episodeTitleRu: String?,
    val releaseDateRu: String,
    val isWatched: Boolean,
)
