package com.kraat.lostfilmnewtv.tvchannel

import com.kraat.lostfilmnewtv.data.db.ReleaseDao
import com.kraat.lostfilmnewtv.data.db.ReleaseSummaryEntity
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import com.kraat.lostfilmnewtv.data.poster.TmdbPosterEnricher
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

        val basePrograms = rows.map { row ->
            HomeChannelProgram(
                detailsUrl = row.detailsUrl,
                title = row.titleRu,
                description = row.channelDescription(),
                posterUrl = row.posterUrl,
                internalProviderId = row.detailsUrl,
            )
        }

        return coroutineScope {
            basePrograms.map { program ->
                async {
                    val tmdbUrls = tmdbResolver.resolve(
                        detailsUrl = program.detailsUrl,
                        titleRu = program.title,
                        releaseDateRu = program.description,
                        kind = inferKindFromDescription(program.description),
                    )
                    val enrichedPosterUrl = tmdbUrls?.posterUrl?.takeIf { it.isNotBlank() }
                        ?: program.posterUrl
                    program.copy(posterUrl = enrichedPosterUrl)
                }
            }.awaitAll()
        }
    }
}

private fun inferKindFromDescription(description: String): ReleaseKind {
    val datePattern = Regex("""\d{2}\.\d{2}\.\d{4}""")
    return if (datePattern.matches(description.trim())) {
        ReleaseKind.MOVIE
    } else {
        ReleaseKind.SERIES
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
