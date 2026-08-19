package org.seg7.familywatchlist.data.repository

import org.seg7.familywatchlist.data.local.entity.MediaType

/**
 * PLAN.md §5a "Search & watchlist availability gating": the one place that answers "can this
 * title actually be watched on a service we pay for, right now, in GB" — shared by [SearchRepository]
 * (drops results that fail this check) and [WatchlistRepository] (blocks adding a title that
 * fails it). Neither call site duplicates the resolution logic; both go through here so the
 * definition of "available" can't drift between search and the watchlist.
 *
 * Resolution reuses [TitleRepository.ensureFresh] rather than issuing its own network calls: a
 * title already detail-fetched from any source (search, discover, a previous details-screen
 * view) has its GB provider rows sitting in Room within their 7-day TTL and this is a cache hit;
 * only a title with no cached rows or a stale cache reaches the network, through the existing
 * 4 req/s `ThrottleInterceptor` — no second throttle/cache mechanism is introduced here.
 */
class AvailabilityGate(
    private val titleRepository: TitleRepository,
    private val providerRepository: ProviderRepository,
) {
    /**
     * True when [tmdbId]/[mediaType] currently has GB availability on at least one provider the
     * family is subscribed to. With nothing subscribed, nothing can ever pass — there is no
     * provider to be "available on".
     */
    suspend fun isAvailableOnSubscribedProvider(tmdbId: Int, mediaType: MediaType): Boolean {
        val subscribedIds = providerRepository.getSubscribedIds()
        if (subscribedIds.isEmpty()) return false
        titleRepository.ensureFresh(tmdbId, mediaType)
        val availableProviderIds = titleRepository.getAvailabilityProviderIds(tmdbId, mediaType)
        return availableProviderIds.any { it in subscribedIds }
    }
}
