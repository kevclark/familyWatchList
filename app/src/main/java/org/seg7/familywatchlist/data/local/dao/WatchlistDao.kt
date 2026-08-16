package org.seg7.familywatchlist.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.WatchlistEntryEntity
import org.seg7.familywatchlist.data.local.entity.WatchlistState

@Dao
interface WatchlistDao {
    @Upsert
    suspend fun upsert(entry: WatchlistEntryEntity)

    @Query("SELECT * FROM watchlist_entries WHERE tmdbId = :tmdbId AND mediaType = :mediaType")
    suspend fun get(tmdbId: Int, mediaType: MediaType): WatchlistEntryEntity?

    @Query("SELECT * FROM watchlist_entries WHERE state = :state ORDER BY addedAt DESC")
    fun observeByState(state: WatchlistState): Flow<List<WatchlistEntryEntity>>

    @Query("UPDATE watchlist_entries SET state = :state WHERE tmdbId = :tmdbId AND mediaType = :mediaType")
    suspend fun updateState(tmdbId: Int, mediaType: MediaType, state: WatchlistState)

    /** PLAN.md §2: logging a watch of a listed title auto-flips its state to WATCHED. */
    @Query(
        "UPDATE watchlist_entries SET state = 'WATCHED' WHERE tmdbId = :tmdbId AND mediaType = :mediaType AND state = 'ACTIVE'"
    )
    suspend fun markWatchedIfActive(tmdbId: Int, mediaType: MediaType)
}
