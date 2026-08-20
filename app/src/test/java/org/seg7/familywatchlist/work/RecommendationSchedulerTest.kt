package org.seg7.familywatchlist.work

import java.time.Duration
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Test

/** PLAN.md §4: "WorkManager weekly (Monday 06:00)" — the pure delay-computation logic, independent of wall-clock time or Android/WorkManager itself. */
class RecommendationSchedulerTest {

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0) =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZoneOffset.UTC)

    @Test
    fun `just before 6am on a Monday delays to 6am that same day`() {
        val now = at(2026, 8, 17, 5, 30) // Monday
        val delay = RecommendationScheduler.initialDelayMillis(now)
        assertEquals(Duration.ofMinutes(30).toMillis(), delay)
    }

    @Test
    fun `just after 6am on a Monday delays a full week to next Monday`() {
        val now = at(2026, 8, 17, 6, 30) // Monday
        val delay = RecommendationScheduler.initialDelayMillis(now)
        assertEquals(TimeUnit.DAYS.toMillis(7) - TimeUnit.MINUTES.toMillis(30), delay)
    }

    @Test
    fun `exactly 6am on a Monday delays a full week, not zero`() {
        val now = at(2026, 8, 17, 6, 0) // Monday, exactly on the mark
        val delay = RecommendationScheduler.initialDelayMillis(now)
        assertEquals(TimeUnit.DAYS.toMillis(7), delay)
    }

    @Test
    fun `mid-week delays to the coming Monday`() {
        val now = at(2026, 8, 19, 12, 0) // Wednesday noon
        val delay = RecommendationScheduler.initialDelayMillis(now)
        val expectedMonday = at(2026, 8, 24, 6, 0) // the following Monday
        assertEquals(Duration.between(now, expectedMonday).toMillis(), delay)
    }

    @Test
    fun `sunday just before midnight delays to the next morning's Monday 6am`() {
        val now = at(2026, 8, 23, 23, 0) // Sunday 23:00
        val delay = RecommendationScheduler.initialDelayMillis(now)
        val expectedMonday = at(2026, 8, 24, 6, 0)
        assertEquals(Duration.between(now, expectedMonday).toMillis(), delay)
    }
}
