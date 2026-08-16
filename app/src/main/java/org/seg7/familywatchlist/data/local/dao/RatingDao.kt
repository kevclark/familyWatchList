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
}
