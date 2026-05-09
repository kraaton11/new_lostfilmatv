package com.kraat.lostfilmnewtv.ui.schedule

import com.kraat.lostfilmnewtv.data.model.ScheduleMonth

data class ScheduleUiState(
    val isLoading: Boolean = false,
    val schedule: ScheduleMonth? = null,
    val errorMessage: String? = null,
)
