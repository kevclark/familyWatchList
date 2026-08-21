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
 * **PLAN.md §4 "Per-profile notification control" (M3e):** `refreshAll` now returns exactly the
 * profiles that genuinely finished this run ([ProfileRefreshResult]); [NotificationGate] narrows
 * that down further by the master toggle and each profile's own toggle before
 * [ShortlistNotifier.notifyShortlistReady] (which still owns the OS-permission gate, unchanged)
 * is even called — a profile that failed to refresh, or has its own toggle off, or the whole
 * account has notifications off, is excluded before the notifier ever runs.
 *
 * `Result.retry()` on failure (network blip, TMDB hiccup) rather than `Result.failure()` — a
 * missed weekly refresh is worth WorkManager's built-in backoff retrying, not giving up on until
 * next Monday. Note this only ever fires for something *outside* `refreshAll`'s own per-profile
 * try/catch (e.g. `invalidateAllCachedPages` itself failing) — a single profile's refresh failure
 * no longer aborts the whole job the way it used to (see `refreshAll`'s kdoc).
 */
class RecommendationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as FamilyWatchListApp).container
        return try {
            val region = container.userPreferencesRepository.region.first()
            val familyBlendSlider = FamilyBlendSlider(container.userPreferencesRepository.familyBlendSlider.first())
            val completed = container.recommendationRepository.refreshAll(region, familyBlendSlider)
            container.discoverRepository.invalidateAllCachedPages()

            val masterEnabled = container.userPreferencesRepository.notificationsEnabled.first()
            // Resolved up front (sequential suspend calls, not parallel — same "a family's
            // profile count is small enough for this to be fine" precedent as
            // WatchlistRepository.observeActiveItemsWithAvailability) so NotificationGate itself
            // stays a plain, non-suspend function callable from a JVM unit test with no DB at all.
            val perProfileEnabled = completed.associate {
                it.profileId to container.notificationPreferencesRepository.isEnabled(it.profileId)
            }
            val toNotify = NotificationGate.profilesToNotify(
                completed = completed,
                masterEnabled = masterEnabled,
                perProfileEnabled = { profileId -> perProfileEnabled[profileId] ?: true },
            )
            ShortlistNotifier.notifyShortlistReady(applicationContext, toNotify.map { it.name })
            Result.success()
        } catch (t: Throwable) {
            Result.retry()
        }
    }
}
