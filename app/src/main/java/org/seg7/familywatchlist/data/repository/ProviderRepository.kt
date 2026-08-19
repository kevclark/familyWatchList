package org.seg7.familywatchlist.data.repository

import kotlinx.coroutines.flow.Flow
import org.seg7.familywatchlist.data.local.dao.ProviderDao
import org.seg7.familywatchlist.data.local.entity.ProviderEntity
import org.seg7.familywatchlist.data.remote.TmdbApi
import org.seg7.familywatchlist.data.remote.dto.WatchProviderDto

/** PLAN.md §2/§3: Provider — seeded once from TMDB's GB provider list; subscribed is toggled in Settings. */
class ProviderRepository(
    private val providerDao: ProviderDao,
    private val api: TmdbApi,
    private val discoverRepository: DiscoverRepository,
) {
    fun observeAll(): Flow<List<ProviderEntity>> = providerDao.observeAll()

    fun observeSubscribed(): Flow<List<ProviderEntity>> = providerDao.observeSubscribed()

    suspend fun getSubscribedIds(): List<Int> = providerDao.getSubscribed().map { it.providerId }

    /** Onboarding calls this once; a no-op if the catalog is already seeded. */
    suspend fun seedIfEmpty() {
        if (providerDao.count() > 0) return
        val movieProviders = api.movieProviders().results
        val tvProviders = api.tvProviders().results
        val merged = (movieProviders + tvProviders).distinctBy { it.providerId }
        providerDao.upsertAll(merged.map { it.toEntity() })
    }

    /**
     * Changing which services are subscribed changes what "popular on your services" should
     * mean — a previously-cached discover page reflects the *old* provider set and would
     * otherwise keep being served for up to [DiscoverRepository.DISCOVER_TTL_MS] (24h) after a
     * toggle here (PLAN.md §7 M2e). Invalidate every cached discover/recommendations page on any
     * subscription change so Home's next refresh re-fetches against the current provider set.
     */
    suspend fun setSubscribed(providerId: Int, subscribed: Boolean) {
        providerDao.setSubscribed(providerId, subscribed)
        discoverRepository.invalidateAllCachedPages()
    }

    /**
     * PLAN.md §2: "Default-on at onboarding: Netflix, Disney+, Amazon Prime Video, BBC iPlayer,
     * Channel 4, ITVX — user confirms/edits." Matched by name (case-insensitive, with a couple
     * of known TMDB naming variants) rather than hard-coded provider IDs, since those aren't
     * stable across regions/catalog changes. Only acts if nothing is subscribed yet, so it's
     * safe to call every time onboarding is (re-)entered — e.g. from Settings later — without
     * clobbering a user's own edits.
     */
    suspend fun applyOnboardingDefaults() {
        if (providerDao.getSubscribed().isNotEmpty()) return
        val matches = providerDao.getAll().filter { provider ->
            DEFAULT_SUBSCRIBED_ALIASES.any { aliases -> provider.name.lowercase() in aliases }
        }
        matches.forEach { providerDao.setSubscribed(it.providerId, true) }
    }

    private fun WatchProviderDto.toEntity(): ProviderEntity = ProviderEntity(
        providerId = providerId,
        name = providerName,
        logoPath = logoPath,
        subscribed = false,
        displayPriority = displayPriority,
    )

    companion object {
        private val DEFAULT_SUBSCRIBED_ALIASES: List<Set<String>> = listOf(
            setOf("netflix"),
            setOf("disney plus", "disney+"),
            setOf("amazon prime video", "prime video"),
            setOf("bbc iplayer"),
            setOf("channel 4", "all 4"),
            setOf("itvx", "itv hub", "itv hub+"),
        )
    }
}
