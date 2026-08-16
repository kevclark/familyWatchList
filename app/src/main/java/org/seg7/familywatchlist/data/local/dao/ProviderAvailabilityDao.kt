package org.seg7.familywatchlist.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.ProviderAvailabilityEntity

@Dao
interface ProviderAvailabilityDao {
    @Upsert
    suspend fun upsertAll(rows: List<ProviderAvailabilityEntity>)

    @Query("DELETE FROM provider_availability WHERE tmdbId = :tmdbId AND mediaType = :mediaType")
    suspend fun deleteForTitle(tmdbId: Int, mediaType: MediaType)

    @Query("SELECT * FROM provider_availability WHERE tmdbId = :tmdbId AND mediaType = :mediaType")
    fun observeForTitle(tmdbId: Int, mediaType: MediaType): Flow<List<ProviderAvailabilityEntity>>

    @Query("SELECT * FROM provider_availability WHERE tmdbId = :tmdbId AND mediaType = :mediaType")
    suspend fun getForTitle(tmdbId: Int, mediaType: MediaType): List<ProviderAvailabilityEntity>

    /** Replaces availability for a title in one go — PLAN.md §3's 7-day TTL always refetches the full set. */
    @Transaction
    suspend fun replaceForTitle(tmdbId: Int, mediaType: MediaType, rows: List<ProviderAvailabilityEntity>) {
        deleteForTitle(tmdbId, mediaType)
        upsertAll(rows)
    }
}
