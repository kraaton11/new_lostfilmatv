package com.kraat.lostfilmnewtv.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

@Dao
interface ReleaseDao {
    @Query(
        """
        SELECT * FROM release_summaries
        WHERE pageNumber = :pageNumber
        ORDER BY positionInPage ASC
        """,
    )
    suspend fun getPageSummaries(pageNumber: Int): List<ReleaseSummaryEntity>

    @Query(
        """
        SELECT * FROM release_summaries
        WHERE pageNumber <= :pageNumber
        ORDER BY pageNumber ASC, positionInPage ASC
        """,
    )
    suspend fun getSummariesUpToPage(pageNumber: Int): List<ReleaseSummaryEntity>

    @Query(
        """
        SELECT * FROM release_summaries
        ORDER BY pageNumber ASC, positionInPage ASC
        LIMIT :limit
        """,
    )
    suspend fun getLatestSummariesForChannel(limit: Int): List<ReleaseSummaryEntity>

    @Query(
        """
        SELECT * FROM release_summaries
        WHERE isWatched = 0
        ORDER BY pageNumber ASC, positionInPage ASC
        LIMIT :limit
        """,
    )
    suspend fun getLatestUnwatchedSummariesForChannel(limit: Int): List<ReleaseSummaryEntity>

    @Query("SELECT * FROM page_cache_metadata WHERE pageNumber = :pageNumber")
    suspend fun getPageMetadata(pageNumber: Int): PageCacheMetadataEntity?

    @Query("SELECT * FROM release_details WHERE detailsUrl = :detailsUrl")
    suspend fun getReleaseDetails(detailsUrl: String): ReleaseDetailsEntity?

    @Query("SELECT * FROM release_summaries WHERE detailsUrl = :detailsUrl LIMIT 1")
    suspend fun getSummary(detailsUrl: String): ReleaseSummaryEntity?

    @Query("SELECT * FROM release_summaries WHERE detailsUrl IN (:detailsUrls)")
    suspend fun getSummaries(detailsUrls: List<String>): List<ReleaseSummaryEntity>

    @Query("UPDATE release_summaries SET isWatched = :isWatched WHERE detailsUrl = :detailsUrl")
    suspend fun updateSummaryWatched(detailsUrl: String, isWatched: Boolean)

    @Upsert
    suspend fun upsertSummaries(summaries: List<ReleaseSummaryEntity>)

    @Upsert
    suspend fun upsertDetails(details: ReleaseDetailsEntity)

    @Upsert
    suspend fun upsertPageMetadata(metadata: PageCacheMetadataEntity)

    @Query("DELETE FROM release_summaries WHERE pageNumber = :pageNumber")
    suspend fun deleteSummariesForPage(pageNumber: Int)

    @Query("DELETE FROM release_summaries WHERE fetchedAt < :threshold")
    suspend fun deleteExpiredSummaries(threshold: Long)

    @Query("DELETE FROM release_details WHERE fetchedAt < :threshold")
    suspend fun deleteExpiredDetails(threshold: Long)

    @Query("DELETE FROM page_cache_metadata WHERE fetchedAt < :threshold")
    suspend fun deleteExpiredPageMetadata(threshold: Long)

    @Query("DELETE FROM page_cache_metadata WHERE pageNumber = :pageNumber")
    suspend fun deletePageMetadata(pageNumber: Int)

    @Query("DELETE FROM release_summaries")
    suspend fun deleteAllSummaries()

    @Query("DELETE FROM release_details")
    suspend fun deleteAllDetails()

    @Query("DELETE FROM page_cache_metadata")
    suspend fun deleteAllPageMetadata()

    @Query("SELECT * FROM favorite_release_cache ORDER BY positionInList ASC")
    suspend fun getFavoriteReleasesCache(): List<FavoriteReleaseCacheEntity>

    @Query("SELECT * FROM favorite_release_cache_metadata WHERE id = 1")
    suspend fun getFavoriteReleaseCacheMetadata(): FavoriteReleaseCacheMetadataEntity?

    @Query("DELETE FROM favorite_release_cache")
    suspend fun deleteAllFavoriteReleasesCache()

    @Query("DELETE FROM favorite_release_cache_metadata")
    suspend fun deleteAllFavoriteReleasesCacheMetadata()

    @Query("DELETE FROM favorite_release_cache WHERE fetchedAt < :threshold")
    suspend fun deleteExpiredFavoriteReleasesCache(threshold: Long)

    @Query("DELETE FROM favorite_release_cache_metadata WHERE fetchedAt < :threshold")
    suspend fun deleteExpiredFavoriteReleasesCacheMetadata(threshold: Long)

    @Upsert
    suspend fun upsertFavoriteReleasesCache(items: List<FavoriteReleaseCacheEntity>)

    @Upsert
    suspend fun upsertFavoriteReleasesCacheMetadata(metadata: FavoriteReleaseCacheMetadataEntity)

    @Transaction
    suspend fun replaceFavoriteReleasesCache(
        items: List<FavoriteReleaseCacheEntity>,
        metadata: FavoriteReleaseCacheMetadataEntity,
    ) {
        deleteAllFavoriteReleasesCache()
        deleteAllFavoriteReleasesCacheMetadata()
        upsertFavoriteReleasesCache(items)
        upsertFavoriteReleasesCacheMetadata(metadata)
    }

    @Transaction
    suspend fun replacePage(
        pageNumber: Int,
        summaries: List<ReleaseSummaryEntity>,
        metadata: PageCacheMetadataEntity,
    ) {
        deleteSummariesForPage(pageNumber)
        upsertSummaries(summaries)
        upsertPageMetadata(metadata)
    }

    @Transaction
    suspend fun deleteExpiredData(threshold: Long) {
        deleteExpiredSummaries(threshold)
        deleteExpiredDetails(threshold)
        deleteExpiredPageMetadata(threshold)
        deleteExpiredFavoriteReleasesCache(threshold)
        deleteExpiredFavoriteReleasesCacheMetadata(threshold)
    }

    @Transaction
    suspend fun deleteAllCachedReleaseData() {
        deleteAllSummaries()
        deleteAllDetails()
        deleteAllPageMetadata()
        deleteAllFavoriteReleasesCache()
        deleteAllFavoriteReleasesCacheMetadata()
    }
}
