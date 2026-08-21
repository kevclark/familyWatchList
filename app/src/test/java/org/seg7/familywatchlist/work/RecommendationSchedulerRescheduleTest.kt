package org.seg7.familywatchlist.work

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * PLAN.md §4 "Configurable schedule" (M3f) critical correctness requirement: a routine app-start
 * call ([RecommendationScheduler.scheduleWeekly], [androidx.work.ExistingPeriodicWorkPolicy.KEEP])
 * must never move an already-scheduled job's next-run time, but a genuine settings change
 * ([RecommendationScheduler.rescheduleForSettingsChange],
 * [androidx.work.ExistingPeriodicWorkPolicy.UPDATE]) must actually move it. Drives real
 * WorkManager (via work-testing's [SynchronousExecutor], so enqueue completes on the calling
 * thread before the next assertion runs) and reads WorkInfo's own
 * `nextScheduleTimeMillis` back — proving the stored preference isn't just written but genuinely
 * changes what WorkManager will do next, per M3f's explicit "not just a stored-but-inert
 * preference" test requirement.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecommendationSchedulerRescheduleTest {

    private val context: Application = ApplicationProvider.getApplicationContext()

    @Before
    fun initWorkManager() {
        val config = Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    }

    private fun nextScheduleTimeMillis(): Long {
        val infos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork("weekly_recommendation_refresh")
            .get()
        return infos.single().nextScheduleTimeMillis
    }

    @Test
    fun `a routine app-start call does not move an already-scheduled job's next-run time`() {
        RecommendationScheduler.scheduleWeekly(context, DayOfWeek.FRIDAY, 6)
        val firstScheduledTime = nextScheduleTimeMillis()

        // Simulate a later app start where the stored preference has since changed underneath —
        // scheduleWeekly's KEEP policy must leave the already-enqueued job's time untouched, not
        // silently pick up the new day/hour.
        RecommendationScheduler.scheduleWeekly(context, DayOfWeek.TUESDAY, 14)

        assertEquals(
            "KEEP must not move an already-scheduled job's next-run time on a routine app start",
            firstScheduledTime,
            nextScheduleTimeMillis(),
        )
    }

    @Test
    fun `changing the setting actually reschedules the job to the new time`() {
        RecommendationScheduler.scheduleWeekly(context, DayOfWeek.FRIDAY, 6)
        val originalScheduledTime = nextScheduleTimeMillis()

        RecommendationScheduler.rescheduleForSettingsChange(context, DayOfWeek.TUESDAY, 14)

        assertNotEquals(
            "UPDATE must genuinely move the job's next-run time, not leave the old schedule running",
            originalScheduledTime,
            nextScheduleTimeMillis(),
        )
    }

    @Test
    fun `rescheduling to the same day and hour still uses UPDATE without erroring`() {
        // Not a behaviour change, just a guard against rescheduleForSettingsChange assuming the
        // new value always differs from the old one.
        RecommendationScheduler.scheduleWeekly(context, DayOfWeek.FRIDAY, 6)
        val originalScheduledTime = nextScheduleTimeMillis()

        RecommendationScheduler.rescheduleForSettingsChange(context, DayOfWeek.FRIDAY, 6)

        // A few milliseconds of real-clock drift between the two `ZonedDateTime.now()` reads
        // (one per enqueue call) is expected and not a target-time difference — assert "same
        // target instant" with a generous tolerance rather than bit-for-bit equality.
        val driftMillis = Math.abs(originalScheduledTime - nextScheduleTimeMillis())
        assertTrue("expected the same target instant, drifted by ${driftMillis}ms", driftMillis < 5_000)
    }
}
