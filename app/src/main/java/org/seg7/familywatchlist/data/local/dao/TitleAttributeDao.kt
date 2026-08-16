package org.seg7.familywatchlist.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.TitleAttributeEntity

@Dao
interface TitleAttributeDao {
    @Upsert
    suspend fun upsertAll(attributes: List<TitleAttributeEntity>)

    @Query("DELETE FROM title_attributes WHERE tmdbId = :tmdbId AND mediaType = :mediaType")
    suspend fun deleteForTitle(tmdbId: Int, mediaType: MediaType)

    @Query("SELECT * FROM title_attributes WHERE tmdbId = :tmdbId AND mediaType = :mediaType ORDER BY ord ASC")
    fun observeForTitle(tmdbId: Int, mediaType: MediaType): Flow<List<TitleAttributeEntity>>

    @Query("SELECT * FROM title_attributes WHERE tmdbId = :tmdbId AND mediaType = :mediaType ORDER BY ord ASC")
    suspend fun getForTitle(tmdbId: Int, mediaType: MediaType): List<TitleAttributeEntity>

    /** Replaces the full attribute set for a title in one go — refresh calls always resupply everything. */
    @androidx.room.Transaction
    suspend fun replaceForTitle(tmdbId: Int, mediaType: MediaType, attributes: List<TitleAttributeEntity>) {
        deleteForTitle(tmdbId, mediaType)
        upsertAll(attributes)
    }
}
