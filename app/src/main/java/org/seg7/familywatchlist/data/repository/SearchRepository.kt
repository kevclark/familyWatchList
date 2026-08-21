package org.seg7.familywatchlist.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import org.seg7.familywatchlist.common.AppClock
import org.seg7.familywatchlist.data.local.dao.TitleDao
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.TitleEntity
import org.seg7.familywatchlist.data.recommend.FamilyBlend
import org.seg7.familywatchlist.data.remote.TmdbApi
import org.seg7.familywatchlist.data.remote.TmdbApi.Companion.REGION_GB
import org.seg7.familywatchlist.data.remote.dto.MediaSummaryDto

/**
 * PLAN.md §5a "Search & watchlist availability gating": search is *not* a general TMDB catalog
 * finder — an earlier version of this class was, and its own kdoc said so ("§5 screen 5 is a
 * title finder"), but that let a title with zero UK streaming availability show up as a normal
 * result, which defeats the app's whole "what can I actually watch tonight" premise. Search is
 * now search-then-check:
 *  1. Run `/search/multi`, keep movie/TV, persist stubs — unchanged from before.
 *  2. Resolve each survivor's GB watch-provider availability via [AvailabilityGate], which reuses
 *     cached [org.seg7.familywatchlist.data.local.entity.ProviderAvailabilityEntity] rows from
 *     any prior detail fetch and only reaches the network (through the existing 4 req/s throttle)
 *     for titles not already cached.
 *  3. Drop anything with no GB availability on a currently-subscribed provider.
 *  4. PLAN.md §4/§8 (M3g safety fix): also drop anything over the caller's [search] `ageRatingCap`
 *     — Search is a title-surfacing path reachable by a capped profile directly (unlike the
 *     watchlist add-gate, a deliberate action typically taken on the shared family list rather
 *     than "shown to" a specific child), so it must respect the same age cap the real recommender
 *     already enforces. Reuses [FamilyBlend.isOverCap], the same shared check — a title with no
 *     cached certification data is never excluded (PLAN.md §8: unknown != unsafe).
 *
 * There's no `/search/multi?with_watch_providers=` — TMDB doesn't offer that parameter on this
 * endpoint, only on `/discover`, which doesn't take a free-text query — so this can't be done in
 * one call. [search] returns a [Flow] rather than a single list because of it: an uncached query
 * takes a checked title's availability call a moment to resolve, so results settle in
 * progressively (surviving titles are appended to the emitted list, in original relevance order,
 * as each check completes) rather than all appearing at once. This is a deliberate trade-off
 * (PLAN.md §5a), not something to hide behind a spinner that blocks the whole screen.
 */
