package com.kraat.lostfilmnewtv.ui.details

import com.kraat.lostfilmnewtv.data.model.ReleaseDetails
import com.kraat.lostfilmnewtv.data.model.ReleaseKind

data class DetailsStageUiModel(
    val activeRowId: String?,
    val title: String,
    val heroMetaLine: String,
    val heroEpisodeTitle: String,
    val heroStatusLine: String,
    val bottomInfoLine: String,
    val primaryAction: DetailsStageActionUiModel,
    val secondaryActions: List<DetailsStageActionUiModel>,
)

data class DetailsStageActionUiModel(
    val actionId: String,
    val rowId: String?,
    val label: String,
    val subtitle: String,
    val qualityLabel: String? = null,
    val actionType: DetailsStageActionType,
    val enabled: Boolean = true,
)

enum class DetailsStageActionType { OPEN_TORRSERVE, NONE }

fun buildDetailsStageUi(
    state: DetailsUiState,
    isAuthenticated: Boolean,
    availableTorrentRowsCount: Int,
    playbackRow: DetailsTorrentRowUiModel?,
    activeTorrServeRowId: String?,
    isTorrServeBusy: Boolean,
    torrServeMessageText: String? = null,
): DetailsStageUiModel {
    val details = state.details
    val isBusy = isTorrServeBusy && activeTorrServeRowId == playbackRow?.rowId
    val primaryAction = playbackRow?.toPrimaryAction(isBusy = isBusy)
        ?: DetailsStageActionUiModel(
            actionId = "empty-primary",
            rowId = null,
            label = "Смотреть",
            subtitle = "Недоступно",
            qualityLabel = null,
            actionType = DetailsStageActionType.NONE,
            enabled = false,
        )
    val bottomInfoLine = buildBottomInfoLine(
        details = details,
        playbackRow = playbackRow,
        isBusy = isBusy,
        torrServeMessageText = torrServeMessageText,
    )

    return DetailsStageUiModel(
        activeRowId = playbackRow?.rowId,
        title = details?.titleRu ?: "",
        heroMetaLine = buildHeroMetaLine(details = details),
        heroEpisodeTitle = buildHeroEpisodeTitle(details = details),
        heroStatusLine = bottomInfoLine,
        bottomInfoLine = bottomInfoLine,
        primaryAction = primaryAction,
        secondaryActions = emptyList(),
    )
}

private fun buildHeroMetaLine(details: ReleaseDetails?): String {
    return ""
}

private fun buildHeroEpisodeTitle(details: ReleaseDetails?): String {
    if (details?.kind != ReleaseKind.SERIES) return ""
    return details.episodeTitleRu?.trim().orEmpty()
}

private fun buildBottomInfoLine(
    details: ReleaseDetails?,
    playbackRow: DetailsTorrentRowUiModel?,
    isBusy: Boolean,
    torrServeMessageText: String?,
): String {
    if (!torrServeMessageText.isNullOrBlank()) return torrServeMessageText
    if (isBusy) return "Открывается..."
    if (playbackRow == null) return "Видео недоступно"

    val quality = playbackRow.label
    return when (details?.kind) {
        ReleaseKind.SERIES -> {
            val season = details.seasonNumber
            val episode = details.episodeNumber
            if (season != null && episode != null) {
                "Сезон $season • Серия $episode • $quality"
            } else {
                quality
            }
        }
        ReleaseKind.MOVIE -> "Фильм • $quality"
        null -> quality
    }
}

private fun DetailsTorrentRowUiModel.toPrimaryAction(
    isBusy: Boolean,
): DetailsStageActionUiModel {
    return DetailsStageActionUiModel(
        actionId = "playback-$rowId",
        rowId = rowId,
        label = "Смотреть",
        subtitle = label,
        qualityLabel = label,
        actionType = DetailsStageActionType.OPEN_TORRSERVE,
        enabled = !isBusy,
    )
}
