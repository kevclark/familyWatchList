package org.seg7.familywatchlist.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.TitleEntity

@Dao
interface TitleDao {
    @Upsert
    suspend fun upsert(title: TitleEntity)

    @Upsert
    suspend fun upsertAll(titles: List<TitleEntity>)

    @Query("SELECT * FROM titles WHERE tmdbId = :tmdbId AND mediaType = :mediaType")
    fun observe(tmdbId: Int, mediaType: MediaType): Flow<TitleEntity?>

    @Query("SELECT * FROM titles WHERE tmdbId = :tmdbId AND mediaType = :mediaType")
    suspend fun get(tmdbId: Int, mediaType: MediaType): TitleEntity?

    @Query("SELECT * FROM titles WHERE tmdbId IN (:tmdbIds) AND mediaType = :mediaType")
    suspend fun getByIds(tmdbIds: List<Int>, mediaType: MediaType): List<TitleEntity>

    /**
     * PLAN.md §7 M2f: forces every cached title stale (metadata *and* provider-availability TTL
     * alike — see [org.seg7.familywatchlist.data.repository.TitleRepository]'s kdoc on why one
     * `fetchedAt` governs both) by resetting it to the epoch. Used when the region preference
     * changes: `provider_availability` rows carry no region column, so a title fetched under the
     * old region would otherwise look "fresh" for up to 7 more days and silently keep showing
     * the old region's providers mislabeled as the new one's.
     */
    @Query("UPDATE titles SET fetchedAt = 0")
    suspend fun expireAllFetchedAt()
}
