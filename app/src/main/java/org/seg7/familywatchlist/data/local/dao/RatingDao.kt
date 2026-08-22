package org.seg7.familywatchlist.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.RatingEntity

@Dao
interface RatingDao {
    /** PLAN.md §2: latest write wins — @Upsert on the composite PK gives us that for free. */
    @Upsert
    suspend fun upsert(rating: RatingEntity)

    @Query("SELECT * FROM ratings WHERE profileId = :profileId AND tmdbId = :tmdbId AND mediaType = :mediaType")
    suspend fun get(profileId: Long, tmdbId: Int, mediaType: MediaType): RatingEntity?

    @Query("SELECT * FROM ratings WHERE profileId = :profileId")
    fun observeForProfile(profileId: Long): Flow<List<RatingEntity>>

    @Query("SELECT * FROM ratings WHERE profileId = :profileId")
    suspend fun getForProfile(profileId: Long): List<RatingEntity>

    /** Every rating in the database — PLAN.md §5b M3i item 2: History needs every tagged profile's rating on every row, not just one title at a time. */
    @Query("SELECT * FROM ratings")
    fun observeAll(): Flow<List<RatingEntity>>

    /** Every profile's thumbs on one title — the details screen and the log-watch sheet both need this. */
    @Query("SELECT * FROM ratings WHERE tmdbId = :tmdbId AND mediaType = :mediaType")
    fun observeForTitle(tmdbId: Int, mediaType: MediaType): Flow<List<RatingEntity>>

    @Query("SELECT * FROM ratings WHERE tmdbId = :tmdbId AND mediaType = :mediaType")
    suspend fun getForTitle(tmdbId: Int, mediaType: MediaType): List<RatingEntity>

    /** Clearing a thumbs is distinct from setting NEUTRAL — "no opinion recorded" vs "meh". */
    @Query("DELETE FROM ratings WHERE profileId = :profileId AND tmdbId = :tmdbId AND mediaType = :mediaType")
    suspend fun delete(profileId: Long, tmdbId: Int, mediaType: MediaType)
}
