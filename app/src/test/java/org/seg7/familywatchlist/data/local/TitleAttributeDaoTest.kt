package org.seg7.familywatchlist.data.local

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.seg7.familywatchlist.data.local.entity.AttrType
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.TitleAttributeEntity
import org.seg7.familywatchlist.testutil.buildInMemoryDb

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TitleAttributeDaoTest {
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
    fun `replaceForTitle swaps the full attribute set`() = runTest {
        val dao = db.titleAttributeDao()
        dao.replaceForTitle(
            1, MediaType.MOVIE,
            listOf(TitleAttributeEntity(1, MediaType.MOVIE, AttrType.GENRE, 12, "Adventure", null))
        )

        dao.replaceForTitle(
            1, MediaType.MOVIE,
            listOf(
                TitleAttributeEntity(1, MediaType.MOVIE, AttrType.GENRE, 35, "Comedy", null),
                TitleAttributeEntity(1, MediaType.MOVIE, AttrType.CAST, 500, "Hugh Bonneville", 0),
            )
        )

        val rows = dao.getForTitle(1, MediaType.MOVIE)
        assertEquals(2, rows.size)
        assertTrue(rows.none { it.name == "Adventure" })
        assertTrue(rows.any { it.name == "Comedy" })
    }

    @Test
    fun `attributes are scoped per tmdbId and mediaType`() = runTest {
        val dao = db.titleAttributeDao()
        dao.upsertAll(listOf(TitleAttributeEntity(1, MediaType.MOVIE, AttrType.GENRE, 12, "Adventure", null)))
        dao.upsertAll(listOf(TitleAttributeEntity(1, MediaType.TV, AttrType.GENRE, 10759, "Action & Adventure", null)))

        assertEquals(1, dao.getForTitle(1, MediaType.MOVIE).size)
        assertEquals(1, dao.getForTitle(1, MediaType.TV).size)
    }

    @Test
    fun `cast rows preserve billing order`() = runTest {
        val dao = db.titleAttributeDao()
        dao.upsertAll(
            listOf(
                TitleAttributeEntity(1, MediaType.MOVIE, AttrType.CAST, 2, "Second Billed", 1),
                TitleAttributeEntity(1, MediaType.MOVIE, AttrType.CAST, 1, "Top Billed", 0),
            )
        )

        val rows = dao.getForTitle(1, MediaType.MOVIE)

        assertEquals(listOf("Top Billed", "Second Billed"), rows.map { it.name })
    }
}
