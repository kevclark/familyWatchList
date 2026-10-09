package org.seg7.familywatchlist.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import org.seg7.familywatchlist.FamilyWatchListApp
import org.seg7.familywatchlist.data.local.entity.RefreshOutcome
import org.seg7.familywatchlist.data.local.entity.RefreshTrigger

/**
 * PLAN.md §4 / §5d: the weekly scheduled refresh. All the actual work -- regenerating every
 * profile's shortlist, invalidating cached discover pages, the notification gate and the refresh-log
 * entry -- lives in [RefreshCoordinator.run], shared with catch-up and manual refresh behind one
 * guard. This class only decides retry versus done, and re-anchors the schedule.
 *
 * **M15 re-anchoring:** the schedule is a chain of one-time requests, not a periodic one (see
 * [RecommendationScheduler]). When this run is finished for good -- it succeeded, or it failed on
 * its last attempt -- it books the next slot from the current wall-clock time as its last act, so a
 * late run can never drag later weeks back. A failure on an earlier attempt returns
 * [Result.retry] (WorkManager backoff) *without* booking the next slot, since this request is
 * still the pending one; each attempt gets its own refresh-log row, which is useful diagnostics.
 */
class RecommendationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as FamilyWatchListApp).container
        val outcome = runCatching { container.refreshCoordinator.run(RefreshTrigger.SCHEDULED) }
            .getOrNull()?.outcome ?: RefreshOutcome.FAILED
        if (outcome == RefreshOutcome.FAILED && runAttemptCount < MAX_ATTEMPTS - 1) return Result.retry()

        // Last act: REPLACE of our own unique name -- nothing may follow it (it cancels this request).
        runCatching {
            val prefs = container.userPreferencesRepository
            RecommendationScheduler.scheduleNext(applicationContext, prefs.refreshDayOfWeek.first(), prefs.refreshHour.first())
        }
        return if (outcome == RefreshOutcome.FAILED) Result.failure() else Result.success()
    }

    companion object {
        const val MAX_ATTEMPTS = 3
    }
}
