package org.seg7.familywatchlist.data.repository

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.seg7.familywatchlist.common.AppClock
import org.seg7.familywatchlist.data.local.dao.WatchlistDao
import org.seg7.familywatchlist.data.local.dao.WatchlistItem
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.WatchlistEntryEntity
import org.seg7.familywatchlist.data.local.entity.WatchlistState
import org.seg7.familywatchlist.data.remote.TmdbApi.Companion.REGION_GB

/** Outcome of [WatchlistRepository.add]/[WatchlistRepository.toggle] — lets callers tell a real add/remove apart from a gate rejection so they can surface *why* nothing happened. */
enum class WatchlistAddResult { ADDED, REMOVED, UNAVAILABLE }

/**
 * A [WatchlistItem] paired with whether it currently passes the same availability check
 * [WatchlistRepository.add] gates on — PLAN.md §5a's M2g refinement. An item added while
 * available can quietly lose it later (add-time gating only, never retroactively removed); the
 * UI needs to know which ones so it can render them dimmed, with a direct clean-up action,
 * wherever the list appears (Home's "My List" carousel, the full My List screen).
 */
data class WatchlistItemAvailability(
    val item: WatchlistItem,
    val isAvailable: Boolean,
)

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
    private val isAvailable: suspend (tmdbId: Int, mediaType: MediaType, region: String) -> Boolean = { _, _, _ -> true },
) {
    fun observeActive(): Flow<List<WatchlistEntryEntity>> = watchlistDao.observeByState(WatchlistState.ACTIVE)

    /** "My List" read-model: active entries joined to their cached titles, newest addition first. */
    fun observeActiveItems(): Flow<List<WatchlistItem>> =
        watchlistDao.observeItemsByState(WatchlistState.ACTIVE)

    /**
     * [observeActiveItems] with each entry's current availability resolved alongside it —
     * PLAN.md §5a's M2g refinement. Reuses the exact same [isAvailable] check `add()` already
     * gates on (in production, [AvailabilityGate.isAvailableOnSubscribedProvider]) rather than a
     * third definition of "available" — Home's My List carousel and the full My List screen both
     * read this instead of [observeActiveItems] so they can dim an item that's lost availability
     * and offer a direct remove action for it.
     *
     * [regionFlow] (PLAN.md §7 M2f) is a live [Flow] rather than a one-shot value, deliberately —
     * unlike a one-shot suspend read, combining against it means a region change made in Settings
     * while this screen is open re-resolves availability immediately, not only after a manual
     * refresh or screen recreation. Real callers pass `UserPreferencesRepository.region`.
     *
     * Checked per item, in parallel, on every emission of the underlying list — deliberately no
     * extra caching/throttling layer beyond what [isAvailable] itself already does. A family's
     * watchlist is expected to be short (unlike a ~20-result search page), so this is fine to do
     * for every visible item rather than the batching Search needed.
     */
    fun observeActiveItemsWithAvailability(regionFlow: Flow<String>): Flow<List<WatchlistItemAvailability>> =
        combine(watchlistDao.observeItemsByState(WatchlistState.ACTIVE), regionFlow) { items, region -> items to region }
            .map { (items, region) ->
                coroutineScope {
                    items
                        .map { item -> async { WatchlistItemAvailability(item, isAvailable(item.tmdbId, item.mediaType, region)) } }
                        .awaitAll()
                }
            }

    /** Whether a given title is currently on the list — drives the ＋/✓ toggle on details and search cards. */
    fun observeIsListed(tmdbId: Int, mediaType: MediaType): Flow<Boolean> =
        watchlistDao.observe(tmdbId, mediaType).map { it?.state == WatchlistState.ACTIVE }

    suspend fun get(tmdbId: Int, mediaType: MediaType): WatchlistEntryEntity? = watchlistDao.get(tmdbId, mediaType)

    /**
     * PLAN.md §5a: blocked (returns [WatchlistAddResult.UNAVAILABLE], nothing written) unless
     * [tmdbId]/[mediaType] currently has [region] availability on a subscribed provider. Callers
     * must surface this to the user rather than treat it as a silent no-op — a title from
     * History or an old details-screen visit can easily have since lost availability. [region]
     * (PLAN.md §7 M2f) defaults to [REGION_GB]; real callers thread the live
     * `UserPreferencesRepository.region` value through.
     */
    suspend fun add(tmdbId: Int, mediaType: MediaType, addedByProfileId: Long, region: String = REGION_GB): WatchlistAddResult {
        if (!isAvailable(tmdbId, mediaType, region)) return WatchlistAddResult.UNAVAILABLE
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
    suspend fun toggle(tmdbId: Int, mediaType: MediaType, byProfileId: Long, region: String = REGION_GB): WatchlistAddResult {
        val current = watchlistDao.get(tmdbId, mediaType)
        return if (current?.state == WatchlistState.ACTIVE) {
            remove(tmdbId, mediaType)
            WatchlistAddResult.REMOVED
        } else {
            add(tmdbId, mediaType, byProfileId, region)
        }
    }
}
