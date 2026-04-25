package com.kraat.lostfilmnewtv.ui.details

import com.kraat.lostfilmnewtv.data.model.ReleaseDetails
import com.kraat.lostfilmnewtv.data.model.ReleaseKind

data class DetailsStageUiModel(
    val activeRowId: String?,
    val title: String,
    val heroEpisodeTitle: String,
    val heroMetaLine: String,
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
    val isHighlighted: Boolean = false,
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
    val primaryAction = when {
        !isAuthenticated -> DetailsStageActionUiModel(
            actionId = "auth-primary",
            rowId = null,
            label = "Войти в LostFilm",
            subtitle = "",
            qualityLabel = null,
            actionType = DetailsStageActionType.OPEN_AUTH,
            enabled = true,
        )
        playbackRow != null -> playbackRow.toPrimaryAction(
            isBusy = isBusy,
            isAuthenticated = isAuthenticated,
        )
        else -> DetailsStageActionUiModel(
            actionId = "empty-primary",
            rowId = null,
            label = "Смотреть",
            subtitle = "Недоступно",
            qualityLabel = null,
            actionType = DetailsStageActionType.NONE,
            enabled = false,
        )
    }
    val heroMetaLine = buildHeroMetaLine(details = details)
    val heroStatusLine = buildHeroStatusLine(details = details)
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
    val watchedAction = details
        ?.takeIf { it.playEpisodeId != null || state.watchedActionLabel.isNotBlank() }
        ?.let {
            DetailsStageActionUiModel(
                actionId = "watched",
                rowId = null,
                label = state.watchedActionLabel.ifBlank { "Статус недоступен" },
                subtitle = "",
                qualityLabel = null,
                actionType = DetailsStageActionType.TOGGLE_WATCHED,
                enabled = state.isWatchedActionEnabled,
                isHighlighted = state.isWatched == true,
            )
        }
    val overviewAction = details
        ?.takeIf { it.kind == ReleaseKind.SERIES }
        ?.let {
            DetailsStageActionUiModel(
                actionId = "series-overview",
                rowId = null,
                label = "Обзор",
                subtitle = "Описание сериала",
                qualityLabel = null,
                actionType = DetailsStageActionType.OPEN_SERIES_OVERVIEW,
                enabled = true,
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
        heroStatusLine = heroStatusLine,
        primaryAction = primaryAction,
        secondaryActions = if (isAuthenticated) {
            listOfNotNull(overviewAction, watchedAction, favoriteAction, guideAction)
        } else {
            emptyList()
        },
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

private fun buildHeroStatusLine(details: ReleaseDetails?): String {
    if (details?.kind != ReleaseKind.SERIES) {
        return ""
    }

    val status = details.seriesStatusRu
        ?.replace(Regex("""\s+"""), " ")
        ?.trim()
        .orEmpty()

    if (status.isBlank()) {
        return ""
    }

    return if (status.startsWith("Статус:", ignoreCase = true)) {
        status
    } else {
        "Статус: $status"
    }
}

private fun DetailsTorrentRowUiModel.toPrimaryAction(
    isBusy: Boolean,
    isAuthenticated: Boolean,
): DetailsStageActionUiModel {
    return DetailsStageActionUiModel(
        actionId = "playback-$rowId",
        rowId = rowId,
        label = "Смотреть",
        subtitle = if (isAuthenticated) label else "Войдите в LostFilm",
        qualityLabel = label,
        actionType = if (isAuthenticated) {
            DetailsStageActionType.OPEN_TORRSERVE
        } else {
            DetailsStageActionType.OPEN_AUTH
        },
        enabled = !isBusy,
    )
}

enum class DetailsStageActionType {
    OPEN_TORRSERVE,
    OPEN_AUTH,
    TOGGLE_WATCHED,
    TOGGLE_FAVORITE,
    OPEN_SERIES_OVERVIEW,
    OPEN_SERIES_GUIDE,
    NONE,
}
