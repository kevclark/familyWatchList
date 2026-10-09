package org.seg7.familywatchlist.work

import java.time.DayOfWeek
import java.time.Duration
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PLAN.md §5d (M15): re-anchored scheduling and catch-up "missed slot" detection, as pure
 * functions of an explicit `now`. 2026-10-09 is a Friday (Kev's real report).
 */
class RefreshSlotsTest {
    private fun at(month: Int, day: Int, hour: Int, minute: Int = 0) =
        ZonedDateTime.of(2026, month, day, hour, minute, 0, 0, ZoneOffset.UTC)

    private fun millis(z: ZonedDateTime) = z.toInstant().toEpochMilli()

    // --- re-anchor: the delay to the next slot is a function of *now*, so lateness doesn't accumulate

    @Test
    fun `a run that finishes 40 minutes late books the next slot a week minus 40 minutes away`() {
        val finishedLate = at(10, 9, 13, 40) // Friday, slot was 13:00
        val delay = RecommendationScheduler.initialDelayMillis(finishedLate, DayOfWeek.FRIDAY, 13)
        assertEquals(TimeUnit.DAYS.toMillis(7) - TimeUnit.MINUTES.toMillis(40), delay)
        // ...which lands exactly on next Friday 13:00, not 7 days after the late finish.
        assertEquals(at(10, 16, 13), finishedLate.plus(Duration.ofMillis(delay)))
    }

    @Test
    fun `a run that finishes a whole day late still re-anchors to the next configured slot`() {
        val finishedSaturday = at(10, 10, 9) // Saturday morning, slot was Friday 13:00
        assertEquals(at(10, 16, 13), RefreshSlots.nextSlot(finishedSaturday, DayOfWeek.FRIDAY, 13))
    }

    @Test
    fun `a run that finishes a few seconds early or exactly on the slot never rebooks the same slot`() {
        val onTheDot = at(10, 9, 13)
        assertEquals(at(10, 16, 13), RefreshSlots.nextSlot(onTheDot, DayOfWeek.FRIDAY, 13))
    }

    // --- mostRecentSlot ------------------------------------------------------------------------

    @Test
    fun `mostRecentSlot on the slot day before the hour is last week's slot`() {
        assertEquals(at(10, 2, 13), RefreshSlots.mostRecentSlot(at(10, 9, 12, 59), DayOfWeek.FRIDAY, 13))
    }

    @Test
    fun `mostRecentSlot at or after the hour on the slot day is today's slot`() {
        assertEquals(at(10, 9, 13), RefreshSlots.mostRecentSlot(at(10, 9, 13), DayOfWeek.FRIDAY, 13))
        assertEquals(at(10, 9, 13), RefreshSlots.mostRecentSlot(at(10, 9, 23, 59), DayOfWeek.FRIDAY, 13))
    }

    @Test
    fun `mostRecentSlot mid-week is the most recent past occurrence`() {
        assertEquals(at(10, 9, 13), RefreshSlots.mostRecentSlot(at(10, 14, 8), DayOfWeek.FRIDAY, 13)) // Wednesday
    }

    // --- catch-up detection --------------------------------------------------------------------

    private fun due(now: ZonedDateTime, lastSuccess: Long?, lastAttempt: Long? = null) =
        RefreshSlots.isCatchUpDue(now, DayOfWeek.FRIDAY, 13, lastSuccess, lastAttempt)

    @Test
    fun `slot missed and no refresh since is due`() {
        // Kev's case: Friday 13:00 slot, nothing ran, he opens the app Friday evening.
        assertTrue(due(at(10, 9, 19), lastSuccess = millis(at(10, 2, 13, 5))))
    }

    @Test
    fun `a successful refresh after the slot means nothing was missed`() {
        assertFalse(due(at(10, 9, 19), lastSuccess = millis(at(10, 9, 13, 4))))
    }

    @Test
    fun `a refresh that finished exactly at the slot instant counts`() {
        assertFalse(due(at(10, 9, 19), lastSuccess = millis(at(10, 9, 13))))
    }

    @Test
    fun `a refresh from before the slot does not count`() {
        assertTrue(due(at(10, 9, 19), lastSuccess = millis(at(10, 9, 12, 59))))
    }

    @Test
    fun `never refreshed at all is due`() {
        assertTrue(due(at(10, 9, 19), lastSuccess = null))
    }

    @Test
    fun `within the grace period after the slot the scheduled worker gets first go`() {
        assertFalse(due(at(10, 9, 13, 10), lastSuccess = null))
        assertTrue(due(at(10, 9, 13, 16), lastSuccess = null))
    }

    @Test
    fun `a recent failed attempt suppresses an immediate automatic retry`() {
        val now = at(10, 9, 19)
        assertFalse(due(now, lastSuccess = null, lastAttempt = millis(now) - Duration.ofMinutes(5).toMillis()))
        assertTrue(due(now, lastSuccess = null, lastAttempt = millis(now) - Duration.ofMinutes(45).toMillis()))
    }

    @Test
    fun `a refresh from a week-old slot is still caught up the following week`() {
        // Last success was before last Friday's slot too: still due (the most recent slot is what matters).
        assertTrue(due(at(10, 12, 9), lastSuccess = millis(at(10, 1, 13, 5))))
    }
}
