package org.seg7.familywatchlist.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.seg7.familywatchlist.data.local.entity.NotificationPreferenceEntity

@Dao
interface NotificationPreferenceDao {
    @Upsert
    suspend fun upsert(pref: NotificationPreferenceEntity)

    @Query("SELECT * FROM profile_notification_prefs WHERE profileId = :profileId")
    suspend fun get(profileId: Long): NotificationPreferenceEntity?

    @Query("SELECT * FROM profile_notification_prefs WHERE profileId = :profileId")
    fun observe(profileId: Long): Flow<NotificationPreferenceEntity?>

    @Query("DELETE FROM profile_notification_prefs WHERE profileId = :profileId")
    suspend fun delete(profileId: Long)

    /** One-shot snapshot for [org.seg7.familywatchlist.data.repository.BackupRepository]'s export. */
    @Query("SELECT * FROM profile_notification_prefs")
    suspend fun getAllOnce(): List<NotificationPreferenceEntity>

    @Upsert
    suspend fun upsertAll(prefs: List<NotificationPreferenceEntity>)

    @Query("DELETE FROM profile_notification_prefs")
    suspend fun deleteAll()
}
