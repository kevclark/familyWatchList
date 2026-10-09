package org.seg7.familywatchlist.work

import java.time.DayOfWeek
import java.time.Duration
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/**
 * PLAN.md §5d (M15): pure schedule arithmetic over the configured weekly slot (day-of-week +
 * hour, minute zero). Shared by [RecommendationScheduler] (when to book the next one-time run)
 * and catch-up detection (was the last slot missed?) so the two can't disagree about what "the
 * slot" is. Everything takes an explicit `now`, so it is deterministic under test.
 */
object RefreshSlots {
    /** Don't catch up until the slot has been past this long: gives the scheduled worker first go. */
    val CATCH_UP_GRACE: Duration = Duration.ofMinutes(15)

    /** After any attempt, wait this long before another automatic catch-up (offline app opens must not spam the log). */
    val CATCH_UP_RETRY_COOLDOWN: Duration = Duration.ofMinutes(30)

    private fun ZonedDateTime.atSlotTime(hour: Int): ZonedDateTime =
        withHour(hour).withMinute(0).withSecond(0).withNano(0)

    /** The first slot strictly after [now]. */
    fun nextSlot(now: ZonedDateTime, dayOfWeek: DayOfWeek, hour: Int): ZonedDateTime {
        val todayAtHour = now.atSlotTime(hour)
        var next = if (now.dayOfWeek == dayOfWeek && now.isBefore(todayAtHour)) {
            todayAtHour
        } else {
            now.with(TemporalAdjusters.next(dayOfWeek)).atSlotTime(hour)
        }
        if (!next.isAfter(now)) next = next.plusWeeks(1)
        return next
    }

    /** The latest slot at or before [now]. */
    fun mostRecentSlot(now: ZonedDateTime, dayOfWeek: DayOfWeek, hour: Int): ZonedDateTime {
        val thisWeeks = now.with(TemporalAdjusters.previousOrSame(dayOfWeek)).atSlotTime(hour)
        return if (thisWeeks.isAfter(now)) thisWeeks.minusWeeks(1) else thisWeeks
    }

    /**
     * True when the app should run a catch-up refresh right now: the most recent slot passed more
     * than [CATCH_UP_GRACE] ago, no successful refresh finished at or after it
     * ([lastSuccessfulFinishedAtMillis]; null = never), and no attempt of any outcome started within
     * [CATCH_UP_RETRY_COOLDOWN] ([lastAttemptStartedAtMillis]).
     */
    fun isCatchUpDue(
        now: ZonedDateTime,
        dayOfWeek: DayOfWeek,
        hour: Int,
        lastSuccessfulFinishedAtMillis: Long?,
        lastAttemptStartedAtMillis: Long?,
    ): Boolean {
        val slot = mostRecentSlot(now, dayOfWeek, hour)
        if (Duration.between(slot, now) < CATCH_UP_GRACE) return false
        val slotMillis = slot.toInstant().toEpochMilli()
        if (lastSuccessfulFinishedAtMillis != null && lastSuccessfulFinishedAtMillis >= slotMillis) return false
        if (lastAttemptStartedAtMillis != null &&
            now.toInstant().toEpochMilli() - lastAttemptStartedAtMillis < CATCH_UP_RETRY_COOLDOWN.toMillis()
        ) {
            return false
        }
        return true
    }
}
