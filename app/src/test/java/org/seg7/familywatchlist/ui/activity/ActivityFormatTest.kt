package org.seg7.familywatchlist.ui.activity

import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.seg7.familywatchlist.data.local.entity.RefreshOutcome
import org.seg7.familywatchlist.data.local.entity.RefreshTrigger
import org.seg7.familywatchlist.data.recommend.ProfileRunSummary
import org.seg7.familywatchlist.data.repository.RefreshRun

class ActivityFormatTest {
    private val zone = ZoneOffset.UTC
    private fun at(month: Int, day: Int, hour: Int, minute: Int = 0) =
        ZonedDateTime.of(2026, month, day, hour, minute, 0, 0, zone)

    private fun run(outcome: RefreshOutcome = RefreshOutcome.SUCCESS, reason: String? = null, vararg profiles: ProfileRunSummary) =
        RefreshRun(1, 0, 1, RefreshTrigger.SCHEDULED, outcome, reason, profiles.toList(), null)

    @Test
    fun `header matches the spec example`() {
        val last = at(10, 9, 13, 4).toInstant().toEpochMilli()
        assertEquals(
            "Last refresh: Fri 13:04 · Next: Fri 16 Oct 13:00",
            ActivityFormat.header(last, at(10, 16, 13), zone),
        )
    }

    @Test
    fun `header with no refresh yet`() {
        assertEquals("Last refresh: none yet · Next: Fri 16 Oct 13:00", ActivityFormat.header(null, at(10, 16, 13), zone))
    }

    @Test
    fun `headline counts new picks across profiles`() {
        val a = ProfileRunSummary(1, "Kev", total = 30, newCount = 3)
        val b = ProfileRunSummary(2, "Sam", total = 30, newCount = 1)
        assertEquals("4 new picks", ActivityFormat.headline(run(profiles = arrayOf(a, b))))
        assertEquals("1 new pick", ActivityFormat.headline(run(profiles = arrayOf(b))))
        assertEquals("No new picks", ActivityFormat.headline(run(profiles = arrayOf(a.copy(newCount = 0)))))
    }

    @Test
    fun `headline for a failed run is its reason`() {
        assertEquals("2 of 2 profiles failed: timeout", ActivityFormat.headline(run(RefreshOutcome.FAILED, "2 of 2 profiles failed: timeout")))
    }

    @Test
    fun `profile lines`() {
        assertEquals("Kev: 30 picks, 4 new", ActivityFormat.profileLine(ProfileRunSummary(1, "Kev", total = 30, newCount = 4)))
        assertEquals("Kev: 30 picks (first shortlist)", ActivityFormat.profileLine(ProfileRunSummary(1, "Kev", total = 30, hadPrevious = false)))
        assertEquals("Kev: not enough watch history yet", ActivityFormat.profileLine(ProfileRunSummary(1, "Kev", status = "COLD_START")))
        assertEquals("Kev: failed (boom)", ActivityFormat.profileLine(ProfileRunSummary(1, "Kev", status = "FAILED", error = "boom")))
    }

    @Test
    fun `title list truncates with a remainder count`() {
        assertEquals("A, B, C, D, E +2 more", ActivityFormat.titleList(7, listOf("A", "B", "C", "D", "E")))
        assertEquals("A, B", ActivityFormat.titleList(2, listOf("A", "B")))
        assertNull(ActivityFormat.titleList(0, emptyList()))
    }
}
