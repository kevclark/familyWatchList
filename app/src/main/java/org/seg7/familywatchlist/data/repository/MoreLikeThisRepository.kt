package org.seg7.familywatchlist.data.repository

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import org.seg7.familywatchlist.common.AppClock
import org.seg7.familywatchlist.data.local.dao.TitleDao
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.TitleEntity
import org.seg7.familywatchlist.data.remote.TmdbApi
import org.seg7.familywatchlist.data.remote.TmdbApi.Companion.REGION_GB
import org.seg7.familywatchlist.data.remote.dto.MediaSummaryDto
import java.util.concurrent.ConcurrentHashMap

/** Cumulative result of one "More like this" load. [settled] is true once every check has finished. */
data class MoreLikeThisResult(val titles: List<TitleEntity>, val settled: Boolean)

/**
 * PLAN.md §5h: the detail screen's "More like this" row. Candidates are page 1 of TMDB's
 * `movie|tv/{id}/recommendations` (never `/similar`), run through the same [passesBrowseGate] checks as
 * structured search browsing. Recommendation ids are cached in memory per title for the process, so
 * revisiting a detail screen does not refetch the list (the per-title checks are Room-cached anyway).
 * No schema change. A failed fetch throws from the flow; the caller hides the row.
 */
class MoreLikeThisRepository(
    private val titleDao: TitleDao,
    private val api: TmdbApi,
    private val clock: AppClock,
    private val availabilityGate: AvailabilityGate,
) {
    private val idCache = ConcurrentHashMap<String, List<MediaSummaryDto>>()

    /**
     * Emits cumulative lists in TMDB's order as checks pass, then a final settled emission.
     * [cap] is the BBFC cap string or null for Any.
     */
    fun load(tmdbId: Int, mediaType: MediaType, cap: String?, region: String = REGION_GB): Flow<MoreLikeThisResult> = channelFlow {
        val dtos = recommendations(tmdbId, mediaType)
            .filter { it.id != tmdbId && !(it.title ?: it.name).isNullOrBlank() }
            .distinctBy { it.id }
        titleDao.persistStubs(dtos.map { it to mediaType }, clock.nowMillis())
        val stored = if (dtos.isEmpty()) emptyMap() else titleDao.getByIds(dtos.map { it.id }, mediaType).associateBy { it.tmdbId }

        val slots = arrayOfNulls<TitleEntity>(dtos.size)
        val sendMutex = Mutex()
        val limit = Semaphore(MAX_CONCURRENT_CHECKS)
        coroutineScope {
            dtos.mapIndexed { index, dto ->
                launch {
                    val candidate = stored[dto.id] ?: return@launch
                    limit.withPermit {
                        // A failed check for one title (network) just drops that title.
                        val verdict = try {
                            passesBrowseGate(availabilityGate, titleDao, dto.id, mediaType, region, cap, candidate.certification)
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            null
                        }
                        if (verdict == null) return@withPermit
                        sendMutex.withLock {
                            slots[index] = titleDao.get(dto.id, mediaType) ?: candidate
                            send(MoreLikeThisResult(slots.filterNotNull(), settled = false))
                        }
                    }
                }
            }.joinAll()
        }
        sendMutex.withLock { send(MoreLikeThisResult(slots.filterNotNull(), settled = true)) }
    }

    private suspend fun recommendations(tmdbId: Int, mediaType: MediaType): List<MediaSummaryDto> {
        val key = "$mediaType-$tmdbId"
        idCache[key]?.let { return it }
        val response = when (mediaType) {
            MediaType.MOVIE -> api.movieRecommendations(tmdbId, page = 1)
            MediaType.TV -> api.tvRecommendations(tmdbId, page = 1)
        }
        return response.results.also { idCache[key] = it }
    }

    private companion object {
        const val MAX_CONCURRENT_CHECKS = 5
    }
}
