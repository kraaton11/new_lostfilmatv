package com.kraat.lostfilmnewtv.ui.home

import com.kraat.lostfilmnewtv.data.model.ReleaseSummary

sealed interface HomeModeContentState {
    data object Loading : HomeModeContentState

    data class Content(
        val items: List<ReleaseSummary>,
    ) : HomeModeContentState

    data object Empty : HomeModeContentState

    data class LoginRequired(
        val message: String = "Войдите в LostFilm",
    ) : HomeModeContentState

    data class Error(
        val message: String,
    ) : HomeModeContentState
}
