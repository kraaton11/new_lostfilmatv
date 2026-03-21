package com.kraat.lostfilmnewtv.ui.details

import com.kraat.lostfilmnewtv.data.model.ReleaseDetails
import com.kraat.lostfilmnewtv.data.model.ReleaseKind

data class DetailsStageUiModel(
    val activeRowId: String?,
    val title: String,
    val heroMetaLine: String,
    val heroStatusLine: String,
    val primaryAction: DetailsStageActionUiModel,
    val qualityActions: List<DetailsStageActionUiModel>,
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

enum class DetailsStageActionType { OPEN_TORRSERVE, OPEN_LINK, NONE }

fun buildDetailsStageUi(
    state: DetailsUiState,
    isAuthenticated: Boolean,
    torrentRows: List<DetailsTorrentRowUiModel>,
    activeRowId: String?,
    activeTorrServeRowId: String?,
    isTorrServeBusy: Boolean,
    torrServeMessageText: String? = null,
): DetailsStageUiModel {
    val details = state.details
    val resolvedActiveRow = torrentRows.firstOrNull { it.rowId == activeRowId } ?: torrentRows.firstOrNull()
    val qualityActions = torrentRows.map { row ->
        row.toQualityAction(
            isBusy = isTorrServeBusy && activeTorrServeRowId == row.rowId,
            disableForBusy = isTorrServeBusy && row.isTorrServeSupported,
        )
    }
    val primaryAction = qualityActions.firstOrNull { it.rowId == resolvedActiveRow?.rowId }
        ?: DetailsStageActionUiModel(
            actionId = "empty-primary",
            rowId = null,
            label = qualityStatusText(
                hasDetails = details != null,
                torrentRowsCount = torrentRows.size,
                isAuthenticated = isAuthenticated,
            ) ?: "Загрузка",
            subtitle = "Нет доступного действия",
            qualityLabel = null,
            actionType = DetailsStageActionType.NONE,
            enabled = false,
        )

    val secondaryActions = buildList {
        if (resolvedActiveRow != null) {
            add(
                DetailsStageActionUiModel(
                    actionId = "open-link-${resolvedActiveRow.rowId}",
                    rowId = resolvedActiveRow.rowId,
                    label = "Открыть ссылку",
                    subtitle = resolvedActiveRow.label,
                    qualityLabel = resolvedActiveRow.label,
                    actionType = DetailsStageActionType.OPEN_LINK,
                    enabled = true,
                ),
            )
        }
    }

    return DetailsStageUiModel(
        activeRowId = resolvedActiveRow?.rowId,
        title = details?.titleRu ?: "",
        heroMetaLine = buildHeroMetaLine(details = details),
        heroStatusLine = buildHeroStatusLine(
            activeRow = resolvedActiveRow,
            showStaleBanner = state.showStaleBanner,
            isBusy = isTorrServeBusy && activeTorrServeRowId == resolvedActiveRow?.rowId,
            torrServeMessageText = torrServeMessageText,
        ),
        primaryAction = primaryAction,
        qualityActions = qualityActions,
        secondaryActions = secondaryActions,
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

private fun buildHeroStatusLine(
    activeRow: DetailsTorrentRowUiModel?,
    showStaleBanner: Boolean,
    isBusy: Boolean,
    torrServeMessageText: String?,
): String {
    if (!torrServeMessageText.isNullOrBlank()) return torrServeMessageText
    if (isBusy) return "Открывается..."

    val quality = activeRow?.label ?: return ""
    if (activeRow.isTorrServeSupported.not()) {
        return "$quality • прямая ссылка"
    }

    val freshness = if (showStaleBanner) "данные из кэша" else "свежие данные"
    return "$quality • TorrServe • $freshness"
}

private fun DetailsTorrentRowUiModel.toQualityAction(
    isBusy: Boolean,
    disableForBusy: Boolean,
): DetailsStageActionUiModel {
    val actionType = if (isTorrServeSupported) {
        DetailsStageActionType.OPEN_TORRSERVE
    } else {
        DetailsStageActionType.OPEN_LINK
    }
    return DetailsStageActionUiModel(
        actionId = "quality-$rowId",
        rowId = rowId,
        label = "Смотреть $label",
        subtitle = when {
            isBusy -> "Открывается..."
            isTorrServeSupported -> "TorrServe"
            else -> "Прямая ссылка"
        },
        qualityLabel = label,
        actionType = actionType,
        enabled = !disableForBusy,
    )
}
