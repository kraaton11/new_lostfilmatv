package com.kraat.lostfilmnewtv.ui.details

import com.kraat.lostfilmnewtv.playback.PlaybackQualityPreference
import kotlin.math.abs

fun resolvePreferredTorrentRow(
    preference: PlaybackQualityPreference,
    rows: List<DetailsTorrentRowUiModel>,
): DetailsTorrentRowUiModel? {
    if (rows.isEmpty()) return null

    val resolvedRows = rows.map { row ->
        ResolvedTorrentRow(
            row = row,
            quality = normalizePlaybackQuality(row.label),
        )
    }

    resolvedRows.firstOrNull { it.quality == preference }?.let { return it.row }

    val knownRows = resolvedRows.filter { it.quality != null }
    if (knownRows.isEmpty()) {
        return rows.firstOrNull()
    }

    return knownRows.minWithOrNull(
        compareBy<ResolvedTorrentRow> {
            abs(checkNotNull(it.quality).rank - preference.rank)
        }.thenByDescending {
            checkNotNull(it.quality).rank
        },
    )?.row
}

private data class ResolvedTorrentRow(
    val row: DetailsTorrentRowUiModel,
    val quality: PlaybackQualityPreference?,
)

private fun normalizePlaybackQuality(label: String): PlaybackQualityPreference? {
    val normalized = label.trim().lowercase()
    return when {
        "1080" in normalized -> PlaybackQualityPreference.Q1080
        "720" in normalized -> PlaybackQualityPreference.Q720
        normalized == "mp4" -> PlaybackQualityPreference.Q720
        "480" in normalized -> PlaybackQualityPreference.Q480
        normalized == "sd" -> PlaybackQualityPreference.Q480
        else -> null
    }
}
