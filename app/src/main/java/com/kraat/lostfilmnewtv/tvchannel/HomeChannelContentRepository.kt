package com.kraat.lostfilmnewtv.tvchannel

import com.kraat.lostfilmnewtv.data.db.ReleaseDao
import com.kraat.lostfilmnewtv.data.db.ReleaseSummaryEntity
import com.kraat.lostfilmnewtv.data.model.FavoriteReleasesResult
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import com.kraat.lostfilmnewtv.data.model.ReleaseSummary
import com.kraat.lostfilmnewtv.data.poster.TmdbPosterResolver
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private const val HOME_CHANNEL_TMDB_CONCURRENCY = 4

class HomeChannelContentRepository(
    private val reader: HomeChannelSummaryReader,
    private val tmdbResolver: TmdbPosterResolver,
    private val loadFavoriteReleases: suspend () -> FavoriteReleasesResult = {
        FavoriteReleasesResult.Unavailable()
    },
) : HomeChannelProgramSource {
    constructor(
        releaseDao: ReleaseDao,
        tmdbResolver: TmdbPosterResolver,
        loadFavoriteReleases: suspend () -> FavoriteReleasesResult = {
            FavoriteReleasesResult.Unavailable()
        },
    ) : this(
        reader = DaoHomeChannelSummaryReader(releaseDao),
        tmdbResolver = tmdbResolver,
        loadFavoriteReleases = loadFavoriteReleases,
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

        return mapEntityRows(rows)
    }

    override suspend fun loadFavoritePrograms(limit: Int): List<HomeChannelProgram> {
        val items = when (val result = loadFavoriteReleases()) {
            is FavoriteReleasesResult.Success -> result.items.take(limit)
            is FavoriteReleasesResult.Unavailable -> return emptyList()
        }

        return mapSummaryRows(items)
    }

    private suspend fun mapEntityRows(rows: List<ReleaseSummaryEntity>): List<HomeChannelProgram> {
        // Home channel refresh runs in the background; keep TMDB lookups polite and predictable.
        val semaphore = Semaphore(HOME_CHANNEL_TMDB_CONCURRENCY)
        return coroutineScope {
            rows.map { row ->
                async {
                    semaphore.withPermit {
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
                }
            }.awaitAll()
        }
    }

    private suspend fun mapSummaryRows(items: List<ReleaseSummary>): List<HomeChannelProgram> {
        // Favorite channel rows can trigger fresh TMDB lookups, so use the same background cap.
        val semaphore = Semaphore(HOME_CHANNEL_TMDB_CONCURRENCY)
        return coroutineScope {
            items.map { item ->
                async {
                    semaphore.withPermit {
                        val tmdbUrls = tmdbResolver.resolve(
                            detailsUrl = item.detailsUrl,
                            titleRu = item.titleRu,
                            releaseDateRu = item.releaseDateRu,
                            kind = item.kind,
                        )
                        HomeChannelProgram(
                            detailsUrl = item.detailsUrl,
                            title = item.titleRu,
                            description = item.channelDescription(),
                            posterUrl = tmdbUrls?.posterUrl?.takeIf { it.isNotBlank() }
                                ?: item.posterUrl,
                            backdropUrl = tmdbUrls?.backdropUrl?.takeIf { it.isNotBlank() }.orEmpty(),
                            internalProviderId = item.detailsUrl,
                        )
                    }
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

    suspend fun loadFavoritePrograms(limit: Int): List<HomeChannelProgram> = emptyList()
}

interface HomeChannelSummaryReader {
    suspend fun latest(limit: Int): List<ReleaseSummaryEntity>

    suspend fun latestUnwatched(limit: Int): List<ReleaseSummaryEntity>
}

class DaoHomeChannelSummaryReader(
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

private fun ReleaseSummary.channelDescription(): String {
    return when (kind) {
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
