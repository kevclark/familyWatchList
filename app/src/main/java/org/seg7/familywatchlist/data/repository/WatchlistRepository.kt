package org.seg7.familywatchlist.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.seg7.familywatchlist.common.AppClock
import org.seg7.familywatchlist.data.local.dao.WatchlistDao
import org.seg7.familywatchlist.data.local.dao.WatchlistItem
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.WatchlistEntryEntity
import org.seg7.familywatchlist.data.local.entity.WatchlistState

/** Outcome of [WatchlistRepository.add]/[WatchlistRepository.toggle] — lets callers tell a real add/remove apart from a gate rejection so they can surface *why* nothing happened. */
enum class WatchlistAddResult { ADDED, REMOVED, UNAVAILABLE }

/**
 * PLAN.md §2: one shared family Want-to-Watch list, tagged with who added each title.
 *
 * PLAN.md §5a: adding is gated to GB availability on a subscribed provider — [isAvailable] is
 * [AvailabilityGate.isAvailableOnSubscribedProvider] in production (wired in `AppContainer`),
 * reused rather than duplicated so the definition of "available" can't drift from Search's. It
 * defaults to "always available" so every other caller of this repository (removing, re-reading,
 * and the many existing tests that seed watchlist rows directly) is unaffected — only the
 * `AppContainer`-built instance the app actually runs enforces the gate. The gate is *add-time
 * only*: an entry that was available when added and later loses availability is **not**
 * retroactively removed here (PLAN.md §5a — flagged in this pass's report as a default Kev
 * hasn't explicitly confirmed). It keeps showing on the list with the existing "not currently
 * available" indicator on the details screen.
 */
class WatchlistRepository(
    private val watchlistDao: WatchlistDao,
    private val clock: AppClock,
    private val isAvailable: suspend (tmdbId: Int, mediaType: MediaType) -> Boolean = { _, _ -> true },
) {
    fun observeActive(): Flow<List<WatchlistEntryEntity>> = watchlistDao.observeByState(WatchlistState.ACTIVE)

    /** "My List" read-model: active entries joined to their cached titles, newest addition first. */
    fun observeActiveItems(): Flow<List<WatchlistItem>> =
        watchlistDao.observeItemsByState(WatchlistState.ACTIVE)

    /** Whether a given title is currently on the list — drives the ＋/✓ toggle on details and search cards. */
    fun observeIsListed(tmdbId: Int, mediaType: MediaType): Flow<Boolean> =
        watchlistDao.observe(tmdbId, mediaType).map { it?.state == WatchlistState.ACTIVE }

    suspend fun get(tmdbId: Int, mediaType: MediaType): WatchlistEntryEntity? = watchlistDao.get(tmdbId, mediaType)

    /**
     * PLAN.md §5a: blocked (returns [WatchlistAddResult.UNAVAILABLE], nothing written) unless
     * [tmdbId]/[mediaType] currently has GB availability on a subscribed provider. Callers must
     * surface this to the user rather than treat it as a silent no-op — a title from History or
     * an old details-screen visit can easily have since lost availability.
     */
    suspend fun add(tmdbId: Int, mediaType: MediaType, addedByProfileId: Long): WatchlistAddResult {
        if (!isAvailable(tmdbId, mediaType)) return WatchlistAddResult.UNAVAILABLE
        watchlistDao.upsert(
            WatchlistEntryEntity(
                tmdbId = tmdbId,
                mediaType = mediaType,
                addedByProfileId = addedByProfileId,
                addedAt = clock.nowMillis(),
                state = WatchlistState.ACTIVE,
            )
        )
        return WatchlistAddResult.ADDED
    }

    suspend fun remove(tmdbId: Int, mediaType: MediaType) =
        watchlistDao.updateState(tmdbId, mediaType, WatchlistState.REMOVED)

    /**
     * One-call ＋/✓ for the details screen and search cards. Returns the resulting state so the
     * caller can show the right confirmation ("Added to your list" / "Removed" / blocked because
     * unavailable — PLAN.md §5a).
     *
     * Re-adding a REMOVED or WATCHED entry re-stamps `addedByProfileId`/`addedAt` to whoever is
     * adding it now — the list is shared, and "Kev added this in March, then watched it, then
     * Sam put it back last night" should credit Sam, not Kev. PLAN.md §4 also weights the
     * watchlist signal by `recencyWeight(addedAt)`, so the fresh timestamp is the correct
     * signal for the recommender too. Removing is never gated — taking something off the list is
     * always allowed regardless of current availability.
     */
    suspend fun toggle(tmdbId: Int, mediaType: MediaType, byProfileId: Long): WatchlistAddResult {
        val current = watchlistDao.get(tmdbId, mediaType)
        return if (current?.state == WatchlistState.ACTIVE) {
            remove(tmdbId, mediaType)
            WatchlistAddResult.REMOVED
        } else {
            add(tmdbId, mediaType, byProfileId)
        }
    }
}
