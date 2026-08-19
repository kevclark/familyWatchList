package org.seg7.familywatchlist.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import org.seg7.familywatchlist.data.local.entity.DiscoverCacheEntity

@Dao
interface DiscoverCacheDao {
    @Upsert
    suspend fun upsertAll(rows: List<DiscoverCacheEntity>)

    @Query("DELETE FROM discover_cache WHERE queryHash = :queryHash")
    suspend fun deleteForQuery(queryHash: String)

    /** Invalidates every cached discover/recommendations page — see [DiscoverRepository.invalidateAllCachedPages]. */
    @Query("DELETE FROM discover_cache")
    suspend fun deleteAll()

    @Query("SELECT * FROM discover_cache WHERE queryHash = :queryHash ORDER BY ord ASC")
    suspend fun getForQuery(queryHash: String): List<DiscoverCacheEntity>

    /** Null if this query hash has never been cached — distinct from "cached but empty". */
    @Query("SELECT MIN(fetchedAt) FROM discover_cache WHERE queryHash = :queryHash")
    suspend fun fetchedAtForQuery(queryHash: String): Long?

    @Transaction
    suspend fun replaceForQuery(queryHash: String, rows: List<DiscoverCacheEntity>) {
        deleteForQuery(queryHash)
        upsertAll(rows)
    }
}
