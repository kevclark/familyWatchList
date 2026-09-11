package org.seg7.familywatchlist.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.ReviewEntity

@Dao
interface ReviewDao {
    @Upsert
    suspend fun upsertAll(reviews: List<ReviewEntity>)

    @Query("DELETE FROM reviews WHERE tmdbId = :tmdbId AND mediaType = :mediaType")
    suspend fun deleteForTitle(tmdbId: Int, mediaType: MediaType)

    @Query("SELECT * FROM reviews WHERE tmdbId = :tmdbId AND mediaType = :mediaType")
    fun observeForTitle(tmdbId: Int, mediaType: MediaType): Flow<List<ReviewEntity>>

    @Query("SELECT * FROM reviews WHERE tmdbId = :tmdbId AND mediaType = :mediaType")
    suspend fun getForTitle(tmdbId: Int, mediaType: MediaType): List<ReviewEntity>

    /** Replaces the full review set for a title in one go — mirrors [TitleAttributeDao.replaceForTitle]: refetches always resupply everything. */
    @androidx.room.Transaction
    suspend fun replaceForTitle(tmdbId: Int, mediaType: MediaType, reviews: List<ReviewEntity>) {
        deleteForTitle(tmdbId, mediaType)
        upsertAll(reviews)
    }
}
