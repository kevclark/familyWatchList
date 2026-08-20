package org.seg7.familywatchlist.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import org.seg7.familywatchlist.data.local.entity.ShortlistEntryEntity
import org.seg7.familywatchlist.data.local.entity.ShortlistState

@Dao
interface ShortlistDao {
    @Upsert
    suspend fun upsertAll(entries: List<ShortlistEntryEntity>)

    @Query("SELECT * FROM shortlist_entries WHERE weekStart = :weekStart AND scopeKey = :scopeKey ORDER BY score DESC")
    fun observeForScope(weekStart: LocalDate, scopeKey: String): Flow<List<ShortlistEntryEntity>>

    /** One-shot read used by [org.seg7.familywatchlist.data.repository.RecommendationRepository] to exclude this cycle's DISMISSED candidates before scoring. */
    @Query("SELECT * FROM shortlist_entries WHERE weekStart = :weekStart AND scopeKey = :scopeKey")
    suspend fun getForScope(weekStart: LocalDate, scopeKey: String): List<ShortlistEntryEntity>

    @Query(
        "UPDATE shortlist_entries SET state = :state WHERE weekStart = :weekStart AND scopeKey = :scopeKey AND tmdbId = :tmdbId AND mediaType = :mediaType"
    )
    suspend fun updateState(
        weekStart: LocalDate,
        scopeKey: String,
        tmdbId: Int,
        mediaType: org.seg7.familywatchlist.data.local.entity.MediaType,
        state: ShortlistState,
    )

    @Query("DELETE FROM shortlist_entries WHERE weekStart < :before")
    suspend fun deleteOlderThan(before: LocalDate)

    /**
     * Clears this scope/week's still-SUGGESTED rows before a recompute writes a fresh set —
     * without this, [org.seg7.familywatchlist.data.repository.RecommendationRepository]'s
     * `upsertAll` only ever adds/updates rows for tmdbIds present in the new assembled list,
     * so a title that scored well last time but didn't make this recompute's cut (e.g. after a
     * slider change) would linger forever, growing the shortlist past its target size on every
     * recompute instead of replacing it. DISMISSED and WATCHED rows are deliberately untouched:
     * DISMISSED still needs to exclude that candidate from this cycle's next recompute, and
     * WATCHED is a real historical record, not a stale suggestion.
     */
    @Query("DELETE FROM shortlist_entries WHERE weekStart = :weekStart AND scopeKey = :scopeKey AND state = 'SUGGESTED'")
    suspend fun deleteSuggestedForScope(weekStart: LocalDate, scopeKey: String)
}
