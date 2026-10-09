package org.seg7.familywatchlist.data.recommend

import kotlinx.serialization.Serializable

/**
 * PLAN.md §5d (M15): the "what changed" between a scope's previous shortlist and its new one.
 * Pure, so the one diff that feeds both the Activity log and Home's "New" badge is unit-testable
 * without Room.
 *
 * [previous] null means there was no earlier shortlist for this scope at all (first ever
 * refresh, or the profile was cold-start until now). That is a baseline, not a change: [added] is
 * empty so nothing gets badged "New" and the log doesn't claim 30 "new picks" on day one.
 */
data class ShortlistDiff(
    val added: List<TitleKey>,
    val dropped: List<TitleKey>,
    val total: Int,
    val hadPrevious: Boolean,
) {
    companion object {
        fun compute(previous: Collection<TitleKey>?, current: List<TitleKey>): ShortlistDiff {
            if (previous == null) return ShortlistDiff(emptyList(), emptyList(), current.size, hadPrevious = false)
            val prevSet = previous.toSet()
            val currSet = current.toSet()
            return ShortlistDiff(
                added = current.filter { it !in prevSet },
                dropped = previous.filter { it !in currSet },
                total = current.size,
                hadPrevious = true,
            )
        }
    }
}

/**
 * One profile's line in a refresh run (PLAN.md §5d), serialised into `refresh_log.summaryJson`.
 * [newTitles]/[droppedTitles] name at most [MAX_NAMED] titles each; [newCount]/[droppedCount] are
 * the full counts. [status] is [STATUS_OK], [STATUS_COLD_START] (not enough history for a
 * personal shortlist yet) or [STATUS_FAILED] (with [error]).
 */
@Serializable
data class ProfileRunSummary(
    val profileId: Long,
    val name: String,
    val status: String = STATUS_OK,
    val total: Int = 0,
    val newCount: Int = 0,
    val newTitles: List<String> = emptyList(),
    val droppedCount: Int = 0,
    val droppedTitles: List<String> = emptyList(),
    val hadPrevious: Boolean = true,
    val error: String? = null,
) {
    companion object {
        const val STATUS_OK = "OK"
        const val STATUS_COLD_START = "COLD_START"
        const val STATUS_FAILED = "FAILED"
        const val MAX_NAMED = 5
    }
}
