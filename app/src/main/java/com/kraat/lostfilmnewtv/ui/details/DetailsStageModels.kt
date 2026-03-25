package com.kraat.lostfilmnewtv.ui.details

import com.kraat.lostfilmnewtv.data.model.ReleaseDetails
import com.kraat.lostfilmnewtv.data.model.ReleaseKind

data class DetailsStageUiModel(
    val activeRowId: String?,
    val title: String,
    val heroEpisodeTitle: String,
    val heroMetaLine: String,
    val bottomStageStatusLine: String,
    val bottomStageSupportLine: String,
    val primaryAction: DetailsStageActionUiModel,
    val secondaryActions: List<DetailsStageActionUiModel>,
) {
    val bottomInfoLine: String
        get() = bottomStageStatusLine
}

data class DetailsStageActionUiModel(
    val actionId: String,
    val rowId: String?,
    val label: String,
    val subtitle: String,
    val qualityLabel: String? = null,
    val actionType: DetailsStageActionType,
    val enabled: Boolean = true,
)

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
    val heroMetaLine = buildHeroMetaLine(details = details)
    val bottomStageStatusLine = buildBottomStageStatusLine(
        details = details,
        playbackRow = playbackRow,
        isBusy = isBusy,
        favoriteStatusMessageText = state.favoriteStatusMessage,
        torrServeMessageText = torrServeMessageText,
    )
    val bottomStageSupportLine = buildBottomStageSupportLine(details = details)
    val favoriteAction = details
        ?.takeIf { it.favoriteTargetId != null || state.favoriteActionLabel.isNotBlank() }
        ?.let {
        DetailsStageActionUiModel(
            actionId = "favorite",
            rowId = null,
            label = state.favoriteActionLabel.ifBlank {
                if (details.isFavorite == true) "Убрать из избранного" else "Добавить в избранное"
            },
            subtitle = when {
                !isAuthenticated -> "Требуется авторизация"
                state.isFavoriteMutationInFlight -> "Синхронизация с LostFilm"
                details.favoriteTargetId == null || details.isFavorite == null -> "Состояние недоступно"
                else -> "Синхронизация с LostFilm"
            },
            qualityLabel = null,
            actionType = DetailsStageActionType.TOGGLE_FAVORITE,
            enabled = state.isFavoriteActionEnabled,
        )
    }
    val guideAction = details
        ?.takeIf { it.kind == ReleaseKind.SERIES }
        ?.let {
            DetailsStageActionUiModel(
                actionId = "series-guide",
                rowId = null,
                label = "Гид по сериям",
                subtitle = "Все сезоны и серии",
                qualityLabel = null,
                actionType = DetailsStageActionType.OPEN_SERIES_GUIDE,
                enabled = true,
            )
        }

    return DetailsStageUiModel(
        activeRowId = playbackRow?.rowId,
        title = details?.titleRu ?: "",
        heroEpisodeTitle = buildHeroEpisodeTitle(details = details),
        heroMetaLine = heroMetaLine,
        bottomStageStatusLine = bottomStageStatusLine,
        bottomStageSupportLine = bottomStageSupportLine,
        primaryAction = primaryAction,
        secondaryActions = listOfNotNull(favoriteAction, guideAction),
    )
}

private fun buildHeroEpisodeTitle(details: ReleaseDetails?): String {
    if (details?.kind != ReleaseKind.SERIES) return ""
    return details.episodeTitleRu?.trim().orEmpty()
}

private fun buildHeroMetaLine(details: ReleaseDetails?): String {
    return when (details?.kind) {
        ReleaseKind.SERIES -> {
            val season = details.seasonNumber
            val episode = details.episodeNumber
            if (season != null && episode != null) {
                "Сезон $season • Серия $episode"
            } else {
                "Сериал"
            }
        }
        ReleaseKind.MOVIE -> "Фильм"
        null -> ""
    }
}

private fun buildBottomStageStatusLine(
    details: ReleaseDetails?,
    playbackRow: DetailsTorrentRowUiModel?,
    isBusy: Boolean,
    favoriteStatusMessageText: String?,
    torrServeMessageText: String?,
): String {
    if (!favoriteStatusMessageText.isNullOrBlank()) return favoriteStatusMessageText
    if (!torrServeMessageText.isNullOrBlank()) return torrServeMessageText
    if (isBusy) return "Открывается..."
    if (playbackRow == null) return "Видео недоступно"

    return "${playbackRow.label} • TorrServe"
}

private fun buildBottomStageSupportLine(details: ReleaseDetails?): String {
    return details?.releaseDateRu?.trim().orEmpty()
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

enum class DetailsStageActionType {
    OPEN_TORRSERVE,
    TOGGLE_FAVORITE,
    OPEN_SERIES_GUIDE,
    NONE,
}
