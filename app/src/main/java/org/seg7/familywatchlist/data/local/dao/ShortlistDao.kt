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
}
