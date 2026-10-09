package org.seg7.familywatchlist.ui.activity

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.seg7.familywatchlist.data.local.entity.RefreshOutcome
import org.seg7.familywatchlist.data.local.entity.RefreshTrigger
import org.seg7.familywatchlist.data.recommend.ProfileRunSummary
import org.seg7.familywatchlist.data.repository.RefreshRun

/**
 * PLAN.md §5d part 3 (M15): the Activity screen's wording, kept as pure functions so the exact
 * strings (header, headline, per-profile lines) are unit-tested rather than eyeballed.
 */
object ActivityFormat {
    private val shortTime = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.UK)
    private val fullSlot = DateTimeFormatter.ofPattern("EEE d MMM HH:mm", Locale.UK)

    /** "Fri 13:04". */
    fun lastRefresh(millis: Long, zone: ZoneId): String =
        ZonedDateTime.ofInstant(Instant.ofEpochMilli(millis), zone).format(shortTime)

    /** "Fri 16 Oct 13:00". */
    fun slot(at: ZonedDateTime): String = at.format(fullSlot)

    /** "Last refresh: Fri 13:04 · Next: Fri 16 Oct 13:00" (or "none yet"). */
    fun header(lastFinishedMillis: Long?, next: ZonedDateTime, zone: ZoneId): String {
        val last = lastFinishedMillis?.let { lastRefresh(it, zone) } ?: "none yet"
        return "Last refresh: $last · Next: ${slot(next)}"
    }

    /** "Fri 9 Oct, 13:04" -- row title in the run list. */
    fun runTime(millis: Long, zone: ZoneId): String =
        ZonedDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)
            .format(DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.UK))

    fun trigger(trigger: RefreshTrigger): String = when (trigger) {
        RefreshTrigger.SCHEDULED -> "Scheduled"
        RefreshTrigger.CATCH_UP -> "Catch-up"
        RefreshTrigger.MANUAL -> "Manual"
    }

    fun outcome(outcome: RefreshOutcome): String = when (outcome) {
        RefreshOutcome.RUNNING -> "Running"
        RefreshOutcome.SUCCESS -> "Success"
        RefreshOutcome.PARTIAL -> "Partial"
        RefreshOutcome.FAILED -> "Failed"
    }

    /** One collapsed-row line: what the run found, or why it failed. */
    fun headline(run: RefreshRun): String {
        val newTotal = run.profiles.sumOf { it.newCount }
        return when (run.outcome) {
            RefreshOutcome.RUNNING -> "In progress"
            RefreshOutcome.FAILED -> run.reason ?: "Refresh failed"
            else -> when {
                newTotal == 1 -> "1 new pick"
                newTotal > 1 -> "$newTotal new picks"
                run.profiles.none { it.status == ProfileRunSummary.STATUS_OK } -> "No shortlists to refresh yet"
                else -> "No new picks"
            }
        }
    }

    /** "Kev: 30 picks, 4 new". */
    fun profileLine(p: ProfileRunSummary): String = when (p.status) {
        ProfileRunSummary.STATUS_FAILED -> "${p.name}: failed${p.error?.let { " ($it)" }.orEmpty()}"
        ProfileRunSummary.STATUS_COLD_START -> "${p.name}: not enough watch history yet"
        else -> if (!p.hadPrevious) {
            "${p.name}: ${p.total} picks (first shortlist)"
        } else {
            "${p.name}: ${p.total} picks, ${p.newCount} new"
        }
    }

    /** "Dune, Arrival +2 more", or null when there is nothing to list. */
    fun titleList(count: Int, named: List<String>): String? {
        if (count <= 0 || named.isEmpty()) return null
        val extra = count - named.size
        return named.joinToString(", ") + if (extra > 0) " +$extra more" else ""
    }
}
