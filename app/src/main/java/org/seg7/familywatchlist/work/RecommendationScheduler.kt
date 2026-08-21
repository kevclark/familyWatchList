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
 * PLAN.md §4: schedules [RecommendationWorker] to run weekly, at a configurable day/hour (M3f —
 * [org.seg7.familywatchlist.data.repository.UserPreferencesRepository.refreshDayOfWeek]/
 * [org.seg7.familywatchlist.data.repository.UserPreferencesRepository.refreshHour], default
 * Friday 06:00; was a hardcoded Monday 06:00 literal through M3e). `enqueueUniquePeriodicWork`
 * with [ExistingPeriodicWorkPolicy.KEEP] via [scheduleWeekly] makes this idempotent — safe to
 * call on every app start ([org.seg7.familywatchlist.FamilyWatchListApp.onCreate]) without
 * resetting an already-scheduled job's cadence/next-run time.
 *
 * **M3f correctness requirement:** that same `KEEP` idempotency means a genuine settings change
 * (the user picking a new day/hour in Settings) would otherwise be silently ignored — WorkManager
 * would leave the previously-scheduled job running untouched. [rescheduleForSettingsChange] is
 * the distinct code path for that case: same request shape, [ExistingPeriodicWorkPolicy.UPDATE]
 * instead, called specifically from wherever Settings persists the new preference — never from
 * `onCreate`.
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

    /** Preference defaults (PLAN.md §4 "Configurable schedule", M3f): Friday, 06:00. */
    val DEFAULT_DAY_OF_WEEK: DayOfWeek = DayOfWeek.FRIDAY
    const val DEFAULT_HOUR: Int = 6

    /**
     * Routine app-start call ([org.seg7.familywatchlist.FamilyWatchListApp.onCreate]). Idempotent
     * via [ExistingPeriodicWorkPolicy.KEEP] — deliberately does NOT reset an already-scheduled
     * job's next-run time just because the process launched again. [dayOfWeek]/[hour] only take
     * effect the first time this unique work name is enqueued (fresh install, or after the job
     * was cancelled); on every subsequent app start with a job already scheduled, `KEEP` leaves
     * the previously-scheduled time alone regardless of what's passed here.
     */
    fun scheduleWeekly(context: Context, dayOfWeek: DayOfWeek = DEFAULT_DAY_OF_WEEK, hour: Int = DEFAULT_HOUR) {
        enqueue(context, dayOfWeek, hour, ExistingPeriodicWorkPolicy.KEEP)
    }

    /**
     * M3f: called specifically when the user changes the schedule setting in Settings —
     * [ExistingPeriodicWorkPolicy.UPDATE] genuinely replaces the previously-scheduled job's
     * next-run time instead of `KEEP`'s no-op-if-already-scheduled behaviour. Never called from
     * `onCreate`.
     */
    fun rescheduleForSettingsChange(context: Context, dayOfWeek: DayOfWeek, hour: Int) {
        enqueue(context, dayOfWeek, hour, ExistingPeriodicWorkPolicy.UPDATE)
    }

    private fun enqueue(context: Context, dayOfWeek: DayOfWeek, hour: Int, policy: ExistingPeriodicWorkPolicy) {
        val request = PeriodicWorkRequestBuilder<RecommendationWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(initialDelayMillis(dayOfWeek = dayOfWeek, hour = hour), TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, policy, request)
    }

    /**
     * Milliseconds from [now] until the next [dayOfWeek] at [hour]:00 (today if it's still ahead,
     * otherwise next week). Package-visible for a direct unit test. [dayOfWeek]/[hour] are
     * explicit parameters rather than a live DataStore read — callers ([scheduleWeekly] and
     * [rescheduleForSettingsChange]) resolve the configured preference first and pass concrete
     * values in, keeping this a pure function of its arguments for deterministic unit testing.
     */
    internal fun initialDelayMillis(
        now: ZonedDateTime = ZonedDateTime.now(),
        dayOfWeek: DayOfWeek = DEFAULT_DAY_OF_WEEK,
        hour: Int = DEFAULT_HOUR,
    ): Long {
        val todayAtHour = now.withHour(hour).withMinute(0).withSecond(0).withNano(0)
        var next = if (now.dayOfWeek == dayOfWeek && now.isBefore(todayAtHour)) {
            todayAtHour
        } else {
            now.with(TemporalAdjusters.next(dayOfWeek)).withHour(hour).withMinute(0).withSecond(0).withNano(0)
        }
        if (!next.isAfter(now)) next = next.plusWeeks(1)
        return Duration.between(now, next).toMillis()
    }
}
