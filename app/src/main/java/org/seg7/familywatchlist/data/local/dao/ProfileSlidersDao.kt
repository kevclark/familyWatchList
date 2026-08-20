package org.seg7.familywatchlist.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.seg7.familywatchlist.data.local.entity.ProfileSlidersEntity

@Dao
interface ProfileSlidersDao {
    @Upsert
    suspend fun upsert(sliders: ProfileSlidersEntity)

    @Query("SELECT * FROM profile_sliders WHERE profileId = :profileId")
    suspend fun get(profileId: Long): ProfileSlidersEntity?

    @Query("SELECT * FROM profile_sliders WHERE profileId = :profileId")
    fun observe(profileId: Long): Flow<ProfileSlidersEntity?>

    @Query("DELETE FROM profile_sliders WHERE profileId = :profileId")
    suspend fun delete(profileId: Long)
}
