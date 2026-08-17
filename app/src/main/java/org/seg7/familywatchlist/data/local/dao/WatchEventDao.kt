package org.seg7.familywatchlist.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
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

    @Query("SELECT * FROM watch_events WHERE id = :id")
    suspend fun getEvent(id: Long): WatchEventEntity?

    @Query("DELETE FROM watch_events WHERE id = :id")
    suspend fun deleteEventById(id: Long)

    @Update
    suspend fun updateEvent(event: WatchEventEntity)

    /**
     * History's read-model (PLAN.md §5 screen 7): every event joined to its cached title, newest
     * first. Profile filtering is applied *in the ViewModel* off [observeAllTags] rather than by
     * a second parameterised query, because History also renders "who watched it" chips on every
     * row — so the tag map has to be in memory anyway, and filtering it there avoids re-querying
     * the whole list every time the filter chip changes.
     */
    @Query(
        """
        SELECT we.id AS id, we.tmdbId AS tmdbId, we.mediaType AS mediaType,
               we.watchedAt AS watchedAt, we.note AS note,
               t.title AS title, t.posterPath AS posterPath, t.year AS year
        FROM watch_events we
        LEFT JOIN titles t ON t.tmdbId = we.tmdbId AND t.mediaType = we.mediaType
        ORDER BY we.watchedAt DESC, we.id DESC
        """
    )
    fun observeAllItems(): Flow<List<WatchEventItem>>

    @Query("SELECT * FROM watch_event_profiles")
    fun observeAllTags(): Flow<List<WatchEventProfileEntity>>

    /** Replaces an event's profile tags wholesale — editing "who watched" in the log-watch sheet. */
    @Transaction
    suspend fun replaceTags(watchEventId: Long, profileIds: List<Long>) {
        deleteTagsForEvent(watchEventId)
        insertTags(profileIds.map { WatchEventProfileEntity(watchEventId = watchEventId, profileId = it) })
    }
}
