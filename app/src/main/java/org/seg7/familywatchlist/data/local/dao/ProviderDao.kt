package org.seg7.familywatchlist.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.seg7.familywatchlist.data.local.entity.ProviderEntity

@Dao
interface ProviderDao {
    @Upsert
    suspend fun upsertAll(providers: List<ProviderEntity>)

    @Query("SELECT * FROM providers ORDER BY displayPriority ASC")
    fun observeAll(): Flow<List<ProviderEntity>>

    @Query("SELECT * FROM providers ORDER BY displayPriority ASC")
    suspend fun getAll(): List<ProviderEntity>

    @Query("SELECT * FROM providers WHERE subscribed = 1 ORDER BY displayPriority ASC")
    fun observeSubscribed(): Flow<List<ProviderEntity>>

    @Query("SELECT * FROM providers WHERE subscribed = 1")
    suspend fun getSubscribed(): List<ProviderEntity>

    @Query("SELECT count(*) FROM providers")
    suspend fun count(): Int

    @Query("UPDATE providers SET subscribed = :subscribed WHERE providerId = :providerId")
    suspend fun setSubscribed(providerId: Int, subscribed: Boolean)
}
