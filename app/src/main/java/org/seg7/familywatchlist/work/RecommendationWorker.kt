package org.seg7.familywatchlist.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import org.seg7.familywatchlist.FamilyWatchListApp
import org.seg7.familywatchlist.data.recommend.FamilyBlendSlider

/**
 * PLAN.md §4: "WorkManager weekly (Monday 06:00, unmetered-preferred) regenerates shortlists +
 * refreshes provider TTLs, then posts a local notification." Regenerates every individual
 * profile's shortlist, plus the Family profile's own — if one exists — real curated-membership
 * shortlist ([org.seg7.familywatchlist.data.repository.RecommendationRepository.refreshAll],
 * resolved by Kev 2026-08-21 — see that method's kdoc for the M3d design decision), invalidates
 * the cached `/discover` pages so the next Home load re-fetches current provider availability
 * rather than serving up to another 24h of a now-week-old "popular on your services" page, then
 * notifies.
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
            val familyBlendSlider = FamilyBlendSlider(container.userPreferencesRepository.familyBlendSlider.first())
            container.recommendationRepository.refreshAll(region, familyBlendSlider)
            container.discoverRepository.invalidateAllCachedPages()
            ShortlistNotifier.notifyShortlistReady(applicationContext)
            Result.success()
        } catch (t: Throwable) {
            Result.retry()
        }
    }
}
