package org.seg7.familywatchlist.data.repository

import java.util.concurrent.TimeUnit
import org.seg7.familywatchlist.common.AppClock
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
    }
}
