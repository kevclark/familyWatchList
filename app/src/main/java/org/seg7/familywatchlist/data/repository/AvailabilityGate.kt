package org.seg7.familywatchlist.data.repository

import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.ProviderAvailabilityEntity
import org.seg7.familywatchlist.data.local.entity.ProviderKind
import org.seg7.familywatchlist.data.remote.TmdbApi.Companion.REGION_GB

/**
 * PLAN.md §5a "Search & watchlist availability gating", widened by §5's M6 "Paid (rent/buy)
 * titles" addendum: the one place that answers "can this title actually be watched on a service
 * we pay for, right now, in GB" — shared by [SearchRepository] (drops results that fail this
 * check) and [WatchlistRepository] (blocks adding a title that fails it). Neither call site
 * duplicates the resolution logic; both go through here so the definition of "available" can't
 * drift between search and the watchlist.
 *
 * Resolution reuses [TitleRepository.ensureFresh] rather than issuing its own network calls: a
 * title already detail-fetched from any source (search, discover, a previous details-screen
 * view) has its GB provider rows sitting in Room within their 7-day TTL and this is a cache hit;
 * only a title with no cached rows or a stale cache reaches the network, through the existing
 * 4 req/s `ThrottleInterceptor` — no second throttle/cache mechanism is introduced here.
 *
 * **M6 (Kev, 2026-08-22):** [isAvailableOnSubscribedProvider] itself needed no code change to
 * widen — it was always kind-agnostic ("any subscribed provider has *a* row for this title"), so
 * once [TmdbMappers.toAvailability] started persisting BUY/RENT rows alongside FLATRATE/FREE
 * (same commit), this check started counting them automatically. [isPaidOnlyOnSubscribedProvider]
 * is new: purely a badge-wording query (never a gate), answering "is every subscribed row for
 * this title BUY/RENT, with no FLATRATE/FREE option at all" — used by Search/details/My List UI
 * to show "Rent/Buy" instead of implying it's included with a subscription.
 *
 * **This class is never called by [RecommendationRepository.gatherCandidatePool]** — the
 * recommender's whole candidate pool comes from [DiscoverRepository]'s `/discover` (filtered
 * TMDB-side by the untouched `with_watch_monetization_types = "flatrate|free"`) and
 * `/recommendations`, neither of which reads `provider_availability` at all. So widening this
 * gate's *own* resolution set (by persisting BUY/RENT rows) has no path back into Home's
 * Popular/For You rows or the recommender's scoring — see
 * [RecommendationRepositoryTest]'s M6 regression test, which proves it rather than just asserting it.
 */
class AvailabilityGate(
    private val titleRepository: TitleRepository,
    private val providerRepository: ProviderRepository,
) {
    /**
     * True when [tmdbId]/[mediaType] currently has [region] availability on at least one
     * provider the family is subscribed to — FLATRATE, FREE, BUY, or RENT all count equally here
     * (PLAN.md §5's M6 addendum: "available if a subscribed provider has it under FLATRATE, FREE,
     * BUY, or RENT"). With nothing subscribed, nothing can ever pass — there is no provider to be
     * "available on". [region] (PLAN.md §7 M2f) defaults to [REGION_GB]; real callers thread the
     * live `UserPreferencesRepository.region` value through.
     */
    suspend fun isAvailableOnSubscribedProvider(tmdbId: Int, mediaType: MediaType, region: String = REGION_GB): Boolean =
        subscribedAvailability(tmdbId, mediaType, region).isNotEmpty()

    /**
     * True only when [tmdbId]/[mediaType] passes [isAvailableOnSubscribedProvider] but has *no*
     * FLATRATE/FREE row among the subscribed providers — i.e. the only way to watch it on a
     * service the family pays for is to separately rent or buy it. Badge-wording query only,
     * never used for gating (a paid-only title still passes the add/search gate above). False for
     * anything unavailable at all, and false the moment at least one subscribed provider offers
     * it included with the subscription (FLATRATE/FREE), even if others only offer BUY/RENT.
     */
    suspend fun isPaidOnlyOnSubscribedProvider(tmdbId: Int, mediaType: MediaType, region: String = REGION_GB): Boolean {
        val rows = subscribedAvailability(tmdbId, mediaType, region)
        if (rows.isEmpty()) return false
        return rows.none { it.kind == ProviderKind.FLATRATE || it.kind == ProviderKind.FREE }
    }

    private suspend fun subscribedAvailability(tmdbId: Int, mediaType: MediaType, region: String): List<ProviderAvailabilityEntity> {
        val subscribedIds = providerRepository.getSubscribedIds()
        if (subscribedIds.isEmpty()) return emptyList()
        titleRepository.ensureFresh(tmdbId, mediaType, region)
        return titleRepository.getAvailability(tmdbId, mediaType).filter { it.providerId in subscribedIds }
    }
}
