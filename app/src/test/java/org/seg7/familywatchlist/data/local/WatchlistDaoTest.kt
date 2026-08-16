package org.seg7.familywatchlist.data.local

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
import org.seg7.familywatchlist.data.local.entity.WatchlistEntryEntity
import org.seg7.familywatchlist.data.local.entity.WatchlistState
import org.seg7.familywatchlist.testutil.buildInMemoryDb

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WatchlistDaoTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = buildInMemoryDb()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `markWatchedIfActive flips only ACTIVE entries`() = runTest {
        val dao = db.watchlistDao()
        dao.upsert(WatchlistEntryEntity(1, MediaType.MOVIE, addedByProfileId = 1, addedAt = 1L, state = WatchlistState.ACTIVE))
        dao.upsert(WatchlistEntryEntity(2, MediaType.MOVIE, addedByProfileId = 1, addedAt = 1L, state = WatchlistState.REMOVED))

        dao.markWatchedIfActive(1, MediaType.MOVIE)
        dao.markWatchedIfActive(2, MediaType.MOVIE)

        assertEquals(WatchlistState.WATCHED, dao.get(1, MediaType.MOVIE)?.state)
        assertEquals(WatchlistState.REMOVED, dao.get(2, MediaType.MOVIE)?.state)
    }

    @Test
    fun `observeByState filters correctly`() = runTest {
        val dao = db.watchlistDao()
        dao.upsert(WatchlistEntryEntity(1, MediaType.MOVIE, addedByProfileId = 1, addedAt = 1L, state = WatchlistState.ACTIVE))
        dao.upsert(WatchlistEntryEntity(2, MediaType.MOVIE, addedByProfileId = 1, addedAt = 1L, state = WatchlistState.WATCHED))

        val active = dao.observeByState(WatchlistState.ACTIVE).first()

        assertEquals(listOf(1), active.map { it.tmdbId })
    }

    @Test
    fun `updateState sets an explicit state`() = runTest {
        val dao = db.watchlistDao()
        dao.upsert(WatchlistEntryEntity(1, MediaType.MOVIE, addedByProfileId = 1, addedAt = 1L, state = WatchlistState.ACTIVE))

        dao.updateState(1, MediaType.MOVIE, WatchlistState.REMOVED)

        assertEquals(WatchlistState.REMOVED, dao.get(1, MediaType.MOVIE)?.state)
    }
}
