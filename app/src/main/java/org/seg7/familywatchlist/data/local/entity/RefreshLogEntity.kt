package org.seg7.familywatchlist.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** What started a refresh run (PLAN.md §5d). */
enum class RefreshTrigger { SCHEDULED, CATCH_UP, MANUAL }

/**
 * How a refresh run ended (PLAN.md §5d). [RUNNING] is only ever stored for the span of a live run;
 * a row still [RUNNING] when a later run starts means the process died mid-run, and is rewritten to
 * [FAILED] ("Interrupted") by [org.seg7.familywatchlist.data.repository.RefreshLogRepository].
 */
enum class RefreshOutcome { RUNNING, SUCCESS, PARTIAL, FAILED }

/**
 * PLAN.md §5d (M15): one row per refresh run, so Kev (and we) can see whether the weekly job
 * actually ran, what it found, and whether the notification was posted or suppressed (and why).
 * About the last [org.seg7.familywatchlist.data.local.dao.RefreshLogDao.KEEP_LATEST] rows are kept.
 *
 * [summaryJson] is a JSON-encoded list of
 * [org.seg7.familywatchlist.data.recommend.ProfileRunSummary] (per-profile new/dropped picks) —
 * stored denormalised as JSON because it is only ever read back whole for display, never queried.
 * [notificationStatus] is a short human-readable sentence ("Posted for Kev and Family", "Not sent:
 * notifications are off in Settings", ...), null while the run is still in flight.
 */
@Entity(tableName = "refresh_log")
data class RefreshLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val trigger: RefreshTrigger,
    val outcome: RefreshOutcome,
    val reason: String? = null,
    val summaryJson: String = "[]",
    val notificationStatus: String? = null,
)
