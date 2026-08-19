package org.seg7.familywatchlist.data.repository

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.seg7.familywatchlist.data.remote.TmdbApi
import org.seg7.familywatchlist.data.remote.dto.WatchProviderRegionDto

/** One region TMDB's watch-provider data supports, e.g. `RegionOption("GB", "United Kingdom", "United Kingdom")`. */
data class RegionOption(
    val code: String,
    val englishName: String,
    val nativeName: String,
)

/**
 * PLAN.md §7 M2f: Settings' region picker sources its list from TMDB's own
 * `/watch/providers/regions` rather than a hand-maintained country list, so it's always exactly
 * what TMDB actually supports. This data changes essentially never (new regions are a rare TMDB
 * catalog event, not a daily occurrence), so unlike [DiscoverRepository]'s 24h TTL — which
 * doesn't fit here at all — this just fetches once and caches the result in memory for the rest
 * of the process's life. A cold app restart re-fetches, which is fine: cheap, and self-healing if
 * TMDB's list ever changes. [Mutex] guards against two concurrent callers (e.g. opening the
 * picker twice quickly) triggering two redundant network calls.
 */
class RegionCatalogRepository(private val api: TmdbApi) {
    @Volatile
    private var cached: List<RegionOption>? = null
    private val mutex = Mutex()

    suspend fun getRegions(): List<RegionOption> {
        cached?.let { return it }
        return mutex.withLock {
            cached ?: api.watchProviderRegions().results
                .map { it.toRegionOption() }
                .sortedBy { it.englishName }
                .also { cached = it }
        }
    }

    private fun WatchProviderRegionDto.toRegionOption() = RegionOption(
        code = isoCode,
        englishName = englishName,
        nativeName = nativeName,
    )
}
