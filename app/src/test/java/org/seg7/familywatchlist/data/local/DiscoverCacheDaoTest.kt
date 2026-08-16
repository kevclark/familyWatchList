package org.seg7.familywatchlist.data.local

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.seg7.familywatchlist.data.local.entity.DiscoverCacheEntity
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.testutil.buildInMemoryDb

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiscoverCacheDaoTest {
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
    fun `fetchedAtForQuery is null before anything is cached`() = runTest {
        assertNull(db.discoverCacheDao().fetchedAtForQuery("abc"))
    }

    @Test
    fun `replaceForQuery swaps the result set and preserves order`() = runTest {
        val dao = db.discoverCacheDao()
        dao.replaceForQuery(
            "abc",
            listOf(
                DiscoverCacheEntity("abc", 1, MediaType.MOVIE, ord = 0, fetchedAt = 100L),
                DiscoverCacheEntity("abc", 2, MediaType.MOVIE, ord = 1, fetchedAt = 100L),
            )
        )

        dao.replaceForQuery(
            "abc",
            listOf(DiscoverCacheEntity("abc", 3, MediaType.MOVIE, ord = 0, fetchedAt = 200L))
        )

        val rows = dao.getForQuery("abc")
        assertEquals(listOf(3), rows.map { it.tmdbId })
        assertEquals(200L, dao.fetchedAtForQuery("abc"))
    }

    @Test
    fun `different query hashes do not collide`() = runTest {
        val dao = db.discoverCacheDao()
        dao.replaceForQuery("abc", listOf(DiscoverCacheEntity("abc", 1, MediaType.MOVIE, 0, 100L)))
        dao.replaceForQuery("xyz", listOf(DiscoverCacheEntity("xyz", 2, MediaType.MOVIE, 0, 100L)))

        assertEquals(1, dao.getForQuery("abc").size)
        assertEquals(1, dao.getForQuery("xyz").size)
    }
}
