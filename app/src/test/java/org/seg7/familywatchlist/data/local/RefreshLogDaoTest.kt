package org.seg7.familywatchlist.data.local

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.seg7.familywatchlist.data.local.dao.RefreshLogDao
import org.seg7.familywatchlist.data.local.entity.RefreshLogEntity
import org.seg7.familywatchlist.data.local.entity.RefreshOutcome
import org.seg7.familywatchlist.data.local.entity.RefreshTrigger
import org.seg7.familywatchlist.testutil.buildInMemoryDb

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RefreshLogDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: RefreshLogDao

    @Before
    fun setUp() {
        db = buildInMemoryDb()
        dao = db.refreshLogDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun row(
        startedAt: Long,
        outcome: RefreshOutcome = RefreshOutcome.SUCCESS,
        trigger: RefreshTrigger = RefreshTrigger.SCHEDULED,
        finishedAt: Long? = startedAt + 10,
    ) = RefreshLogEntity(startedAt = startedAt, finishedAt = finishedAt, trigger = trigger, outcome = outcome)

    @Test
    fun `observeAll is newest first and round-trips every field`() = runTest {
        dao.insert(row(100))
        dao.insert(
            RefreshLogEntity(
                startedAt = 300, finishedAt = 350, trigger = RefreshTrigger.CATCH_UP, outcome = RefreshOutcome.PARTIAL,
                reason = "1 of 2 profiles failed", summaryJson = "[{\"x\":1}]", notificationStatus = "Not sent: off",
            )
        )
        dao.insert(row(200))

        val all = dao.observeAll().first()

        assertEquals(listOf(300L, 200L, 100L), all.map { it.startedAt })
        val top = all.first()
        assertEquals(RefreshTrigger.CATCH_UP, top.trigger)
        assertEquals(RefreshOutcome.PARTIAL, top.outcome)
        assertEquals("1 of 2 profiles failed", top.reason)
        assertEquals("[{\"x\":1}]", top.summaryJson)
        assertEquals("Not sent: off", top.notificationStatus)
    }

    @Test
    fun `lastSuccessfulFinishedAt counts SUCCESS and PARTIAL but not FAILED or RUNNING`() = runTest {
        assertNull(dao.lastSuccessfulFinishedAt())
        dao.insert(row(100, RefreshOutcome.SUCCESS, finishedAt = 110))
        dao.insert(row(200, RefreshOutcome.PARTIAL, finishedAt = 260))
        dao.insert(row(300, RefreshOutcome.FAILED, finishedAt = 310))
        dao.insert(row(400, RefreshOutcome.RUNNING, finishedAt = null))

        assertEquals(260L, dao.lastSuccessfulFinishedAt())
        assertEquals(400L, dao.lastStartedAt())
    }

    @Test
    fun `failInterrupted closes only RUNNING rows`() = runTest {
        dao.insert(row(100, RefreshOutcome.SUCCESS))
        val runningId = dao.insert(row(200, RefreshOutcome.RUNNING, finishedAt = null))

        dao.failInterrupted()

        val running = dao.getById(runningId)!!
        assertEquals(RefreshOutcome.FAILED, running.outcome)
        assertEquals("Interrupted before finishing", running.reason)
        assertEquals(RefreshOutcome.SUCCESS, dao.getAll().last().outcome)
    }

    @Test
    fun `pruneToLatest keeps only the newest rows`() = runTest {
        (1..25).forEach { dao.insert(row(it * 100L)) }

        dao.pruneToLatest(RefreshLogDao.KEEP_LATEST)

        val remaining = dao.getAll()
        assertEquals(20, remaining.size)
        assertEquals(2500L, remaining.first().startedAt)
        assertEquals(600L, remaining.last().startedAt)
    }

    @Test
    fun `update rewrites a row in place`() = runTest {
        val id = dao.insert(row(100, RefreshOutcome.RUNNING, finishedAt = null))
        dao.update(dao.getById(id)!!.copy(outcome = RefreshOutcome.SUCCESS, finishedAt = 150))

        assertEquals(RefreshOutcome.SUCCESS, dao.getById(id)!!.outcome)
        assertEquals(150L, dao.observeLastFinishedAt().first())
    }
}
