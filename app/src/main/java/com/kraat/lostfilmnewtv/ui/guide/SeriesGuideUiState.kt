package com.kraat.lostfilmnewtv.ui.guide

import com.kraat.lostfilmnewtv.data.model.SeriesGuideSeason

data class SeriesGuideUiState(
    val title: String = "",
    val posterUrl: String? = null,
    val seasons: List<SeriesGuideSeason> = emptyList(),
    val selectedEpisodeDetailsUrl: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)
