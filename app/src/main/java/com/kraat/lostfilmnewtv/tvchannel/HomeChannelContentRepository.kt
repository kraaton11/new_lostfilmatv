package com.kraat.lostfilmnewtv.tvchannel

import com.kraat.lostfilmnewtv.data.db.FavoriteReleaseDao
import com.kraat.lostfilmnewtv.data.db.FavoriteReleaseEntity
import com.kraat.lostfilmnewtv.data.db.ReleaseDao
import com.kraat.lostfilmnewtv.data.db.ReleaseSummaryEntity
import com.kraat.lostfilmnewtv.data.model.ReleaseKind

class HomeChannelContentRepository(
    private val reader: HomeChannelSummaryReader,
) : HomeChannelProgramSource {
    constructor(releaseDao: ReleaseDao, favoriteReleaseDao: FavoriteReleaseDao) : this(
        reader = DaoHomeChannelSummaryReader(releaseDao, favoriteReleaseDao),
    )

    override suspend fun loadPrograms(
        mode: AndroidTvChannelMode,
        limit: Int,
    ): List<HomeChannelProgram> {
        return when (mode) {
            AndroidTvChannelMode.ALL_NEW -> reader.latest(limit)
            AndroidTvChannelMode.UNWATCHED -> reader.latestUnwatched(limit)
            AndroidTvChannelMode.FAVORITES -> reader.latestFavorites(limit)
            AndroidTvChannelMode.DISABLED -> return emptyList()
        }.map { row ->
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

interface ChannelProgramRow {
    val detailsUrl: String
    val titleRu: String
    val posterUrl: String
    fun channelDescription(): String
}

interface HomeChannelSummaryReader {
    suspend fun latest(limit: Int): List<ChannelProgramRow>

    suspend fun latestUnwatched(limit: Int): List<ChannelProgramRow>

    suspend fun latestFavorites(limit: Int): List<ChannelProgramRow>
}

private class DaoHomeChannelSummaryReader(
    private val releaseDao: ReleaseDao,
    private val favoriteReleaseDao: FavoriteReleaseDao,
) : HomeChannelSummaryReader {
    override suspend fun latest(limit: Int): List<ChannelProgramRow> {
        return releaseDao.getLatestSummariesForChannel(limit)
            .map { it.toChannelRow() }
    }

    override suspend fun latestUnwatched(limit: Int): List<ChannelProgramRow> {
        return releaseDao.getLatestUnwatchedSummariesForChannel(limit)
            .map { it.toChannelRow() }
    }

    override suspend fun latestFavorites(limit: Int): List<ChannelProgramRow> {
        return releaseDao.getLatestSummariesForChannel(limit)
            .map { it.toChannelRow() }
    }
}

internal fun ReleaseSummaryEntity.toChannelRow(): ChannelProgramRow {
    val entity = this
    return object : ChannelProgramRow {
        override val detailsUrl: String get() = entity.detailsUrl
        override val titleRu: String get() = entity.titleRu
        override val posterUrl: String get() = entity.posterUrl
        override fun channelDescription(): String = entity.channelDescription()
    }
}

private fun FavoriteReleaseEntity.toChannelRow(): ChannelProgramRow {
    val entity = this
    return object : ChannelProgramRow {
        override val detailsUrl: String get() = entity.detailsUrl
        override val titleRu: String get() = entity.titleRu
        override val posterUrl: String get() = entity.posterUrl
        override fun channelDescription(): String = entity.channelDescription()
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

private fun FavoriteReleaseEntity.channelDescription(): String {
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
