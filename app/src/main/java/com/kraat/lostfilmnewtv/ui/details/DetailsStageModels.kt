package com.kraat.lostfilmnewtv.ui.details

import com.kraat.lostfilmnewtv.data.model.ReleaseDetails
import com.kraat.lostfilmnewtv.data.model.ReleaseKind

data class DetailsStageUiModel(
    val activeRowId: String?,
    val title: String,
    val heroSubtitle: String,
    val metaChips: List<String>,
    val heroStats: List<String>,
    val primaryAction: DetailsStageActionUiModel,
    val qualityActions: List<DetailsStageActionUiModel>,
    val secondaryActions: List<DetailsStageActionUiModel>,
    val techCards: List<DetailsTechCardUiModel>,
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

enum class DetailsStageActionType {
    OPEN_TORRSERVE,
    OPEN_LINK,
    NONE,
}

data class DetailsTechCardUiModel(
    val cardId: String,
    val label: String,
    val value: String,
    val supportingText: String,
)

fun buildDetailsStageUi(
    state: DetailsUiState,
    isAuthenticated: Boolean,
    torrentRows: List<DetailsTorrentRowUiModel>,
    activeRowId: String?,
    activeTorrServeRowId: String?,
    isTorrServeBusy: Boolean,
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
        heroSubtitle = buildHeroSubtitle(
            details = details,
            activeRow = resolvedActiveRow,
            isAuthenticated = isAuthenticated,
            showStaleBanner = state.showStaleBanner,
        ),
        metaChips = buildMetaChips(details = details, activeRow = resolvedActiveRow),
        heroStats = buildHeroStats(
            activeRow = resolvedActiveRow,
            isAuthenticated = isAuthenticated,
            showStaleBanner = state.showStaleBanner,
            isBusy = isTorrServeBusy && activeTorrServeRowId == resolvedActiveRow?.rowId,
        ),
        primaryAction = primaryAction,
        qualityActions = qualityActions,
        secondaryActions = secondaryActions,
        techCards = buildTechCards(
            details = details,
            activeRow = resolvedActiveRow,
            isAuthenticated = isAuthenticated,
            showStaleBanner = state.showStaleBanner,
            isBusy = isTorrServeBusy && activeTorrServeRowId == resolvedActiveRow?.rowId,
        ),
    )
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

private fun buildMetaChips(
    details: ReleaseDetails?,
    activeRow: DetailsTorrentRowUiModel?,
): List<String> {
    if (details == null) return emptyList()

    return buildList {
        add(if (details.kind == ReleaseKind.SERIES) "Series" else "Movie")
        if (details.kind == ReleaseKind.SERIES &&
            details.seasonNumber != null &&
            details.episodeNumber != null
        ) {
            add("S${details.seasonNumber} E${details.episodeNumber}")
        }
        activeRow?.label?.let(::add)
    }
}

private fun buildHeroStats(
    activeRow: DetailsTorrentRowUiModel?,
    isAuthenticated: Boolean,
    showStaleBanner: Boolean,
    isBusy: Boolean,
): List<String> {
    return buildList {
        activeRow?.label?.let(::add)
        add(if (activeRow?.isTorrServeSupported == true) "TorrServe" else "Прямая ссылка")
        add(if (isAuthenticated) "Доступно" else "Требуется вход")
        add(
            when {
                isBusy -> "Запуск"
                showStaleBanner -> "Кэш"
                else -> "Свежо"
            },
        )
    }
}

private fun buildHeroSubtitle(
    details: ReleaseDetails?,
    activeRow: DetailsTorrentRowUiModel?,
    isAuthenticated: Boolean,
    showStaleBanner: Boolean,
): String {
    if (details == null) return ""

    val availability = if (isAuthenticated) {
        "варианты доступны"
    } else {
        "для полной загрузки нужен вход"
    }
    val freshness = if (showStaleBanner) {
        "данные показаны из кэша"
    } else {
        "данные свежие"
    }
    val delivery = if (activeRow?.isTorrServeSupported == false) {
        "основной маршрут идёт через прямую ссылку"
    } else {
        "основной маршрут идёт через TorrServe"
    }

    return "$delivery, $availability, $freshness."
}

private fun buildTechCards(
    details: ReleaseDetails?,
    activeRow: DetailsTorrentRowUiModel?,
    isAuthenticated: Boolean,
    showStaleBanner: Boolean,
    isBusy: Boolean,
): List<DetailsTechCardUiModel> {
    return listOf(
        DetailsTechCardUiModel(
            cardId = "quality",
            label = "Качество",
            value = activeRow?.label ?: "Нет данных",
            supportingText = "Активный playback-вариант",
        ),
        DetailsTechCardUiModel(
            cardId = "delivery",
            label = "Доставка",
            value = when {
                isBusy -> "Запуск"
                activeRow?.isTorrServeSupported == true -> "TorrServe"
                else -> "Прямая ссылка"
            },
            supportingText = if (activeRow?.isTorrServeSupported == true) {
                "Основной TV-маршрут"
            } else {
                "Fallback без TorrServe"
            },
        ),
        DetailsTechCardUiModel(
            cardId = "release",
            label = "Релиз",
            value = details.releaseMarker(),
            supportingText = details?.releaseDateRu ?: "Дата недоступна",
        ),
        DetailsTechCardUiModel(
            cardId = "access",
            label = "Доступ",
            value = if (isAuthenticated) "Доступно" else "Требуется вход",
            supportingText = if (isAuthenticated) {
                "Поток можно открыть с экрана"
            } else {
                "Без входа часть вариантов может быть недоступна"
            },
        ),
        DetailsTechCardUiModel(
            cardId = "freshness",
            label = "Кэш",
            value = if (showStaleBanner) "Устарел" else "Актуален",
            supportingText = if (showStaleBanner) {
                "Показаны сохранённые данные"
            } else {
                "Используется свежая карточка"
            },
        ),
    )
}

private fun ReleaseDetails?.releaseMarker(): String {
    if (this == null) return "Нет данных"

    return when (kind) {
        ReleaseKind.SERIES -> {
            if (seasonNumber != null && episodeNumber != null) {
                "Сезон $seasonNumber · Серия $episodeNumber"
            } else {
                "Сериал"
            }
        }
        ReleaseKind.MOVIE -> "Фильм"
    }
}
