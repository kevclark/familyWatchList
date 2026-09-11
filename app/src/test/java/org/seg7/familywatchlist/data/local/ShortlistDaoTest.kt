package org.seg7.familywatchlist.data.local

import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.ShortlistEntryEntity
import org.seg7.familywatchlist.data.local.entity.ShortlistState
import org.seg7.familywatchlist.testutil.buildInMemoryDb

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShortlistDaoTest {
    private lateinit var db: AppDatabase
    private val weekStart = LocalDate.of(2026, 8, 17)

    @Before
    fun setUp() {
        db = buildInMemoryDb()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `observeForScope orders by score descending`() = runTest {
        val dao = db.shortlistDao()
        dao.upsertAll(
            listOf(
                ShortlistEntryEntity(weekStart, "1", 1, MediaType.MOVIE, score = 0.4, reasons = "[]", state = ShortlistState.SUGGESTED),
                ShortlistEntryEntity(weekStart, "1", 2, MediaType.MOVIE, score = 0.9, reasons = "[]", state = ShortlistState.SUGGESTED),
            )
        )

        val entries = dao.observeForScope(weekStart, "1").first()

        assertEquals(listOf(2, 1), entries.map { it.tmdbId })
    }

    @Test
    fun `family scope is separate from per-profile scope`() = runTest {
        val dao = db.shortlistDao()
        dao.upsertAll(
            listOf(
                ShortlistEntryEntity(weekStart, "1", 1, MediaType.MOVIE, score = 0.5, reasons = "[]", state = ShortlistState.SUGGESTED),
                ShortlistEntryEntity(weekStart, "FAMILY", 1, MediaType.MOVIE, score = 0.5, reasons = "[]", state = ShortlistState.SUGGESTED),
            )
        )

        assertEquals(1, dao.observeForScope(weekStart, "1").first().size)
        assertEquals(1, dao.observeForScope(weekStart, "FAMILY").first().size)
    }

    @Test
    fun `updateState marks a dismissal`() = runTest {
        val dao = db.shortlistDao()
        dao.upsertAll(
            listOf(ShortlistEntryEntity(weekStart, "1", 1, MediaType.MOVIE, score = 0.5, reasons = "[]", state = ShortlistState.SUGGESTED))
        )

        dao.updateState(weekStart, "1", 1, MediaType.MOVIE, ShortlistState.DISMISSED)

        assertEquals(ShortlistState.DISMISSED, dao.observeForScope(weekStart, "1").first().first().state)
    }

    /**
     * PLAN.md §4c (M13 fix 1): [org.seg7.familywatchlist.data.local.dao.ShortlistDao.getDismissedForScope]
     * has no `weekStart` filter at all — every DISMISSED row for a scope, across every week —
     * unlike [ShortlistDao.getForScope]'s old current-week-only read. Proven against a DISMISSED
     * row from a week other than [weekStart].
     */
    @Test
    fun `getDismissedForScope returns DISMISSED rows across every weekStart for a scope`() = runTest {
        val dao = db.shortlistDao()
        val olderWeek = weekStart.minusWeeks(3)
        dao.upsertAll(
            listOf(ShortlistEntryEntity(olderWeek, "1", 1, MediaType.MOVIE, score = 0.0, reasons = "[]", state = ShortlistState.DISMISSED))
        )

        val dismissed = dao.getDismissedForScope("1")

        assertEquals(listOf(1), dismissed.map { it.tmdbId })
    }

    /**
     * PLAN.md §4c (M13 fix 1): [getDismissedForScope] must only ever return DISMISSED rows —
     * SUGGESTED and WATCHED rows for the same scope must never leak into the suppression set
     * (or, per M13 fix 3, into the affinity vector's dismissal signal).
     */
    @Test
    fun `getDismissedForScope excludes SUGGESTED and WATCHED rows for the same scope`() = runTest {
        val dao = db.shortlistDao()
        dao.upsertAll(
            listOf(
                ShortlistEntryEntity(weekStart, "1", 1, MediaType.MOVIE, score = 0.0, reasons = "[]", state = ShortlistState.DISMISSED),
                ShortlistEntryEntity(weekStart, "1", 2, MediaType.MOVIE, score = 0.5, reasons = "[]", state = ShortlistState.SUGGESTED),
                ShortlistEntryEntity(weekStart, "1", 3, MediaType.MOVIE, score = 0.0, reasons = "[]", state = ShortlistState.WATCHED),
            )
        )

        val dismissed = dao.getDismissedForScope("1")

        assertEquals(listOf(1), dismissed.map { it.tmdbId })
    }

    @Test
    fun `deleteOlderThan clears past weeks only`() = runTest {
        val dao = db.shortlistDao()
        val oldWeek = weekStart.minusWeeks(2)
        dao.upsertAll(
            listOf(
                ShortlistEntryEntity(oldWeek, "1", 1, MediaType.MOVIE, score = 0.5, reasons = "[]", state = ShortlistState.SUGGESTED),
                ShortlistEntryEntity(weekStart, "1", 2, MediaType.MOVIE, score = 0.5, reasons = "[]", state = ShortlistState.SUGGESTED),
            )
        )

        dao.deleteOlderThan(weekStart)

        assertEquals(0, dao.observeForScope(oldWeek, "1").first().size)
        assertEquals(1, dao.observeForScope(weekStart, "1").first().size)
    }
}
