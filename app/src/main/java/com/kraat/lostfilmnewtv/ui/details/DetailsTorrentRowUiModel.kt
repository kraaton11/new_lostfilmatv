package com.kraat.lostfilmnewtv.ui.details

data class DetailsTorrentRowUiModel(
    val rowId: String,
    val label: String,
    val url: String,
    val isTorrServeSupported: Boolean,
)

data class TorrServeMessage(
    val rowId: String,
    val text: String,
)
