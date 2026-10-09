package org.seg7.familywatchlist.work

import java.time.DayOfWeek
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.seg7.familywatchlist.data.local.AppDatabase
import org.seg7.familywatchlist.data.local.entity.RefreshLogEntity
import org.seg7.familywatchlist.data.local.entity.RefreshOutcome
import org.seg7.familywatchlist.data.local.entity.RefreshTrigger
import org.seg7.familywatchlist.data.recommend.ProfileRunSummary
import org.seg7.familywatchlist.data.repository.ProfileRefreshResult
import org.seg7.familywatchlist.data.repository.RefreshAllOutcome
import org.seg7.familywatchlist.data.repository.RefreshLogRepository
import org.seg7.familywatchlist.testutil.FakeClock
import org.seg7.familywatchlist.testutil.buildInMemoryDb

/**
 * PLAN.md §5d (M15): the shared refresh guard, the log entry every run leaves behind, which
 * triggers notify, and catch-up decisions -- against an in-memory refresh log and scripted
 * refresh/notify collaborators (no network, no WorkManager).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RefreshCoordinatorTest {
    private lateinit var db: AppDatabase
    private lateinit var log: RefreshLogRepository
    private lateinit var clock: FakeClock

    // Friday 2026-10-09 19:00 UTC; the configured slot is Friday 13:00.
    private val now = ZonedDateTime.of(2026, 10, 9, 19, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()

    private var refreshCalls = 0
    private var gate: CompletableDeferred<Unit>? = null
    private var outcome = RefreshAllOutcome(
        completed = listOf(ProfileRefreshResult(1, "Kev")),
        summaries = listOf(ProfileRunSummary(1, "Kev", total = 30, newCount = 2, newTitles = listOf("A", "B"))),
    )
    private var refreshThrows: Throwable? = null
    private var master = true
    private var profileOn = true
    private var posted = ShortlistNotifier.Result.POSTED
    private var notifyCalls = 0

    @Before
    fun setUp() {
        db = buildInMemoryDb()
        clock = FakeClock(now)
        log = RefreshLogRepository(db.refreshLogDao(), clock)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun coordinator() = RefreshCoordinator(
        log = log,
        clock = clock,
        scope = CoroutineScope(Dispatchers.Unconfined),
        schedule = { DayOfWeek.FRIDAY to 13 },
        region = { "GB" },
        refreshAll = {
            refreshCalls++
            gate?.await()
            refreshThrows?.let { throw it }
            outcome
        },
        invalidateDiscover = {},
        notificationsMasterEnabled = { master },
        profileNotificationEnabled = { profileOn },
        postNotification = { notifyCalls++; posted },
        zone = ZoneOffset.UTC,
        finishedBannerMillis = 10,
    )

    @Test
    fun `a scheduled run is logged with its diff and posts the notification`() = runBlocking {
        val result = coordinator().run(RefreshTrigger.SCHEDULED)!!

        assertEquals(RefreshOutcome.SUCCESS, result.outcome)
        val row = db.refreshLogDao().getAll().single()
        assertEquals(RefreshTrigger.SCHEDULED, row.trigger)
        assertEquals(RefreshOutcome.SUCCESS, row.outcome)
        assertNotNull(row.finishedAt)
        assertEquals("Posted for Kev", row.notificationStatus)
        assertEquals(1, notifyCalls)
        val run = log.observeRuns().first().single()
        assertEquals(2, run.profiles.single().newCount)
        assertEquals(listOf("A", "B"), run.profiles.single().newTitles)
    }

    @Test
    fun `scheduled run with the master toggle off logs why it was suppressed and never calls the notifier`() = runBlocking {
        master = false
        coordinator().run(RefreshTrigger.SCHEDULED)

        assertEquals("Not sent: notifications are turned off in Settings", db.refreshLogDao().getAll().single().notificationStatus)
        assertEquals(0, notifyCalls)
    }

    @Test
    fun `scheduled run with the profile toggle off logs it`() = runBlocking {
        profileOn = false
        coordinator().run(RefreshTrigger.SCHEDULED)

        assertEquals("Not sent: notifications are off for Kev", db.refreshLogDao().getAll().single().notificationStatus)
        assertEquals(0, notifyCalls)
    }

    @Test
    fun `scheduled run without the OS permission logs it`() = runBlocking {
        posted = ShortlistNotifier.Result.PERMISSION_DENIED
        coordinator().run(RefreshTrigger.SCHEDULED)

        assertEquals("Not sent: Android notification permission was denied", db.refreshLogDao().getAll().single().notificationStatus)
    }

    @Test
    fun `catch-up and manual runs refresh but never notify`() = runBlocking {
        val c = coordinator()
        c.run(RefreshTrigger.CATCH_UP)
        c.run(RefreshTrigger.MANUAL)

        assertEquals(0, notifyCalls)
        assertTrue(db.refreshLogDao().getAll().all { it.notificationStatus!!.startsWith("Not sent") })
        assertEquals(2, refreshCalls)
    }

    @Test
    fun `a thrown refresh is logged as failed with its reason`() = runBlocking {
        refreshThrows = IllegalStateException("TMDB unreachable")
        val result = coordinator().run(RefreshTrigger.SCHEDULED)!!

        assertEquals(RefreshOutcome.FAILED, result.outcome)
        val row = db.refreshLogDao().getAll().single()
        assertEquals("TMDB unreachable", row.reason)
        assertEquals(0, notifyCalls)
    }

    @Test
    fun `some profiles failing is a partial run and still notifies for the ones that finished`() = runBlocking {
        outcome = RefreshAllOutcome(
            completed = listOf(ProfileRefreshResult(1, "Kev")),
            summaries = listOf(
                ProfileRunSummary(1, "Kev", total = 30),
                ProfileRunSummary(2, "Sam", status = ProfileRunSummary.STATUS_FAILED, error = "boom"),
            ),
        )
        val result = coordinator().run(RefreshTrigger.SCHEDULED)!!

        assertEquals(RefreshOutcome.PARTIAL, result.outcome)
        assertEquals("1 of 2 profiles failed: boom", db.refreshLogDao().getAll().single().reason)
        assertEquals(1, notifyCalls)
    }

    @Test
    fun `never two refreshes at once - a second manual or catch-up request yields while one is running`() = runBlocking {
        gate = CompletableDeferred()
        val c = coordinator()
        val first = async(Dispatchers.Default) { c.run(RefreshTrigger.CATCH_UP) }
        withTimeout(5_000) { while (!c.isRunning || refreshCalls == 0) kotlinx.coroutines.yield() }

        assertNull(c.run(RefreshTrigger.MANUAL))
        assertNull(c.run(RefreshTrigger.CATCH_UP))
        assertNull(c.runIfIdle { "recompute" })
        assertEquals(1, refreshCalls)

        gate!!.complete(Unit)
        assertEquals(RefreshOutcome.SUCCESS, first.await()!!.outcome)
        assertFalse(c.isRunning)
        assertEquals("recompute", c.runIfIdle { "recompute" })
        assertEquals(1, db.refreshLogDao().getAll().size)
    }

    @Test
    fun `the scheduled run waits for an in-flight catch-up instead of skipping`() = runBlocking {
        gate = CompletableDeferred()
        val c = coordinator()
        val catchUp = async(Dispatchers.Default) { c.run(RefreshTrigger.CATCH_UP) }
        withTimeout(5_000) { while (refreshCalls == 0) kotlinx.coroutines.yield() }
        val scheduled = async(Dispatchers.Default) { c.run(RefreshTrigger.SCHEDULED) }

        gate!!.complete(Unit)
        assertNotNull(catchUp.await())
        assertEquals(RefreshOutcome.SUCCESS, scheduled.await()!!.outcome)
        assertEquals(listOf(RefreshTrigger.SCHEDULED, RefreshTrigger.CATCH_UP), db.refreshLogDao().getAll().map { it.trigger })
        assertEquals(1, notifyCalls)
    }

    @Test
    fun `a run left RUNNING by a dead process is closed out as failed when the next run starts`() = runBlocking {
        db.refreshLogDao().insert(
            RefreshLogEntity(startedAt = 5, trigger = RefreshTrigger.SCHEDULED, outcome = RefreshOutcome.RUNNING),
        )
        coordinator().run(RefreshTrigger.MANUAL)

        val rows = db.refreshLogDao().getAll()
        val stale = rows.single { it.startedAt == 5L }
        assertEquals(RefreshOutcome.FAILED, stale.outcome)
        assertEquals("Interrupted before finishing", stale.reason)
    }

    @Test
    fun `maybeCatchUp runs when the Friday slot was missed`() = runBlocking {
        assertTrue(coordinator().catchUpIfDue())

        assertEquals(1, refreshCalls)
        assertEquals(RefreshTrigger.CATCH_UP, db.refreshLogDao().getAll().single().trigger)
    }

    @Test
    fun `maybeCatchUp does nothing once a refresh succeeded after the slot`() = runBlocking {
        val c = coordinator()
        c.run(RefreshTrigger.MANUAL) // succeeds "now", after Friday 13:00
        refreshCalls = 0

        c.catchUpIfDue()

        assertEquals(0, refreshCalls)
        assertEquals(1, db.refreshLogDao().getAll().size)
    }

    @Test
    fun `maybeCatchUp does not hammer a failing refresh on every app open`() = runBlocking {
        refreshThrows = IllegalStateException("offline")
        val c = coordinator()
        c.catchUpIfDue()
        c.catchUpIfDue()
        c.catchUpIfDue()

        assertEquals(1, refreshCalls)
        // Half an hour later it may try again.
        clock.advanceBy(31 * 60 * 1000L)
        c.catchUpIfDue()
        assertEquals(2, refreshCalls)
    }

    @Test
    fun `classify - none failed is success, all failed is failed, mixed is partial`() {
        fun ok(id: Long) = ProfileRunSummary(id, "P$id")
        fun bad(id: Long) = ProfileRunSummary(id, "P$id", status = ProfileRunSummary.STATUS_FAILED, error = "e")
        assertEquals(RefreshOutcome.SUCCESS, RefreshOutcomes.classify(emptyList()).first)
        assertEquals(RefreshOutcome.SUCCESS, RefreshOutcomes.classify(listOf(ok(1), ProfileRunSummary(2, "c", status = ProfileRunSummary.STATUS_COLD_START))).first)
        assertEquals(RefreshOutcome.FAILED, RefreshOutcomes.classify(listOf(bad(1), bad(2))).first)
        assertEquals(RefreshOutcome.PARTIAL, RefreshOutcomes.classify(listOf(ok(1), bad(2))).first)
    }
}
