package com.kraat.lostfilmnewtv.data.model

import java.time.LocalDate

data class ScheduleMonth(
    val title: String,
    val days: List<ScheduleDay>,
)

data class ScheduleDay(
    val date: LocalDate,
    val label: String,
    val isToday: Boolean,
    val items: List<ScheduleItem>,
)

data class ScheduleItem(
    val title: String,
    val episodeLabel: String?,
    val targetUrl: String,
    val kind: ReleaseKind,
    val posterUrl: String? = null,
)
