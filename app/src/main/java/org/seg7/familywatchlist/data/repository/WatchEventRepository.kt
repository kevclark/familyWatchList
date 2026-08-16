package org.seg7.familywatchlist.data.repository

import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import org.seg7.familywatchlist.data.local.dao.WatchEventDao
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
}
