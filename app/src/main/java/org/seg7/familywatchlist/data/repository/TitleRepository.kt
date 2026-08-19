package org.seg7.familywatchlist.data.repository

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import org.seg7.familywatchlist.common.AppClock
import org.seg7.familywatchlist.data.local.dao.AvailabilityBadge
import org.seg7.familywatchlist.data.local.dao.ProviderAvailabilityDao
import org.seg7.familywatchlist.data.local.dao.TitleAttributeDao
import org.seg7.familywatchlist.data.local.dao.TitleDao
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.TitleAttributeEntity
import org.seg7.familywatchlist.data.local.entity.TitleEntity
import org.seg7.familywatchlist.data.remote.TmdbApi
import org.seg7.familywatchlist.data.remote.TmdbApi.Companion.REGION_GB

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

    /**
     * GB availability with real provider names (PLAN.md §5 screen 4). Every UI that renders
     * these must also render the "Streaming data by JustWatch" credit — see
     * [org.seg7.familywatchlist.ui.components.AvailabilityRow], which pairs them so the credit
     * can't be forgotten.
     */
    fun observeAvailability(tmdbId: Int, mediaType: MediaType): Flow<List<AvailabilityBadge>> =
        providerAvailabilityDao.observeBadges(tmdbId, mediaType)

    /** Raw GB provider ids with availability for a title — used by [AvailabilityGate], which pairs it with [ProviderRepository]'s subscribed set. */
    suspend fun getAvailabilityProviderIds(tmdbId: Int, mediaType: MediaType): Set<Int> =
        providerAvailabilityDao.getForTitle(tmdbId, mediaType).map { it.providerId }.toSet()

    fun isMetadataStale(title: TitleEntity): Boolean =
        clock.nowMillis() - title.fetchedAt >= METADATA_TTL_MS

    /**
     * True once the 7-day TTL has elapsed, **or** [title] is only ever been a stub. A search or
     * discover hit persists a stub with its own fresh `fetchedAt` (so the row exists offline
     * before its details screen loads) — without the stub check, a title reached moments after
     * being stubbed would look "fresh" by timestamp alone and [ensureFresh] would return it
     * as-is, forever, never actually fetching the one call that fills provider availability.
     * PLAN.md §5a's gate hits this exact path: it calls [ensureFresh] on a result [SearchRepository]
     * just stubbed a moment earlier, so this distinction is what makes the gate see real
     * availability instead of "nothing cached yet" for every uncached search result.
     */
    fun isProviderDataStale(title: TitleEntity): Boolean =
        title.isStubOnly || clock.nowMillis() - title.fetchedAt >= PROVIDER_TTL_MS

    /** Same "has this row ever been through a full detail fetch" heuristic [SearchRepository]/[DiscoverRepository] use to decide whether a hit is safe to overwrite. */
    private val TitleEntity.isStubOnly: Boolean
        get() = runtimeMin == null && certification == null

    /**
     * Offline-first entry point: returns the cached row unless it's missing or provider-stale,
     * in which case it refreshes first. [region] (PLAN.md §7 M2f) picks which country's key
     * [refresh] pulls out of the multi-country `watch/providers` payload — defaults to
     * [REGION_GB] so pre-existing callers are unaffected; real callers thread the live
     * `UserPreferencesRepository.region` value through.
     */
    suspend fun ensureFresh(tmdbId: Int, mediaType: MediaType, region: String = REGION_GB): TitleEntity {
        val cached = titleDao.get(tmdbId, mediaType)
        if (cached != null && !isProviderDataStale(cached)) return cached
        return refresh(tmdbId, mediaType, region)
    }

    suspend fun refresh(tmdbId: Int, mediaType: MediaType, region: String = REGION_GB): TitleEntity {
        val now = clock.nowMillis()
        return when (mediaType) {
            MediaType.MOVIE -> {
                val dto = api.movieDetail(tmdbId)
                val entity = dto.toTitleEntity(now)
                titleDao.upsert(entity)
                titleAttributeDao.replaceForTitle(tmdbId, MediaType.MOVIE, dto.toAttributes())
                providerAvailabilityDao.replaceForTitle(tmdbId, MediaType.MOVIE, dto.toAvailability(now, region))
                entity
            }
            MediaType.TV -> {
                val dto = api.tvDetail(tmdbId)
                val entity = dto.toTitleEntity(now)
                titleDao.upsert(entity)
                titleAttributeDao.replaceForTitle(tmdbId, MediaType.TV, dto.toAttributes())
                providerAvailabilityDao.replaceForTitle(tmdbId, MediaType.TV, dto.toAvailability(now, region))
                entity
            }
        }
    }

    /**
     * PLAN.md §7 M2f: the region preference changed. `provider_availability` rows carry no
     * region column (PLAN.md §2 modelled them as GB-only), and this class's shared
     * [TitleEntity.fetchedAt] would otherwise keep serving up to 7 more days of the *old*
     * region's providers, silently mislabeled as the new region's — a wrong-data bug, worse than
     * the stale-but-honest data this fixes. Forcing every title stale makes the next
     * [ensureFresh] per title do a real refetch (now using the current region). Same
     * "invalidate on preference change" precedent as [DiscoverRepository.invalidateAllCachedPages]
     * / [ProviderRepository.setSubscribed] (PLAN.md M2e) — called from Settings' region picker.
     */
    suspend fun invalidateAllProviderData() = titleDao.expireAllFetchedAt()

    companion object {
        val METADATA_TTL_MS: Long = TimeUnit.DAYS.toMillis(30)
        val PROVIDER_TTL_MS: Long = TimeUnit.DAYS.toMillis(7)
    }
}
