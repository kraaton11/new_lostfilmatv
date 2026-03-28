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
    }
}
