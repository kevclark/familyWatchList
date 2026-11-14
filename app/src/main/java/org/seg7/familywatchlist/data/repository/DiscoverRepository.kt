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
import org.seg7.familywatchlist.data.remote.dto.PagedResponseDto

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
     * PLAN.md §5i: Home's "New films" row, [page] 1 first and then one page (20) per "More" tap. TMDB has no
     * "date added to provider", so "new" is a recent theatrical/primary release (last
     * [NEW_FILMS_WINDOW_MONTHS] months, relative to the injected clock) that is on a subscribed provider in
     * [region], most popular first.
     *
     * This is Kev's ONE sign-off exception to the 2026-08-22 "no rent/buy in browsing" rule: the query asks for
     * flatrate|free|rent|buy. Every other discover call keeps flatrate|free (see `DiscoverRepositoryTest`, which
     * pins that). To badge rent/buy-only titles without a detail fetch per card, the same query with
     * flatrate|free only (the narrow query) is cached alongside; a title in the wide page but not in the narrow
     * pages is paid-only.
     *
     * Why that stays exact past page 1: the narrow result is the wide result minus the paid-only titles, in the
     * same popularity order. So a title at wide rank r (0-based) has narrow rank at most r. Wide page p covers
     * ranks below 20p, hence any of its titles that is included with a subscription sits within the first 20p
     * narrow ranks, i.e. narrow pages 1..p. This fetches (or reads from cache) narrow pages 1..p, stopping early
     * only when TMDB says the narrow query has no more pages. A wide title absent from all of those is
     * provably not in the narrow result at all, so paid-only. Pages 1..p-1 are cache hits from the earlier
     * taps, so each tap costs one wide request plus one narrow request.
     */
    suspend fun newMovies(subscribedProviderIds: List<Int>, region: String = REGION_GB, page: Int = 1): NewMovies {
        if (subscribedProviderIds.isEmpty()) return NewMovies(emptyList(), emptySet())
        val from = clock.today().minusMonths(NEW_FILMS_WINDOW_MONTHS).toString()
        val to = clock.today().toString()
        val providers = subscribedProviderIds.toProviderParam()
        val wide = cachedPage(queryHash(NEW_MOVIE_WIDE_KEY, subscribedProviderIds, region, page), MediaType.MOVIE) {
            api.discoverMovies(
                watchRegion = region,
                withWatchProviders = providers,
                monetizationTypes = NEW_FILMS_MONETIZATION,
                voteCountGte = NEW_VOTE_FLOOR,
                primaryReleaseDateGte = from,
                primaryReleaseDateLte = to,
                page = page,
            )
        }
        if (wide.titles.isEmpty()) return NewMovies(emptyList(), emptySet(), page, wide.totalPages)
        val includedIds = HashSet<Int>()
        for (narrowPage in 1..page) {
            val narrow = cachedPage(queryHash(NEW_MOVIE_NARROW_KEY, subscribedProviderIds, region, narrowPage), MediaType.MOVIE) {
                api.discoverMovies(
                    watchRegion = region,
                    withWatchProviders = providers,
                    voteCountGte = NEW_VOTE_FLOOR,
                    primaryReleaseDateGte = from,
                    primaryReleaseDateLte = to,
                    page = narrowPage,
                )
            }
            narrow.titles.forEach { includedIds += it.tmdbId }
            if (narrowPage >= narrow.totalPages) break
        }
        return NewMovies(
            wide.titles,
            paidOnlyIds = wide.titles.map { it.tmdbId }.filterNot { it in includedIds }.toSet(),
            page = page,
            totalPages = wide.totalPages,
        )
    }

    /** PLAN.md §5i: Home's "New series" row — first aired in the last [NEW_SERIES_WINDOW_MONTHS] months, flatrate|free only. */
    suspend fun newTv(subscribedProviderIds: List<Int>, region: String = REGION_GB, page: Int = 1): NewTv {
        if (subscribedProviderIds.isEmpty()) return NewTv(emptyList())
        val from = clock.today().minusMonths(NEW_SERIES_WINDOW_MONTHS).toString()
        val result = cachedPage(queryHash(NEW_TV_KEY, subscribedProviderIds, region, page), MediaType.TV) {
            api.discoverTv(
                watchRegion = region,
                withWatchProviders = subscribedProviderIds.toProviderParam(),
                voteCountGte = NEW_VOTE_FLOOR,
                firstAirDateGte = from,
                page = page,
            )
        }
        return NewTv(result.titles, page, result.totalPages)
    }

    /**
     * One page of New films. [paidOnlyIds] are TMDB ids in [titles] offered on a subscribed provider only to
     * rent/buy. [hasMore] is TMDB's own page count: false on the last page, which is when the More card hides.
     */
    data class NewMovies(
        val titles: List<TitleEntity>,
        val paidOnlyIds: Set<Int>,
        val page: Int = 1,
        val totalPages: Int = 1,
    ) {
        val hasMore: Boolean get() = page < totalPages
    }

    /** One page of New series; see [NewMovies]. */
    data class NewTv(val titles: List<TitleEntity>, val page: Int = 1, val totalPages: Int = 1) {
        val hasMore: Boolean get() = page < totalPages
    }

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

    private class CachedPage(val titles: List<TitleEntity>, val totalPages: Int)

    /**
     * [cachedOrFetch] for a paged query that must also remember TMDB's `total_pages`, so the More card knows when
     * to hide without probing a page past the end. There is no column for it and no schema change: the count
     * lives in a one-row `discover_cache` entry under `<queryHash>#pages` (its `tmdbId` holds the count), written
     * with the page and so sharing its 24 h TTL and [invalidateAllCachedPages]. A page cached before the count was
     * recorded (an alpha.20 install) is dropped and refetched once.
     */
    private suspend fun cachedPage(
        queryHash: String,
        mediaType: MediaType,
        fetch: suspend () -> PagedResponseDto<MediaSummaryDto>,
    ): CachedPage {
        val metaHash = "$queryHash#pages"
        if (discoverCacheDao.fetchedAtForQuery(metaHash) == null) discoverCacheDao.deleteForQuery(queryHash)
        var fetchedTotal: Int? = null
        val titles = cachedOrFetch(queryHash, mediaType) {
            val response = fetch()
            fetchedTotal = response.totalPages
            response.results
        }
        val fetched = fetchedTotal
        if (fetched != null) {
            discoverCacheDao.replaceForQuery(metaHash, listOf(DiscoverCacheEntity(metaHash, fetched, mediaType, 0, clock.nowMillis())))
            return CachedPage(titles, fetched)
        }
        return CachedPage(titles, discoverCacheDao.getForQuery(metaHash).firstOrNull()?.tmdbId ?: 1)
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

        /** PLAN.md §5i (alpha.21): both New rows look back two months, most popular first. */
        const val NEW_FILMS_WINDOW_MONTHS = 2L
        const val NEW_SERIES_WINDOW_MONTHS = 2L

        /**
         * Cache keys carry the window so a changed window never serves pages from the old one (alpha.20 cached
         * 4- and 3-month pages that would otherwise be served for up to 24 h and then mixed with 2-month page 2s).
         */
        private const val NEW_MOVIE_WIDE_KEY = "discover_new_movie_${NEW_FILMS_WINDOW_MONTHS}m"
        private const val NEW_MOVIE_NARROW_KEY = "discover_new_movie_included_${NEW_FILMS_WINDOW_MONTHS}m"
        private const val NEW_TV_KEY = "discover_new_tv_${NEW_SERIES_WINDOW_MONTHS}m"

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
