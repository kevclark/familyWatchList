package org.seg7.familywatchlist.data.repository

import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.seg7.familywatchlist.data.local.dao.WatchEventDao
import org.seg7.familywatchlist.data.local.dao.WatchEventItem
import org.seg7.familywatchlist.data.local.dao.WatchlistDao
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.WatchEventEntity

/**
 * PLAN.md §2: logging a watch tags N profiles on one event, and auto-flips a matching
 * ACTIVE watchlist entry to WATCHED.
 */
class WatchEventRepository(
    private val watchEventDao: WatchEventDao,
    private val watchlistDao: WatchlistDao,
) {
    fun observeAll(): Flow<List<WatchEventEntity>> = watchEventDao.observeAll()

    fun observeForProfile(profileId: Long): Flow<List<WatchEventEntity>> = watchEventDao.observeForProfile(profileId)

    /** History's read-model (PLAN.md §5 screen 7): events joined to their cached titles, newest first. */
    fun observeAllItems(): Flow<List<WatchEventItem>> = watchEventDao.observeAllItems()

    /** eventId -> the profiles tagged on it, for History's "who watched" chips and profile filter. */
    fun observeTagsByEvent(): Flow<Map<Long, List<Long>>> = watchEventDao.observeAllTags()
        .map { tags -> tags.groupBy({ it.watchEventId }, { it.profileId }) }

    suspend fun getProfileIds(eventId: Long): List<Long> = watchEventDao.getProfileIdsForEvent(eventId)

    suspend fun getEvent(eventId: Long): WatchEventEntity? = watchEventDao.getEvent(eventId)

    suspend fun logWatch(
        tmdbId: Int,
        mediaType: MediaType,
        watchedAt: LocalDate,
        note: String?,
        profileIds: List<Long>,
    ): Long {
        val eventId = watchEventDao.logWatch(
            WatchEventEntity(tmdbId = tmdbId, mediaType = mediaType, watchedAt = watchedAt, note = note),
            profileIds,
        )
        watchlistDao.markWatchedIfActive(tmdbId, mediaType)
        return eventId
    }

    /**
     * Editing a logged watch (PLAN.md §5 screen 7: "tap to edit/delete an event"). The title
     * itself is never editable — that would be a different watch, so the user deletes and logs
     * again. Date, note and the tagged profiles all are.
     */
    suspend fun updateWatch(
        eventId: Long,
        watchedAt: LocalDate,
        note: String?,
        profileIds: List<Long>,
    ) {
        val existing = watchEventDao.getEvent(eventId) ?: return
        watchEventDao.updateEvent(existing.copy(watchedAt = watchedAt, note = note))
        watchEventDao.replaceTags(eventId, profileIds)
    }

    /**
     * Deletes an event and its profile tags. Tags go first and explicitly: PLAN.md §2's
     * WatchEventProfile has no foreign key (the entity declares none), so nothing would cascade
     * them away and they'd linger as orphans skewing the recommender's per-profile corpus.
     *
     * A deleted event does *not* un-flip the title's watchlist entry back to ACTIVE. That's a
     * judgment call: the flip to WATCHED is also reachable by other routes, and silently
     * resurrecting a title on the shared family list because one person corrected a date would
     * be more surprising than leaving it. Flagged for Kev in the M2b report.
     */
    suspend fun deleteWatch(eventId: Long) {
        watchEventDao.deleteTagsForEvent(eventId)
        watchEventDao.deleteEventById(eventId)
    }
}
