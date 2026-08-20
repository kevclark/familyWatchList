package org.seg7.familywatchlist.work

import android.content.Context
import java.time.DayOfWeek
import java.time.Duration
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager

/**
 * PLAN.md §4: schedules [RecommendationWorker] to run weekly, Monday 06:00. `enqueueUniquePeriodicWork`
 * with [ExistingPeriodicWorkPolicy.KEEP] makes this idempotent — safe to call on every app start
 * ([org.seg7.familywatchlist.FamilyWatchListApp.onCreate]) without resetting an already-scheduled
 * job's cadence/next-run time.
 *
 * **"unmetered-preferred" judgment call:** WorkManager's [Constraints] API only expresses hard
 * requirements (`NetworkType.CONNECTED`/`UNMETERED`/…), not a soft preference that still runs
 * without it — there's no "prefer but don't require" knob. [NetworkType.CONNECTED] is used as the
 * practical approximation (never blocks the weekly refresh entirely on being unmetered, which
 * `NetworkType.UNMETERED` would) rather than building custom preference-then-fallback scheduling
 * logic for a once-a-week background job.
 */
object RecommendationScheduler {
    private const val UNIQUE_WORK_NAME = "weekly_recommendation_refresh"

    fun scheduleWeekly(context: Context) {
        val request = PeriodicWorkRequestBuilder<RecommendationWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(initialDelayMillis(), TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Milliseconds from [now] until the next Monday 06:00 (today if it's still ahead, otherwise next week). Package-visible for a direct unit test. */
    internal fun initialDelayMillis(now: ZonedDateTime = ZonedDateTime.now()): Long {
        val todayAtSix = now.withHour(6).withMinute(0).withSecond(0).withNano(0)
        var next = if (now.dayOfWeek == DayOfWeek.MONDAY && now.isBefore(todayAtSix)) {
            todayAtSix
        } else {
            now.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).withHour(6).withMinute(0).withSecond(0).withNano(0)
        }
        if (!next.isAfter(now)) next = next.plusWeeks(1)
        return Duration.between(now, next).toMillis()
    }
}
