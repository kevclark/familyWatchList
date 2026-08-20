package org.seg7.familywatchlist.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import org.seg7.familywatchlist.FamilyWatchListApp

/**
 * PLAN.md §4: "WorkManager weekly (Monday 06:00, unmetered-preferred) regenerates shortlists +
 * refreshes provider TTLs, then posts a local notification." Regenerates every profile's
 * shortlist plus the default "everyone" family scope ([org.seg7.familywatchlist.data.repository
 * .RecommendationRepository.refreshAll]), invalidates the cached `/discover` pages so the next
 * Home load re-fetches current provider availability rather than serving up to another 24h of a
 * now-week-old "popular on your services" page, then notifies.
 *
 * `Result.retry()` on failure (network blip, TMDB hiccup) rather than `Result.failure()` — a
 * missed weekly refresh is worth WorkManager's built-in backoff retrying, not giving up on until
 * next Monday.
 */
class RecommendationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as FamilyWatchListApp).container
        return try {
            val region = container.userPreferencesRepository.region.first()
            container.recommendationRepository.refreshAll(region)
            container.discoverRepository.invalidateAllCachedPages()
            ShortlistNotifier.notifyShortlistReady(applicationContext)
            Result.success()
        } catch (t: Throwable) {
            Result.retry()
        }
    }
}
