package org.seg7.familywatchlist.work

import android.content.Context
import java.time.DayOfWeek
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * PLAN.md §4 / §5d (M15): schedules [RecommendationWorker] for the user's configured weekly slot
 * ([org.seg7.familywatchlist.data.repository.UserPreferencesRepository.refreshDayOfWeek]/
 * [org.seg7.familywatchlist.data.repository.UserPreferencesRepository.refreshHour], default
 * Friday 06:00).
 *
 * **Why one-time, re-anchored (M15):** through M14 this was a 7-day `PeriodicWorkRequest`. Only
 * its *first* run honoured the day/hour initial delay; every later run was 7 days after the
 * previous *actual* run, so any lateness (Doze, the standby bucket, the network constraint) carried
 * forward and the slot drifted -- Kev's Friday-13:00 notification stopped arriving on Fridays.
 * Now there is always exactly one pending unique **one-time** request aimed at the next
 * configured slot, and [RecommendationWorker] books the following one as the last thing it does
 * ([scheduleNext]), computed from the wall clock at that moment. A late run therefore never pushes
 * later weeks back. Exact alarms were offered and declined, so the OS may still defer a run by
 * minutes-to-hours; catch-up on app open ([RefreshCoordinator.maybeCatchUp]) covers the rest.
 *
 * Policies (same semantics as before, expressed on one-time work):
 *  - [scheduleWeekly], app start: [ExistingWorkPolicy.KEEP] -- never resets a pending run.
 *  - [rescheduleForSettingsChange], Settings: [ExistingWorkPolicy.REPLACE] -- genuinely moves it.
 *  - [scheduleNext], from the worker: REPLACE too (it replaces itself, as its final act).
 *
 * **Upgrade:** the old periodic job lived under [LEGACY_PERIODIC_WORK_NAME]; every schedule call
 * cancels it, so two jobs never run. The new work uses a different unique name precisely so the
 * old periodic entry can't make KEEP silently no-op.
 *
 * **"unmetered-preferred":** [NetworkType.CONNECTED] remains the practical approximation of a
 * preference WorkManager can't express (see git history for the original reasoning).
 */
object RecommendationScheduler {
    const val UNIQUE_WORK_NAME = "weekly_recommendation_slot"
    const val LEGACY_PERIODIC_WORK_NAME = "weekly_recommendation_refresh"

    /** Preference defaults (PLAN.md §4 "Configurable schedule", M3f): Friday, 06:00. */
    val DEFAULT_DAY_OF_WEEK: DayOfWeek = DayOfWeek.FRIDAY
    const val DEFAULT_HOUR: Int = 6

    /** Routine app-start call: books the next slot only if none is pending ([ExistingWorkPolicy.KEEP]). */
    fun scheduleWeekly(context: Context, dayOfWeek: DayOfWeek = DEFAULT_DAY_OF_WEEK, hour: Int = DEFAULT_HOUR) {
        enqueue(context, dayOfWeek, hour, ExistingWorkPolicy.KEEP)
    }

    /** Settings changed the day/hour: replaces the pending run so the new slot takes effect. */
    fun rescheduleForSettingsChange(context: Context, dayOfWeek: DayOfWeek, hour: Int) {
        enqueue(context, dayOfWeek, hour, ExistingWorkPolicy.REPLACE)
    }

    /**
     * Called by [RecommendationWorker] when its run is finished for good (success, or failure after
     * retries): books the next slot relative to *now*, which is what stops lateness accumulating.
     */
    fun scheduleNext(context: Context, dayOfWeek: DayOfWeek, hour: Int) {
        enqueue(context, dayOfWeek, hour, ExistingWorkPolicy.REPLACE)
    }

    private fun enqueue(context: Context, dayOfWeek: DayOfWeek, hour: Int, policy: ExistingWorkPolicy) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(LEGACY_PERIODIC_WORK_NAME)
        val request = OneTimeWorkRequestBuilder<RecommendationWorker>()
            .setInitialDelay(initialDelayMillis(dayOfWeek = dayOfWeek, hour = hour), TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        workManager.enqueueUniqueWork(UNIQUE_WORK_NAME, policy, request)
    }

    /**
     * Milliseconds from [now] until the next [dayOfWeek] at [hour]:00 (today if still ahead,
     * otherwise next week). Pure function of its arguments for deterministic testing; this is
     * also the re-anchor calculation -- called at the end of a (possibly late) run it yields the
     * delay to the *next* slot, not "7 days from now".
     */
    internal fun initialDelayMillis(
        now: ZonedDateTime = ZonedDateTime.now(),
        dayOfWeek: DayOfWeek = DEFAULT_DAY_OF_WEEK,
        hour: Int = DEFAULT_HOUR,
    ): Long = Duration.between(now, RefreshSlots.nextSlot(now, dayOfWeek, hour)).toMillis()
}
