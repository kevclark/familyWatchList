package org.seg7.familywatchlist.common

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Seam for TTL logic: repository tests inject a fake clock instead of depending on wall time. */
interface AppClock {
    fun nowMillis(): Long
}

class SystemAppClock : AppClock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}

/**
 * "Today" as a [LocalDate] for the recommender (PLAN.md §4: recency weighting, freshness,
 * weekly shortlist `weekStart`). Fixed to UTC rather than the device's local zone so a
 * [org.seg7.familywatchlist.testutil.FakeClock] starting at epoch millis 0 deterministically
 * resolves to 1970-01-01 in every test environment, regardless of the machine's local timezone.
 */
fun AppClock.today(): LocalDate = Instant.ofEpochMilli(nowMillis()).atZone(ZoneOffset.UTC).toLocalDate()
