package org.seg7.familywatchlist.data.repository

import java.util.concurrent.TimeUnit
import org.seg7.familywatchlist.common.AppClock
import org.seg7.familywatchlist.data.local.dao.DiscoverCacheDao
import org.seg7.familywatchlist.data.local.dao.TitleDao
import org.seg7.familywatchlist.data.local.entity.DiscoverCacheEntity
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.TitleEntity
import org.seg7.familywatchlist.data.remote.TmdbApi
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
    suspend fun discoverMovies(subscribedProviderIds: List<Int>, page: Int = 1): List<TitleEntity> {
        if (subscribedProviderIds.isEmpty()) return emptyList()
        val queryHash = queryHash("discover_movie", subscribedProviderIds, page)
        return cachedOrFetch(queryHash, MediaType.MOVIE) {
            api.discoverMovies(withWatchProviders = subscribedProviderIds.toProviderParam(), page = page).results
        }
    }

    suspend fun discoverTv(subscribedProviderIds: List<Int>, page: Int = 1): List<TitleEntity> {
        if (subscribedProviderIds.isEmpty()) return emptyList()
        val queryHash = queryHash("discover_tv", subscribedProviderIds, page)
        return cachedOrFetch(queryHash, MediaType.TV) {
            api.discoverTv(withWatchProviders = subscribedProviderIds.toProviderParam(), page = page).results
        }
    }

    /**
     * Subscribed-provider changes must invalidate previously-cached discover pages: otherwise
     * unsubscribing from a service (or subscribing to a new one) can keep serving a stale
     * "popular on your services" page — reflecting the *old* provider set — for up to
     * [DISCOVER_TTL_MS]. Called from [org.seg7.familywatchlist.data.repository.ProviderRepository.setSubscribed].
     */
    suspend fun invalidateAllCachedPages() = discoverCacheDao.deleteAll()

    suspend fun movieRecommendations(tmdbId: Int, page: Int = 1): List<TitleEntity> {
        val queryHash = queryHash("movie_recs_$tmdbId", emptyList(), page)
        return cachedOrFetch(queryHash, MediaType.MOVIE) { api.movieRecommendations(tmdbId, page).results }
    }

    suspend fun tvRecommendations(tmdbId: Int, page: Int = 1): List<TitleEntity> {
        val queryHash = queryHash("tv_recs_$tmdbId", emptyList(), page)
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

    private fun queryHash(endpoint: String, providerIds: List<Int>, page: Int): String =
        "$endpoint:${providerIds.sorted().joinToString(",")}:$page"

    companion object {
        val DISCOVER_TTL_MS: Long = TimeUnit.HOURS.toMillis(24)
    }
}
