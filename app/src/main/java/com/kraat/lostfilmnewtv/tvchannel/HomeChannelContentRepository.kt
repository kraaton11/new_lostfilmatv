package com.kraat.lostfilmnewtv.tvchannel

import com.kraat.lostfilmnewtv.data.db.ReleaseDao
import com.kraat.lostfilmnewtv.data.db.ReleaseSummaryEntity
import com.kraat.lostfilmnewtv.data.model.FavoriteReleasesResult
import com.kraat.lostfilmnewtv.data.model.ReleaseKind
import com.kraat.lostfilmnewtv.data.model.ReleaseSummary
import com.kraat.lostfilmnewtv.data.parser.LostFilmBaseUrl
import com.kraat.lostfilmnewtv.data.parser.lostFilmArtworkUrl

class HomeChannelContentRepository(
    private val reader: HomeChannelSummaryReader,
    private val loadFavoriteReleases: suspend () -> FavoriteReleasesResult = {
        FavoriteReleasesResult.Unavailable()
    },
) : HomeChannelProgramSource {
    constructor(
        releaseDao: ReleaseDao,
        loadFavoriteReleases: suspend () -> FavoriteReleasesResult = {
            FavoriteReleasesResult.Unavailable()
        },
    ) : this(
        reader = DaoHomeChannelSummaryReader(releaseDao),
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

        return mapPrograms(rows.map { it.toModel() })
    }

    override suspend fun loadFavoritePrograms(limit: Int): List<HomeChannelProgram> {
        val items = when (val result = loadFavoriteReleases()) {
            is FavoriteReleasesResult.Success -> result.items.take(limit)
            is FavoriteReleasesResult.Partial -> result.items.take(limit)
            is FavoriteReleasesResult.Unavailable -> return emptyList()
        }

        return mapPrograms(items)
    }

    private fun mapPrograms(items: List<ReleaseSummary>): List<HomeChannelProgram> {
        val baseUrl = LostFilmBaseUrl.get()
        return items.map { item ->
            HomeChannelProgram(
                detailsUrl = item.detailsUrl,
                title = item.titleRu,
                description = item.channelDescription(),
                posterUrl = lostFilmArtworkUrl(item.posterUrl, baseUrl).orEmpty(),
                backdropUrl = lostFilmArtworkUrl(item.backdropUrl, baseUrl).orEmpty(),
                internalProviderId = item.detailsUrl,
            )
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
