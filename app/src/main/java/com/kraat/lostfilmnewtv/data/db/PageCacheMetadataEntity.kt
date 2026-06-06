package com.kraat.lostfilmnewtv.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "page_cache_metadata",
    indices = [Index(value = ["fetchedAt"])],
)
data class PageCacheMetadataEntity(
    @PrimaryKey val pageNumber: Int,
    val fetchedAt: Long,
    val itemCount: Int,
    /**
     * Признак наличия следующей страницы, сохранённый последним успешным сетевым ответом.
     * Используется для показа кэша без сетевого запроса (stale-while-revalidate):
     * при наличии кэша в Room мы можем отдать `hasNextPage` из метаданных, не дожидаясь сети.
     */
    val hasNextPage: Boolean = true,
)
