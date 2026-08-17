package org.seg7.familywatchlist.data.repository

import org.seg7.familywatchlist.common.AppClock
import org.seg7.familywatchlist.data.local.dao.TitleDao
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.TitleEntity
import org.seg7.familywatchlist.data.remote.TmdbApi
import org.seg7.familywatchlist.data.remote.dto.MediaSummaryDto

/**
 * PLAN.md §3/§5 screen 5: `/search/multi`, "filter results to movie/tv client-side".
 *
 * Search results are *not* cached by query the way discover pages are (PLAN.md §3's 24h TTL is
 * specified for "discover/candidate pages"): a search is an explicit, one-off user intent where
 * stale results would be a bug, not a saving. What search *does* persist is the titles it finds
 * — every result is upserted into [TitleEntity] as a stub, so tapping through to details,
 * adding to the watchlist, or logging a watch all work offline afterwards, and the poster is
 * already in Coil's cache.
 */
class SearchRepository(
    private val titleDao: TitleDao,
    private val api: TmdbApi,
    private val clock: AppClock,
) {
    /**
     * Returns movie/TV results in TMDB's relevance order. `person` results (and anything else
     * TMDB adds to multi-search later) are dropped — PLAN.md §5 screen 5 is a title finder.
     */
    suspend fun search(query: String, page: Int = 1): List<TitleEntity> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        val results = api.searchMulti(trimmed, page).results
            .mapNotNull { dto -> dto.mediaTypeOrNull()?.let { dto to it } }
            // A result with no title is unrenderable; TMDB occasionally returns these.
            .filter { (dto, _) -> !(dto.title ?: dto.name).isNullOrBlank() }

        val now = clock.nowMillis()
        persistStubs(results, now)

        // Re-read through Room so callers get whatever detail is already cached (runtime,
        // certification, trailer key) rather than the thinner search payload — offline-first,
        // per PLAN.md §3: "Room is the source of truth for the UI".
        return results.reReadThroughRoom()
    }

    /**
     * A search hit must never overwrite a title that already has full detail cached, for the
     * same reason [DiscoverRepository] guards against it: the summary payload has no runtime,
     * certification or trailer key, so a blind upsert would blank fields the details screen
     * needs and force a refetch.
     */
    private suspend fun persistStubs(results: List<Pair<MediaSummaryDto, MediaType>>, now: Long) {
        MediaType.entries.forEach { mediaType ->
            val forType = results.filter { it.second == mediaType }.map { it.first }
            if (forType.isEmpty()) return@forEach
            val detailedIds = titleDao.getByIds(forType.map { it.id }, mediaType)
                .filter { it.runtimeMin != null || it.certification != null }
                .map { it.tmdbId }
                .toSet()
            val stubs = forType.filter { it.id !in detailedIds }.map { it.toStubTitleEntity(mediaType, now) }
            if (stubs.isNotEmpty()) titleDao.upsertAll(stubs)
        }
    }

    private suspend fun List<Pair<MediaSummaryDto, MediaType>>.reReadThroughRoom(): List<TitleEntity> {
        val byType = MediaType.entries.associateWith { mediaType ->
            val ids = filter { it.second == mediaType }.map { it.first.id }
            if (ids.isEmpty()) emptyMap() else titleDao.getByIds(ids, mediaType).associateBy { it.tmdbId }
        }
        return mapNotNull { (dto, mediaType) -> byType[mediaType]?.get(dto.id) }
    }

    private fun MediaSummaryDto.mediaTypeOrNull(): MediaType? = when (mediaType) {
        "movie" -> MediaType.MOVIE
        "tv" -> MediaType.TV
        else -> null
    }
}
