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

    @Query("SELECT * FROM watchlist_entries WHERE tmdbId = :tmdbId AND mediaType = :mediaType")
    fun observe(tmdbId: Int, mediaType: MediaType): Flow<WatchlistEntryEntity?>

    /**
     * The "My List" read-model: entries joined to their cached title row. LEFT JOIN because an
     * entry can briefly outlive its cached title (Room is the source of truth for the UI, and
     * the title refetches lazily on view) — the UI shows a placeholder tile rather than
     * dropping the row.
     */
    @Query(
        """
        SELECT w.tmdbId AS tmdbId, w.mediaType AS mediaType, t.title AS title,
               t.posterPath AS posterPath, t.year AS year,
               w.addedByProfileId AS addedByProfileId, w.addedAt AS addedAt
        FROM watchlist_entries w
        LEFT JOIN titles t ON t.tmdbId = w.tmdbId AND t.mediaType = w.mediaType
        WHERE w.state = :state
        ORDER BY w.addedAt DESC
        """
    )
    fun observeItemsByState(state: WatchlistState): Flow<List<WatchlistItem>>

    @Query("UPDATE watchlist_entries SET state = :state WHERE tmdbId = :tmdbId AND mediaType = :mediaType")
    suspend fun updateState(tmdbId: Int, mediaType: MediaType, state: WatchlistState)

    /** PLAN.md §2: logging a watch of a listed title auto-flips its state to WATCHED. */
    @Query(
        "UPDATE watchlist_entries SET state = 'WATCHED' WHERE tmdbId = :tmdbId AND mediaType = :mediaType AND state = 'ACTIVE'"
    )
    suspend fun markWatchedIfActive(tmdbId: Int, mediaType: MediaType)
}
