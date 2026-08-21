package org.seg7.familywatchlist.work

import java.time.DayOfWeek
import java.time.Duration
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * PLAN.md §4 "Configurable schedule" (M3f): the pure delay-computation logic, independent of
 * wall-clock time or Android/WorkManager itself. [RecommendationScheduler.initialDelayMillis]
 * takes the configured day-of-week/hour as explicit parameters (no live DataStore read) so this
 * stays a deterministic function of its arguments.
 *
 * The original Monday/06:00 cases (from before M3f made the schedule configurable) are kept
 * as-is, just passing `DayOfWeek.MONDAY`/`6` explicitly now instead of relying on hardcoded
 * defaults inside the function — still a useful regression check that the pure function's logic
 * works for *a* day/hour. The Friday/06:00 cases below match the new app-wide default
 * (PLAN.md §4: "Default: Friday, 06:00"), and the Tuesday/09:00 cases prove the function is
 * genuinely parametric over an arbitrary day *and* hour, not just the new default either.
 */
class RecommendationSchedulerTest {

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0) =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZoneOffset.UTC)

    // --- Monday 06:00 (the old hardcoded default, still a valid configured value) -------------

    @Test
    fun `just before 6am on a Monday delays to 6am that same day`() {
        val now = at(2026, 8, 17, 5, 30) // Monday
        val delay = RecommendationScheduler.initialDelayMillis(now, DayOfWeek.MONDAY, 6)
        assertEquals(Duration.ofMinutes(30).toMillis(), delay)
    }

    @Test
    fun `just after 6am on a Monday delays a full week to next Monday`() {
        val now = at(2026, 8, 17, 6, 30) // Monday
        val delay = RecommendationScheduler.initialDelayMillis(now, DayOfWeek.MONDAY, 6)
        assertEquals(TimeUnit.DAYS.toMillis(7) - TimeUnit.MINUTES.toMillis(30), delay)
    }

    @Test
    fun `exactly 6am on a Monday delays a full week, not zero`() {
        val now = at(2026, 8, 17, 6, 0) // Monday, exactly on the mark
        val delay = RecommendationScheduler.initialDelayMillis(now, DayOfWeek.MONDAY, 6)
        assertEquals(TimeUnit.DAYS.toMillis(7), delay)
    }

    @Test
    fun `mid-week delays to the coming Monday`() {
        val now = at(2026, 8, 19, 12, 0) // Wednesday noon
        val delay = RecommendationScheduler.initialDelayMillis(now, DayOfWeek.MONDAY, 6)
        val expectedMonday = at(2026, 8, 24, 6, 0) // the following Monday
        assertEquals(Duration.between(now, expectedMonday).toMillis(), delay)
    }

    @Test
    fun `sunday just before midnight delays to the next morning's Monday 6am`() {
        val now = at(2026, 8, 23, 23, 0) // Sunday 23:00
        val delay = RecommendationScheduler.initialDelayMillis(now, DayOfWeek.MONDAY, 6)
        val expectedMonday = at(2026, 8, 24, 6, 0)
        assertEquals(Duration.between(now, expectedMonday).toMillis(), delay)
    }

    // --- Friday 06:00 (the new app-wide default, PLAN.md §4) -----------------------------------

    @Test
    fun `just before 6am on a Friday delays to 6am that same day`() {
        val now = at(2026, 8, 21, 5, 30) // Friday
        val delay = RecommendationScheduler.initialDelayMillis(now, DayOfWeek.FRIDAY, 6)
        assertEquals(Duration.ofMinutes(30).toMillis(), delay)
    }

    @Test
    fun `just after 6am on a Friday delays a full week to next Friday`() {
        val now = at(2026, 8, 21, 6, 30) // Friday
        val delay = RecommendationScheduler.initialDelayMillis(now, DayOfWeek.FRIDAY, 6)
        assertEquals(TimeUnit.DAYS.toMillis(7) - TimeUnit.MINUTES.toMillis(30), delay)
    }

    @Test
    fun `mid-week (Wednesday) delays to the coming Friday`() {
        val now = at(2026, 8, 19, 12, 0) // Wednesday noon
        val delay = RecommendationScheduler.initialDelayMillis(now, DayOfWeek.FRIDAY, 6)
        val expectedFriday = at(2026, 8, 21, 6, 0) // the coming Friday
        assertEquals(Duration.between(now, expectedFriday).toMillis(), delay)
    }

    // --- Tuesday 09:00 (an arbitrary non-default day AND hour, to prove genuine parametricity) --

    @Test
    fun `just before 9am on a Tuesday (arbitrary configured hour) delays to 9am that same day`() {
        val now = at(2026, 8, 18, 8, 0) // Tuesday
        val delay = RecommendationScheduler.initialDelayMillis(now, DayOfWeek.TUESDAY, 9)
        assertEquals(Duration.ofHours(1).toMillis(), delay)
    }

    @Test
    fun `just after 9am on a Tuesday delays a full week to next Tuesday`() {
        val now = at(2026, 8, 18, 10, 0) // Tuesday
        val delay = RecommendationScheduler.initialDelayMillis(now, DayOfWeek.TUESDAY, 9)
        assertEquals(TimeUnit.DAYS.toMillis(7) - TimeUnit.HOURS.toMillis(1), delay)
    }

    @Test
    fun `mid-week (Thursday) delays to the coming Tuesday`() {
        val now = at(2026, 8, 20, 15, 0) // Thursday
        val delay = RecommendationScheduler.initialDelayMillis(now, DayOfWeek.TUESDAY, 9)
        val expectedTuesday = at(2026, 8, 25, 9, 0) // the following Tuesday
        assertEquals(Duration.between(now, expectedTuesday).toMillis(), delay)
    }
}
