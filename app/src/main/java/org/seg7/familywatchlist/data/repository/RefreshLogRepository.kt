package org.seg7.familywatchlist.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.seg7.familywatchlist.common.AppClock
import org.seg7.familywatchlist.data.local.dao.RefreshLogDao
import org.seg7.familywatchlist.data.local.entity.RefreshLogEntity
import org.seg7.familywatchlist.data.local.entity.RefreshOutcome
import org.seg7.familywatchlist.data.local.entity.RefreshTrigger
import org.seg7.familywatchlist.data.recommend.ProfileRunSummary

/** A [RefreshLogEntity] with its JSON summary decoded, for the Activity screen. */
data class RefreshRun(
    val id: Long,
    val startedAt: Long,
    val finishedAt: Long?,
    val trigger: RefreshTrigger,
    val outcome: RefreshOutcome,
    val reason: String?,
    val profiles: List<ProfileRunSummary>,
    val notificationStatus: String?,
)

/**
 * PLAN.md §5d (M15): reads and writes the `refresh_log` — one row per refresh run, newest
 * [RefreshLogDao.KEEP_LATEST] kept. A row is inserted as [RefreshOutcome.RUNNING] when a run starts
 * (so a run the OS kills mid-way still leaves evidence it began) and completed by [finish].
 */
class RefreshLogRepository(
    private val dao: RefreshLogDao,
    private val clock: AppClock,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val summaryListSerializer = ListSerializer(ProfileRunSummary.serializer())

    fun observeRuns(): Flow<List<RefreshRun>> = dao.observeAll().map { rows -> rows.map { it.toRun() } }

    fun observeLastFinishedAt(): Flow<Long?> = dao.observeLastFinishedAt()

    suspend fun lastSuccessfulFinishedAt(): Long? = dao.lastSuccessfulFinishedAt()

    suspend fun lastAttemptStartedAt(): Long? = dao.lastStartedAt()

    /**
     * Opens a run. The caller must hold the refresh guard: any row still RUNNING at this point can
     * only belong to a dead process, so it is closed out as failed first.
     */
    suspend fun start(trigger: RefreshTrigger): Long {
        dao.failInterrupted()
        return dao.insert(
            RefreshLogEntity(startedAt = clock.nowMillis(), trigger = trigger, outcome = RefreshOutcome.RUNNING),
        )
    }

    suspend fun finish(
        id: Long,
        outcome: RefreshOutcome,
        reason: String?,
        summaries: List<ProfileRunSummary>,
        notificationStatus: String?,
    ) {
        val row = dao.getById(id) ?: return
        dao.update(
            row.copy(
                finishedAt = clock.nowMillis(),
                outcome = outcome,
                reason = reason,
                summaryJson = json.encodeToString(summaryListSerializer, summaries),
                notificationStatus = notificationStatus,
            ),
        )
        dao.pruneToLatest(RefreshLogDao.KEEP_LATEST)
    }

    private fun RefreshLogEntity.toRun() = RefreshRun(
        id = id,
        startedAt = startedAt,
        finishedAt = finishedAt,
        trigger = trigger,
        outcome = outcome,
        reason = reason,
        profiles = runCatching { json.decodeFromString(summaryListSerializer, summaryJson) }.getOrDefault(emptyList()),
        notificationStatus = notificationStatus,
    )
}
