package org.seg7.familywatchlist.data.repository

import kotlinx.coroutines.flow.Flow
import org.seg7.familywatchlist.common.AppClock
import org.seg7.familywatchlist.data.local.dao.WatchlistDao
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.WatchlistEntryEntity
import org.seg7.familywatchlist.data.local.entity.WatchlistState

/** PLAN.md §2: one shared family Want-to-Watch list, tagged with who added each title. */
class WatchlistRepository(
    private val watchlistDao: WatchlistDao,
    private val clock: AppClock,
) {
    fun observeActive(): Flow<List<WatchlistEntryEntity>> = watchlistDao.observeByState(WatchlistState.ACTIVE)

    suspend fun get(tmdbId: Int, mediaType: MediaType): WatchlistEntryEntity? = watchlistDao.get(tmdbId, mediaType)

    suspend fun add(tmdbId: Int, mediaType: MediaType, addedByProfileId: Long) {
        watchlistDao.upsert(
            WatchlistEntryEntity(
                tmdbId = tmdbId,
                mediaType = mediaType,
                addedByProfileId = addedByProfileId,
                addedAt = clock.nowMillis(),
                state = WatchlistState.ACTIVE,
            )
        )
    }

    suspend fun remove(tmdbId: Int, mediaType: MediaType) =
        watchlistDao.updateState(tmdbId, mediaType, WatchlistState.REMOVED)
}
