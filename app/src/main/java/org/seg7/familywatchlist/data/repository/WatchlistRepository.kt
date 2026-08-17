package org.seg7.familywatchlist.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.seg7.familywatchlist.common.AppClock
import org.seg7.familywatchlist.data.local.dao.WatchlistDao
import org.seg7.familywatchlist.data.local.dao.WatchlistItem
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.WatchlistEntryEntity
import org.seg7.familywatchlist.data.local.entity.WatchlistState

/** PLAN.md §2: one shared family Want-to-Watch list, tagged with who added each title. */
class WatchlistRepository(
    private val watchlistDao: WatchlistDao,
    private val clock: AppClock,
) {
    fun observeActive(): Flow<List<WatchlistEntryEntity>> = watchlistDao.observeByState(WatchlistState.ACTIVE)

    /** "My List" read-model: active entries joined to their cached titles, newest addition first. */
    fun observeActiveItems(): Flow<List<WatchlistItem>> =
        watchlistDao.observeItemsByState(WatchlistState.ACTIVE)

    /** Whether a given title is currently on the list — drives the ＋/✓ toggle on details and search cards. */
    fun observeIsListed(tmdbId: Int, mediaType: MediaType): Flow<Boolean> =
        watchlistDao.observe(tmdbId, mediaType).map { it?.state == WatchlistState.ACTIVE }

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

    /**
     * One-call ＋/✓ for the details screen and search cards. Returns the resulting state so the
     * caller can show the right confirmation ("Added to your list" / "Removed").
     *
     * Re-adding a REMOVED or WATCHED entry re-stamps `addedByProfileId`/`addedAt` to whoever is
     * adding it now — the list is shared, and "Kev added this in March, then watched it, then
     * Sam put it back last night" should credit Sam, not Kev. PLAN.md §4 also weights the
     * watchlist signal by `recencyWeight(addedAt)`, so the fresh timestamp is the correct
     * signal for the recommender too.
     */
    suspend fun toggle(tmdbId: Int, mediaType: MediaType, byProfileId: Long): Boolean {
        val current = watchlistDao.get(tmdbId, mediaType)
        return if (current?.state == WatchlistState.ACTIVE) {
            remove(tmdbId, mediaType)
            false
        } else {
            add(tmdbId, mediaType, byProfileId)
            true
        }
    }
}
