package com.kraat.lostfilmnewtv.ui.details

import com.kraat.lostfilmnewtv.data.model.ReleaseDetails

data class DetailsTorrentRowUiModel(
    val rowId: String,
    val label: String,
    val url: String,
    val isTorrServeSupported: Boolean = true,
)

data class TorrServeMessage(
    val rowId: String,
    val text: String,
)

fun ReleaseDetails.toTorrentRows(): List<DetailsTorrentRowUiModel> {
    return torrentLinks.mapIndexed { index, link ->
        DetailsTorrentRowUiModel(
            rowId = "torrent-row-$index",
            label = link.label,
            url = link.url,
        )
    }
}

fun qualityStatusText(
    hasDetails: Boolean,
    torrentRowsCount: Int,
    isAuthenticated: Boolean,
): String? {
    if (!hasDetails) {
        return null
    }

    return when {
        torrentRowsCount > 0 -> "Найдено $torrentRowsCount варианта качества".replace("1 варианта", "1 вариант")
        !isAuthenticated -> "Войдите в LostFilm, чтобы загрузить варианты качества"
        else -> "Варианты качества не найдены"
    }
}
