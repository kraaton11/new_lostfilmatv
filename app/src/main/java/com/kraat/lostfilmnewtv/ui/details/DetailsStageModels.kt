package com.kraat.lostfilmnewtv.ui.details

import com.kraat.lostfilmnewtv.data.model.ReleaseDetails
import com.kraat.lostfilmnewtv.data.model.ReleaseKind

data class DetailsStageUiModel(
    val activeRowId: String?,
    val title: String,
    val heroMetaLine: String,
    val heroEpisodeTitle: String,
    val heroStatusLine: String,
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
            label = qualityStatusText(
                hasDetails = details != null,
                torrentRowsCount = availableTorrentRowsCount,
                isAuthenticated = isAuthenticated,
            ) ?: "Загрузка",
            subtitle = "Нет доступного действия",
            qualityLabel = null,
            actionType = DetailsStageActionType.NONE,
            enabled = false,
        )

    return DetailsStageUiModel(
        activeRowId = playbackRow?.rowId,
        title = details?.titleRu ?: "",
        heroMetaLine = buildHeroMetaLine(details = details),
        heroEpisodeTitle = buildHeroEpisodeTitle(details = details),
        heroStatusLine = buildHeroStatusLine(
            playbackRow = playbackRow,
            showStaleBanner = state.showStaleBanner,
            isBusy = isBusy,
            torrServeMessageText = torrServeMessageText,
        ),
        primaryAction = primaryAction,
        secondaryActions = emptyList(),
    )
}

private fun buildHeroMetaLine(details: ReleaseDetails?): String {
    if (details == null) return ""

    return when (details.kind) {
        ReleaseKind.SERIES -> {
            if (details.seasonNumber != null && details.episodeNumber != null) {
                "Сезон ${details.seasonNumber}, серия ${details.episodeNumber}"
            } else {
                details.releaseDateRu
            }
        }
        ReleaseKind.MOVIE -> details.releaseDateRu
    }
}

private fun buildHeroEpisodeTitle(details: ReleaseDetails?): String {
    if (details?.kind != ReleaseKind.SERIES) return ""
    return details.episodeTitleRu?.trim().orEmpty()
}

private fun buildHeroStatusLine(
    playbackRow: DetailsTorrentRowUiModel?,
    showStaleBanner: Boolean,
    isBusy: Boolean,
    torrServeMessageText: String?,
): String {
    if (!torrServeMessageText.isNullOrBlank()) return torrServeMessageText
    if (isBusy) return "Открывается..."

    val quality = playbackRow?.label ?: return ""
    val freshness = if (showStaleBanner) "данные из кэша" else "свежие данные"
    return "$quality • TorrServe • $freshness"
}

private fun DetailsTorrentRowUiModel.toPrimaryAction(
    isBusy: Boolean,
): DetailsStageActionUiModel {
    return DetailsStageActionUiModel(
        actionId = "playback-$rowId",
        rowId = rowId,
        label = "Смотреть",
        subtitle = "$label • TorrServe",
        qualityLabel = label,
        actionType = DetailsStageActionType.OPEN_TORRSERVE,
        enabled = !isBusy,
    )
}
