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
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.ReviewEntity
import org.seg7.familywatchlist.testutil.buildInMemoryDb

/**
 * PLAN.md §5c (M14): [org.seg7.familywatchlist.data.local.dao.ReviewDao.replaceForTitle] mirrors
 * TitleAttributeDao.replaceForTitle's exact delete-then-upsert pattern — a refetch always
 * resupplies the full review set, and rows are scoped by (tmdbId, mediaType) just like every
 * other per-title table.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReviewDaoTest {
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
    fun `replaceForTitle swaps the full review set`() = runTest {
        val dao = db.reviewDao()
        dao.replaceForTitle(
            1, MediaType.MOVIE,
            listOf(review(reviewId = "r1", author = "Old Reviewer"))
        )

        dao.replaceForTitle(
            1, MediaType.MOVIE,
            listOf(
                review(reviewId = "r2", author = "New Reviewer"),
                review(reviewId = "r3", author = "Another New Reviewer"),
            )
        )

        val rows = dao.getForTitle(1, MediaType.MOVIE)
        assertEquals(2, rows.size)
        assertTrue(rows.none { it.author == "Old Reviewer" })
        assertTrue(rows.any { it.author == "New Reviewer" })
    }

    @Test
    fun `reviews are scoped per tmdbId and mediaType`() = runTest {
        val dao = db.reviewDao()
        dao.upsertAll(listOf(review(tmdbId = 1, mediaType = MediaType.MOVIE, reviewId = "r1")))
        dao.upsertAll(listOf(review(tmdbId = 1, mediaType = MediaType.TV, reviewId = "r1")))
        dao.upsertAll(listOf(review(tmdbId = 2, mediaType = MediaType.MOVIE, reviewId = "r1")))

        assertEquals(1, dao.getForTitle(1, MediaType.MOVIE).size)
        assertEquals(1, dao.getForTitle(1, MediaType.TV).size)
        assertEquals(1, dao.getForTitle(2, MediaType.MOVIE).size)
    }

    @Test
    fun `a review with no rating persists a null rating`() = runTest {
        val dao = db.reviewDao()
        dao.upsertAll(listOf(review(reviewId = "r1", rating = null)))

        val rows = dao.getForTitle(1, MediaType.MOVIE)
        assertEquals(null, rows.single().rating)
    }

    private fun review(
        tmdbId: Int = 1,
        mediaType: MediaType = MediaType.MOVIE,
        reviewId: String,
        author: String = "A Reviewer",
        rating: Double? = 8.0,
    ) = ReviewEntity(
        tmdbId = tmdbId,
        mediaType = mediaType,
        reviewId = reviewId,
        author = author,
        content = "A lovely bear.",
        url = "https://www.themoviedb.org/review/$reviewId",
        rating = rating,
        createdAt = "2015-01-01T00:00:00.000Z",
    )
}
