package org.seg7.familywatchlist.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import org.seg7.familywatchlist.data.local.entity.WatchEventEntity
import org.seg7.familywatchlist.data.local.entity.WatchEventProfileEntity

@Dao
interface WatchEventDao {
    @Insert
    suspend fun insertEvent(event: WatchEventEntity): Long

    @Insert
    suspend fun insertTags(tags: List<WatchEventProfileEntity>)

    @Delete
    suspend fun deleteEvent(event: WatchEventEntity)

    @Query("DELETE FROM watch_event_profiles WHERE watchEventId = :watchEventId")
    suspend fun deleteTagsForEvent(watchEventId: Long)

    /** Logs one watch, multi-tagged with N profiles — PLAN.md §2 WatchEvent/WatchEventProfile. */
    @Transaction
    suspend fun logWatch(event: WatchEventEntity, profileIds: List<Long>): Long {
        val eventId = insertEvent(event)
        insertTags(profileIds.map { WatchEventProfileEntity(watchEventId = eventId, profileId = it) })
        return eventId
    }

    @Query("SELECT * FROM watch_events ORDER BY watchedAt DESC, id DESC")
    fun observeAll(): Flow<List<WatchEventEntity>>

    @Query(
        """
        SELECT we.* FROM watch_events we
        INNER JOIN watch_event_profiles wep ON wep.watchEventId = we.id
        WHERE wep.profileId = :profileId
        ORDER BY we.watchedAt DESC, we.id DESC
        """
    )
    fun observeForProfile(profileId: Long): Flow<List<WatchEventEntity>>

    @Query("SELECT profileId FROM watch_event_profiles WHERE watchEventId = :watchEventId")
    suspend fun getProfileIdsForEvent(watchEventId: Long): List<Long>

    @Query(
        """
        SELECT we.* FROM watch_events we
        INNER JOIN watch_event_profiles wep ON wep.watchEventId = we.id
        WHERE wep.profileId = :profileId
        ORDER BY we.watchedAt DESC, we.id DESC
        """
    )
    suspend fun getForProfile(profileId: Long): List<WatchEventEntity>

    @Query("SELECT count(*) FROM watch_events we INNER JOIN watch_event_profiles wep ON wep.watchEventId = we.id WHERE wep.profileId = :profileId")
    suspend fun countForProfile(profileId: Long): Int
}
