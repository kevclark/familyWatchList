package org.seg7.familywatchlist.data.repository

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import org.seg7.familywatchlist.common.AppClock
import org.seg7.familywatchlist.data.local.dao.ProviderAvailabilityDao
import org.seg7.familywatchlist.data.local.dao.TitleAttributeDao
import org.seg7.familywatchlist.data.local.dao.TitleDao
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.TitleAttributeEntity
import org.seg7.familywatchlist.data.local.entity.TitleEntity
import org.seg7.familywatchlist.data.remote.TmdbApi

/**
 * Offline-first title detail cache (PLAN.md §3). One TMDB detail call (with
 * append_to_response) fills [TitleEntity], its [TitleAttributeEntity] rows, and its GB
 * provider availability together, so a single [TitleEntity.fetchedAt] timestamp governs both
 * TTLs described in the plan: metadata is good for 30 days, but because provider availability
 * (7 days) can only be refreshed by redoing that same call, [isStale] uses the *stricter*
 * 7-day bound to decide whether a refresh is due — refreshing early for providers' sake also
 * happens to keep metadata fresher than its own 30-day allowance, which is harmless.
 */
class TitleRepository(
    private val titleDao: TitleDao,
    private val titleAttributeDao: TitleAttributeDao,
    private val providerAvailabilityDao: ProviderAvailabilityDao,
    private val api: TmdbApi,
    private val clock: AppClock,
) {
    fun observeTitle(tmdbId: Int, mediaType: MediaType): Flow<TitleEntity?> =
        titleDao.observe(tmdbId, mediaType)

    fun observeAttributes(tmdbId: Int, mediaType: MediaType): Flow<List<TitleAttributeEntity>> =
        titleAttributeDao.observeForTitle(tmdbId, mediaType)

    fun isMetadataStale(title: TitleEntity): Boolean =
        clock.nowMillis() - title.fetchedAt >= METADATA_TTL_MS

    fun isProviderDataStale(title: TitleEntity): Boolean =
        clock.nowMillis() - title.fetchedAt >= PROVIDER_TTL_MS

    /** Offline-first entry point: returns the cached row unless it's missing or provider-stale, in which case it refreshes first. */
    suspend fun ensureFresh(tmdbId: Int, mediaType: MediaType): TitleEntity {
        val cached = titleDao.get(tmdbId, mediaType)
        if (cached != null && !isProviderDataStale(cached)) return cached
        return refresh(tmdbId, mediaType)
    }

    suspend fun refresh(tmdbId: Int, mediaType: MediaType): TitleEntity {
        val now = clock.nowMillis()
        return when (mediaType) {
            MediaType.MOVIE -> {
                val dto = api.movieDetail(tmdbId)
                val entity = dto.toTitleEntity(now)
                titleDao.upsert(entity)
                titleAttributeDao.replaceForTitle(tmdbId, MediaType.MOVIE, dto.toAttributes())
                providerAvailabilityDao.replaceForTitle(tmdbId, MediaType.MOVIE, dto.toAvailability(now))
                entity
            }
            MediaType.TV -> {
                val dto = api.tvDetail(tmdbId)
                val entity = dto.toTitleEntity(now)
                titleDao.upsert(entity)
                titleAttributeDao.replaceForTitle(tmdbId, MediaType.TV, dto.toAttributes())
                providerAvailabilityDao.replaceForTitle(tmdbId, MediaType.TV, dto.toAvailability(now))
                entity
            }
        }
    }

    companion object {
        val METADATA_TTL_MS: Long = TimeUnit.DAYS.toMillis(30)
        val PROVIDER_TTL_MS: Long = TimeUnit.DAYS.toMillis(7)
    }
}
