package com.kraat.lostfilmnewtv.tvchannel

import com.kraat.lostfilmnewtv.data.db.ReleaseDao
import com.kraat.lostfilmnewtv.data.db.ReleaseSummaryEntity
import com.kraat.lostfilmnewtv.data.model.ReleaseKind

class HomeChannelContentRepository(
    private val reader: HomeChannelSummaryReader,
) : HomeChannelProgramSource {
    constructor(releaseDao: ReleaseDao) : this(
        reader = DaoHomeChannelSummaryReader(releaseDao),
    )

    override suspend fun loadPrograms(
        mode: AndroidTvChannelMode,
        limit: Int,
    ): List<HomeChannelProgram> {
        val rows = when (mode) {
            AndroidTvChannelMode.ALL_NEW -> reader.latest(limit)
            AndroidTvChannelMode.UNWATCHED -> reader.latestUnwatched(limit)
            AndroidTvChannelMode.DISABLED -> return emptyList()
        }

        return rows.map { row ->
            HomeChannelProgram(
                detailsUrl = row.detailsUrl,
                title = row.titleRu,
                description = row.channelDescription(),
                posterUrl = row.posterUrl,
                internalProviderId = row.detailsUrl,
            )
        }
    }
}

interface HomeChannelProgramSource {
    suspend fun loadPrograms(
        mode: AndroidTvChannelMode,
        limit: Int,
    ): List<HomeChannelProgram>
}

interface HomeChannelSummaryReader {
    suspend fun latest(limit: Int): List<ReleaseSummaryEntity>

    suspend fun latestUnwatched(limit: Int): List<ReleaseSummaryEntity>
}

private class DaoHomeChannelSummaryReader(
    private val releaseDao: ReleaseDao,
) : HomeChannelSummaryReader {
    override suspend fun latest(limit: Int): List<ReleaseSummaryEntity> {
        return releaseDao.getLatestSummariesForChannel(limit)
    }

    override suspend fun latestUnwatched(limit: Int): List<ReleaseSummaryEntity> {
        return releaseDao.getLatestUnwatchedSummariesForChannel(limit)
    }
}

private fun ReleaseSummaryEntity.channelDescription(): String {
    return when (ReleaseKind.valueOf(kind)) {
        ReleaseKind.MOVIE -> releaseDateRu
        ReleaseKind.SERIES -> episodeTitleRu
            ?.takeIf { it.isNotBlank() }
            ?: buildString {
                append("S")
                append(seasonNumber ?: "?")
                append("E")
                append(episodeNumber ?: "?")
            }
    }
}
