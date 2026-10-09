package org.seg7.familywatchlist.work

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.seg7.familywatchlist.common.AppClock
import org.seg7.familywatchlist.data.local.entity.RefreshOutcome
import org.seg7.familywatchlist.data.local.entity.RefreshTrigger
import org.seg7.familywatchlist.data.recommend.ProfileRunSummary
import org.seg7.familywatchlist.data.repository.RefreshAllOutcome
import org.seg7.familywatchlist.data.repository.RefreshLogRepository

/** What the refresh banner on Home shows (PLAN.md §5d part 2). */
sealed interface RefreshUiState {
    data object Idle : RefreshUiState
    data class Running(val trigger: RefreshTrigger) : RefreshUiState
    data class Finished(val outcome: RefreshOutcome, val runId: Long) : RefreshUiState
}

data class RefreshRunResult(val runId: Long, val outcome: RefreshOutcome)

/** Pure outcome rules, unit-tested without Room. */
object RefreshOutcomes {
    /**
     * Every attempted profile failed -> FAILED; some -> PARTIAL; none (including an account with
     * no profiles yet) -> SUCCESS. Cold-start profiles are skipped by design, not failures.
     */
    fun classify(summaries: List<ProfileRunSummary>): Pair<RefreshOutcome, String?> {
        val failed = summaries.filter { it.status == ProfileRunSummary.STATUS_FAILED }
        if (failed.isEmpty()) return RefreshOutcome.SUCCESS to null
        val firstError = failed.first().error?.let { ": $it" }.orEmpty()
        val reason = "${failed.size} of ${summaries.size} profiles failed$firstError"
        return (if (failed.size == summaries.size) RefreshOutcome.FAILED else RefreshOutcome.PARTIAL) to reason
    }
}

/**
 * PLAN.md §5d (M15): the one place a refresh run happens, whoever asked for it. The scheduled
 * worker, catch-up on app open and the manual refresh all call [run], and a single [Mutex]
 * ([guard]) guarantees never two at once. Each run is recorded in the refresh log (start, outcome,
 * per-profile diff, notification posted/suppressed and why).
 *
 * Collaborators are plain lambdas so the logic is JVM-testable without WorkManager or Room-backed
 * repositories; [org.seg7.familywatchlist.di.AppContainer] wires the real ones.
 *
 * Only the [RefreshTrigger.SCHEDULED] run posts the notification: catch-up and manual runs happen
 * with the app open in front of the user, where a "your picks are ready" notification is noise (the
 * Home banner says it instead). The log records that as the reason.
 */
class RefreshCoordinator(
    private val log: RefreshLogRepository,
    private val clock: AppClock,
    private val scope: CoroutineScope,
    private val schedule: suspend () -> Pair<DayOfWeek, Int>,
    private val region: suspend () -> String,
    private val refreshAll: suspend (region: String) -> RefreshAllOutcome,
    private val invalidateDiscover: suspend () -> Unit,
    private val notificationsMasterEnabled: suspend () -> Boolean,
    private val profileNotificationEnabled: suspend (profileId: Long) -> Boolean,
    private val postNotification: (profileNames: List<String>) -> ShortlistNotifier.Result,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val finishedBannerMillis: Long = 4_000L,
) {
    private val guard = Mutex()
    private val _state = MutableStateFlow<RefreshUiState>(RefreshUiState.Idle)
    val state: StateFlow<RefreshUiState> = _state

    val isRunning: Boolean get() = guard.isLocked

    /**
     * Runs one refresh. Returns null when another refresh already holds the guard and [waitForGuard]
     * is false. The scheduled worker waits (it must run, and it is the one that notifies); catch-up
     * and manual just yield to whatever is already running.
     */
    suspend fun run(
        trigger: RefreshTrigger,
        waitForGuard: Boolean = trigger == RefreshTrigger.SCHEDULED,
    ): RefreshRunResult? {
        if (!guard.tryLock()) {
            if (!waitForGuard) return null
            guard.lock()
        }
        try {
            return execute(trigger)
        } finally {
            guard.unlock()
        }
    }

    /**
     * Lets an incidental recompute (Home opening) run exclusively with real refreshes. Returns
     * null, without running [block], when a refresh is in progress -- that run is about to rewrite
     * the same shortlists anyway.
     */
    suspend fun <T> runIfIdle(block: suspend () -> T): T? {
        if (!guard.tryLock()) return null
        try {
            return block()
        } finally {
            guard.unlock()
        }
    }

    /** Fire-and-forget [run] on the coordinator's own scope, so it outlives the screen that asked. */
    fun start(trigger: RefreshTrigger) {
        scope.launch { run(trigger) }
    }

    /**
     * App-foreground hook (PLAN.md §5d part 2): if the latest scheduled slot has passed and no
     * successful refresh finished since, refresh now. See [RefreshSlots.isCatchUpDue].
     */
    fun maybeCatchUp() {
        scope.launch { catchUpIfDue() }
    }

    /** The suspend body of [maybeCatchUp]; returns true when it started a catch-up run. */
    suspend fun catchUpIfDue(): Boolean {
        if (guard.isLocked) return false
        val (day, hour) = schedule()
        val due = RefreshSlots.isCatchUpDue(
            now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(clock.nowMillis()), zone),
            dayOfWeek = day,
            hour = hour,
            lastSuccessfulFinishedAtMillis = log.lastSuccessfulFinishedAt(),
            lastAttemptStartedAtMillis = log.lastAttemptStartedAt(),
        )
        return due && run(RefreshTrigger.CATCH_UP) != null
    }

    private suspend fun execute(trigger: RefreshTrigger): RefreshRunResult {
        _state.value = RefreshUiState.Running(trigger)
        val runId = log.start(trigger)
        var outcome = RefreshOutcome.FAILED
        var reason: String? = null
        var summaries: List<ProfileRunSummary> = emptyList()
        var notificationStatus: String? = null
        try {
            val result = refreshAll(region())
            invalidateDiscover()
            summaries = result.summaries
            val classified = RefreshOutcomes.classify(summaries)
            outcome = classified.first
            reason = classified.second
            notificationStatus = notify(trigger, outcome, result)
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                log.finish(runId, RefreshOutcome.FAILED, "Stopped by the system before finishing", summaries, null)
            }
            _state.value = RefreshUiState.Idle
            throw e
        } catch (t: Throwable) {
            outcome = RefreshOutcome.FAILED
            reason = t.message?.take(160) ?: t::class.java.simpleName
        }
        log.finish(runId, outcome, reason, summaries, notificationStatus)
        val finished = RefreshUiState.Finished(outcome, runId)
        _state.value = finished
        scope.launch {
            delay(finishedBannerMillis)
            if (_state.value == finished) _state.value = RefreshUiState.Idle
        }
        return RefreshRunResult(runId, outcome)
    }

    private suspend fun notify(trigger: RefreshTrigger, outcome: RefreshOutcome, result: RefreshAllOutcome): String {
        if (trigger != RefreshTrigger.SCHEDULED) return "Not sent: only the scheduled weekly run notifies"
        if (outcome == RefreshOutcome.FAILED) return "Not sent: the refresh failed"
        val master = notificationsMasterEnabled()
        val perProfile = result.completed.associate { it.profileId to profileNotificationEnabled(it.profileId) }
        val toNotify = NotificationGate.profilesToNotify(
            completed = result.completed,
            masterEnabled = master,
            perProfileEnabled = { perProfile[it] ?: true },
        )
        val posted = if (toNotify.isEmpty()) null else postNotification(toNotify.map { it.name })
        return NotificationGate.describe(result.completed, master, toNotify, posted)
    }
}