class SearchRepository(
    private val titleDao: TitleDao,
    private val api: TmdbApi,
    private val clock: AppClock,
    private val availabilityGate: AvailabilityGate,
) {
    /**
     * Runs the search, then emits the growing list of currently-available results as each
     * availability check completes — the last emission (once the flow finishes) is the final,
     * complete answer. Collecting this flow is cancellable mid-flight: cancelling the collecting
     * coroutine (e.g. because a newer query superseded this one) cancels every in-flight
     * availability check started here, so a slow, stale batch can never write over a newer
     * query's results — see [org.seg7.familywatchlist.ui.search.SearchViewModel] for the
     * cancel-on-requery wiring.
     */
    /**
     * [region] (PLAN.md §7 M2f) defaults to [REGION_GB]; real callers (`SearchViewModel`) thread
     * the live `UserPreferencesRepository.region` value through. [ageRatingCap] (PLAN.md §4/§8,
     * M3g) defaults to `null` ("don't filter") for the same reason — real callers resolve and
     * thread the active profile's/Family's live cap via
     * [org.seg7.familywatchlist.data.repository.RecommendationRepository.resolveAgeRatingCap].
     */
    fun search(query: String, region: String = REGION_GB, page: Int = 1, ageRatingCap: String? = null): Flow<List<TitleEntity>> = channelFlow {
        val candidates = fetchCandidates(query, page)
        if (candidates.isEmpty()) {
            send(emptyList())
            return@channelFlow
        }

        // Indexed slots rather than an append-as-you-go list: checks resolve in whatever order
        // the network/cache happens to answer them, but the results a user sees should still
        // read in TMDB's relevance order, the same as an unfiltered search would.
        val slots = arrayOfNulls<TitleEntity>(candidates.size)
        val sendMutex = Mutex()
        val concurrencyLimit = Semaphore(MAX_CONCURRENT_CHECKS)

        val checks = candidates.mapIndexed { index, candidate ->
            launch {
                concurrencyLimit.withPermit {
                    val available = availabilityGate.isAvailableOnSubscribedProvider(candidate.tmdbId, candidate.mediaType, region)
                    if (!available) return@withPermit
                    // The availability check above may have just detail-fetched this title for
                    // the first time (ensureFresh inside AvailabilityGate) — re-read through Room
                    // rather than trusting `candidate`'s pre-fetch (possibly stub-only,
                    // certification-less) snapshot.
                    val certification = titleDao.get(candidate.tmdbId, candidate.mediaType)?.certification ?: candidate.certification
                    if (FamilyBlend.isOverCap(certification, ageRatingCap)) return@withPermit
                    sendMutex.withLock {
                        slots[index] = candidate
                        send(slots.filterNotNull())
                    }
                }
            }
        }
        checks.joinAll()
        // Guarantees a terminal emission even when every candidate was filtered out (nothing
        // above ever called `send` in that case) — without this, a fully-unavailable query
        // would leave the flow's collector never invoked at all, and the UI stuck "still
        // checking" forever instead of settling on "nothing available".
        sendMutex.withLock { send(slots.filterNotNull()) }
    }

    /** `/search/multi`, movie/TV kept, everything else (e.g. `person`) dropped, results persisted as stubs. */
    private suspend fun fetchCandidates(query: String, page: Int): List<TitleEntity> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        val results = api.searchMulti(trimmed, page).results
            .mapNotNull { dto -> dto.mediaTypeOrNull()?.let { dto to it } }
            // A result with no title is unrenderable; TMDB occasionally returns these.
            .filter { (dto, _) -> !(dto.title ?: dto.name).isNullOrBlank() }

        val now = clock.nowMillis()
        persistStubs(results, now)

        // Re-read through Room so callers get whatever detail is already cached (runtime,
        // certification, trailer key) rather than the thinner search payload — offline-first,
        // per PLAN.md §3: "Room is the source of truth for the UI".
        return results.reReadThroughRoom()
    }

    /**
     * A search hit must never overwrite a title that already has full detail cached, for the
     * same reason [DiscoverRepository] guards against it: the summary payload has no runtime,
     * certification or trailer key, so a blind upsert would blank fields the details screen
     * needs and force a refetch.
     */
    private suspend fun persistStubs(results: List<Pair<MediaSummaryDto, MediaType>>, now: Long) {
        MediaType.entries.forEach { mediaType ->
            val forType = results.filter { it.second == mediaType }.map { it.first }
            if (forType.isEmpty()) return@forEach
            val detailedIds = titleDao.getByIds(forType.map { it.id }, mediaType)
                .filter { it.runtimeMin != null || it.certification != null }
                .map { it.tmdbId }
                .toSet()
            val stubs = forType.filter { it.id !in detailedIds }.map { it.toStubTitleEntity(mediaType, now) }
            if (stubs.isNotEmpty()) titleDao.upsertAll(stubs)
        }
    }

    private suspend fun List<Pair<MediaSummaryDto, MediaType>>.reReadThroughRoom(): List<TitleEntity> {
        val byType = MediaType.entries.associateWith { mediaType ->
            val ids = filter { it.second == mediaType }.map { it.first.id }
            if (ids.isEmpty()) emptyMap() else titleDao.getByIds(ids, mediaType).associateBy { it.tmdbId }
        }
        return mapNotNull { (dto, mediaType) -> byType[mediaType]?.get(dto.id) }
    }

    private fun MediaSummaryDto.mediaTypeOrNull(): MediaType? = when (mediaType) {
        "movie" -> MediaType.MOVIE
        "tv" -> MediaType.TV
        else -> null
    }

    private companion object {
        /** Caps how many availability checks run concurrently; the 4 req/s throttle still governs actual network pacing. */
        const val MAX_CONCURRENT_CHECKS = 5
    }
}
