package org.seg7.familywatchlist.data.repository

import java.util.concurrent.TimeUnit
import org.seg7.familywatchlist.common.AppClock
import org.seg7.familywatchlist.common.today
import org.seg7.familywatchlist.data.local.dao.DiscoverCacheDao
import org.seg7.familywatchlist.data.local.dao.TitleDao
import org.seg7.familywatchlist.data.local.entity.DiscoverCacheEntity
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.TitleEntity
import org.seg7.familywatchlist.data.remote.TmdbApi
import org.seg7.familywatchlist.data.remote.TmdbApi.Companion.REGION_GB
import org.seg7.familywatchlist.data.remote.dto.MediaSummaryDto

/**
 * Candidate pool for the recommender (PLAN.md §4, wired up in M3) — /discover and
 * /recommendations pages, cached 24h per query hash (PLAN.md §3).
 */
class DiscoverRepository(
    private val discoverCacheDao: DiscoverCacheDao,
    private val titleDao: TitleDao,
    private val api: TmdbApi,
    private val clock: AppClock,
) {
    /**
     * A `with_watch_providers` value of `null` gets dropped from the Retrofit request entirely
     * (Retrofit omits `null` `@Query` params from the URL), which would silently turn "popular
     * on your services" into "popular in the UK, unfiltered" — a real bug, not intentional
     * randomness, if it were ever allowed to reach the network call. So when nothing is
     * subscribed there is nothing to show: short-circuit before [toProviderParam] or the
     * network/cache are even touched, rather than falling back to an unfiltered page.
     */
    /**
     * [region] (PLAN.md §7 M2f) is a real call-time parameter, the same way
     * [subscribedProviderIds] already is — no compile-time default baked into the network call.
     * It defaults here to [REGION_GB] purely so the ~10 pre-existing tests of this class that
     * don't care about region don't need touching; every real caller (`HomeViewModel`) threads
     * the live `UserPreferencesRepository.region` value through explicitly. It's folded into
     * [queryHash] so a region switch is a different cache key, not stale/wrong data served under
     * the new region's label.
     */
    suspend fun discoverMovies(subscribedProviderIds: List<Int>, region: String = REGION_GB, page: Int = 1): List<TitleEntity> {
        if (subscribedProviderIds.isEmpty()) return emptyList()
        val queryHash = queryHash("discover_movie", subscribedProviderIds, region, page)
        return cachedOrFetch(queryHash, MediaType.MOVIE) {
            api.discoverMovies(watchRegion = region, withWatchProviders = subscribedProviderIds.toProviderParam(), page = page).results
        }
    }

    suspend fun discoverTv(subscribedProviderIds: List<Int>, region: String = REGION_GB, page: Int = 1): List<TitleEntity> {
        if (subscribedProviderIds.isEmpty()) return emptyList()
        val queryHash = queryHash("discover_tv", subscribedProviderIds, region, page)
        return cachedOrFetch(queryHash, MediaType.TV) {
            api.discoverTv(watchRegion = region, withWatchProviders = subscribedProviderIds.toProviderParam(), page = page).results
        }
    }

    /**
     * PLAN.md §5i: Home's "New films" row. TMDB has no "date added to provider", so "new" is a
     * recent theatrical/primary release (last [NEW_FILMS_WINDOW_MONTHS] months, relative to the
     * injected clock) that is on a subscribed provider in [region].
     *
     * This is Kev's ONE sign-off exception to the 2026-08-22 "no rent/buy in browsing" rule: the
     * query asks for flatrate|free|rent|buy. Every other discover call keeps flatrate|free
     * (see `DiscoverRepositoryTest`, which pins that). To badge rent/buy-only titles without a
     * detail fetch per card, a second query with flatrate|free only is cached alongside; a title
     * in the wide page but not in the narrow one is paid-only. That is exact, not heuristic: the
     * narrow result is the wide one minus paid-only titles in the same popularity order, so
     * anything in the wide top page that is included with a subscription is also in the narrow
     * top page.
     */
    suspend fun newMovies(subscribedProviderIds: List<Int>, region: String = REGION_GB): NewMovies {
        if (subscribedProviderIds.isEmpty()) return NewMovies(emptyList(), emptySet())
        val from = clock.today().minusMonths(NEW_FILMS_WINDOW_MONTHS).toString()
        val to = clock.today().toString()
        val providers = subscribedProviderIds.toProviderParam()
        val all = cachedOrFetch(queryHash("discover_new_movie", subscribedProviderIds, region, 1), MediaType.MOVIE) {
            api.discoverMovies(
                watchRegion = region,
                withWatchProviders = providers,
                monetizationTypes = NEW_FILMS_MONETIZATION,
                voteCountGte = NEW_VOTE_FLOOR,
                primaryReleaseDateGte = from,
                primaryReleaseDateLte = to,
            ).results
        }
        if (all.isEmpty()) return NewMovies(emptyList(), emptySet())
        val included = cachedOrFetch(queryHash("discover_new_movie_included", subscribedProviderIds, region, 1), MediaType.MOVIE) {
            api.discoverMovies(
                watchRegion = region,
                withWatchProviders = providers,
                voteCountGte = NEW_VOTE_FLOOR,
                primaryReleaseDateGte = from,
                primaryReleaseDateLte = to,
            ).results
        }.map { it.tmdbId }.toSet()
        return NewMovies(all, paidOnlyIds = all.map { it.tmdbId }.filterNot { it in included }.toSet())
    }

    /** PLAN.md §5i: Home's "New series" row — first aired in the last [NEW_SERIES_WINDOW_MONTHS] months, flatrate|free only. */
    suspend fun newTv(subscribedProviderIds: List<Int>, region: String = REGION_GB): List<TitleEntity> {
        if (subscribedProviderIds.isEmpty()) return emptyList()
        val from = clock.today().minusMonths(NEW_SERIES_WINDOW_MONTHS).toString()
        return cachedOrFetch(queryHash("discover_new_tv", subscribedProviderIds, region, 1), MediaType.TV) {
            api.discoverTv(
                watchRegion = region,
                withWatchProviders = subscribedProviderIds.toProviderParam(),
                voteCountGte = NEW_VOTE_FLOOR,
                firstAirDateGte = from,
            ).results
        }
    }

    /** [paidOnlyIds] are TMDB ids in [titles] offered on a subscribed provider only to rent/buy. */
    data class NewMovies(val titles: List<TitleEntity>, val paidOnlyIds: Set<Int>)

    /**
     * Subscribed-provider changes must invalidate previously-cached discover pages: otherwise
     * unsubscribing from a service (or subscribing to a new one) can keep serving a stale
     * "popular on your services" page — reflecting the *old* provider set — for up to
     * [DISCOVER_TTL_MS]. Called from [org.seg7.familywatchlist.data.repository.ProviderRepository.setSubscribed].
     */
    suspend fun invalidateAllCachedPages() = discoverCacheDao.deleteAll()

    // Recommendations aren't region-scoped at all (TMDB's /recommendations takes no
    // watch_region param) — the "region" slot in the shared queryHash format is a constant
    // placeholder here, not a real dimension of this cache key.
    suspend fun movieRecommendations(tmdbId: Int, page: Int = 1): List<TitleEntity> {
        val queryHash = queryHash("movie_recs_$tmdbId", emptyList(), region = "-", page)
        return cachedOrFetch(queryHash, MediaType.MOVIE) { api.movieRecommendations(tmdbId, page).results }
    }

    suspend fun tvRecommendations(tmdbId: Int, page: Int = 1): List<TitleEntity> {
        val queryHash = queryHash("tv_recs_$tmdbId", emptyList(), region = "-", page)
        return cachedOrFetch(queryHash, MediaType.TV) { api.tvRecommendations(tmdbId, page).results }
    }

    private suspend fun cachedOrFetch(
        queryHash: String,
        mediaType: MediaType,
        fetch: suspend () -> List<MediaSummaryDto>,
    ): List<TitleEntity> {
        val now = clock.nowMillis()
        val cachedAt = discoverCacheDao.fetchedAtForQuery(queryHash)
        if (cachedAt != null && now - cachedAt < DISCOVER_TTL_MS) {
            val ids = discoverCacheDao.getForQuery(queryHash).sortedBy { it.ord }.map { it.tmdbId }
            val byId = titleDao.getByIds(ids, mediaType).associateBy { it.tmdbId }
            return ids.mapNotNull { byId[it] }
        }

        val results = fetch()

        // A thin discover-page row must never clobber a title that already has full detail
        // (runtime, certification, …) fetched via TitleRepository — but it's fine to refresh
        // a title that's only ever been seen as a stub itself.
        val fullyDetailedIds = titleDao.getByIds(results.map { it.id }, mediaType)
            .filter { it.runtimeMin != null || it.certification != null }
            .map { it.tmdbId }
            .toSet()
        val newStubs = results.filter { it.id !in fullyDetailedIds }.map { it.toStubTitleEntity(mediaType, now) }
        if (newStubs.isNotEmpty()) titleDao.upsertAll(newStubs)

        discoverCacheDao.replaceForQuery(
            queryHash,
            results.mapIndexed { index, item -> DiscoverCacheEntity(queryHash, item.id, mediaType, index, now) },
        )

        val byId = titleDao.getByIds(results.map { it.id }, mediaType).associateBy { it.tmdbId }
        return results.mapNotNull { byId[it.id] }
    }

    private fun List<Int>.toProviderParam(): String? = takeIf { it.isNotEmpty() }?.sorted()?.joinToString("|")

    private fun queryHash(endpoint: String, providerIds: List<Int>, region: String, page: Int): String =
        "$endpoint:${providerIds.sorted().joinToString(",")}:$region:$page"

    companion object {
        val DISCOVER_TTL_MS: Long = TimeUnit.HOURS.toMillis(24)

        const val NEW_FILMS_WINDOW_MONTHS = 4L
        const val NEW_SERIES_WINDOW_MONTHS = 3L

        /**
         * Live probe 2026-11-14 (GB, six big providers): unfloored, the film page's tail was
         * obscure (a 3-vote title ranked 6th); 10 drops those but keeps releases from the last
         * few days (17 votes) that 25 would hide. TV: 0 -> 209 hits, 10 -> 74, 25 -> 39, with
         * the leading titles identical, so 10 trims noise without losing fresh shows.
         */
        const val NEW_VOTE_FLOOR = 10

        /** The single rent/buy exception to browse gating; only [newMovies] may use it. */
        const val NEW_FILMS_MONETIZATION = "flatrate|free|rent|buy"
    }
}
