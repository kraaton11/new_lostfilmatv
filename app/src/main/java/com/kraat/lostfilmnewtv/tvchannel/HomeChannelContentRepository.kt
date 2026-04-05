package com.kraat.lostfilmnewtv.tvchannel

import com.kraat.lostfilmnewtv.data.db.ReleaseDao
import com.kraat.lostfilmnewtv.data.db.ReleaseSummaryEntity
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import com.kraat.lostfilmnewtv.data.poster.TmdbPosterResolver
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class HomeChannelContentRepository(
    private val reader: HomeChannelSummaryReader,
    private val tmdbResolver: TmdbPosterResolver,
) : HomeChannelProgramSource {
    constructor(releaseDao: ReleaseDao, tmdbResolver: TmdbPosterResolver) : this(
        reader = DaoHomeChannelSummaryReader(releaseDao),
        tmdbResolver = tmdbResolver,
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

        return coroutineScope {
            rows.map { row ->
                async {
                    val tmdbUrls = tmdbResolver.resolve(
                        detailsUrl = row.detailsUrl,
                        titleRu = row.titleRu,
                        releaseDateRu = row.releaseDateRu,
                        kind = ReleaseKind.valueOf(row.kind),
                    )
                    HomeChannelProgram(
                        detailsUrl = row.detailsUrl,
                        title = row.titleRu,
                        description = row.channelDescription(),
                        posterUrl = tmdbUrls?.posterUrl?.takeIf { it.isNotBlank() }
                            ?: row.posterUrl,
                        backdropUrl = tmdbUrls?.backdropUrl?.takeIf { it.isNotBlank() }.orEmpty(),
                        internalProviderId = row.detailsUrl,
                    )
                }
            }.awaitAll()
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
