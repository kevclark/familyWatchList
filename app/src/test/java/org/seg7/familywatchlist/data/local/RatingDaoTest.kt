package org.seg7.familywatchlist.data.local

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.RatingEntity
import org.seg7.familywatchlist.data.local.entity.RatingValue
import org.seg7.familywatchlist.testutil.buildInMemoryDb

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RatingDaoTest {
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
    fun `latest write wins per PLAN md §2`() = runTest {
        val dao = db.ratingDao()
        dao.upsert(RatingEntity(profileId = 1, tmdbId = 10, mediaType = MediaType.MOVIE, value = RatingValue.NEUTRAL, ratedAt = 1L))
        dao.upsert(RatingEntity(profileId = 1, tmdbId = 10, mediaType = MediaType.MOVIE, value = RatingValue.UP, ratedAt = 2L))

        val loaded = dao.get(1, 10, MediaType.MOVIE)

        assertEquals(RatingValue.UP, loaded?.value)
        assertEquals(2L, loaded?.ratedAt)
    }

    @Test
    fun `ratings are per profile not per event`() = runTest {
        val dao = db.ratingDao()
        dao.upsert(RatingEntity(profileId = 1, tmdbId = 10, mediaType = MediaType.MOVIE, value = RatingValue.UP, ratedAt = 1L))
        dao.upsert(RatingEntity(profileId = 2, tmdbId = 10, mediaType = MediaType.MOVIE, value = RatingValue.DOWN, ratedAt = 1L))

        assertEquals(RatingValue.UP, dao.get(1, 10, MediaType.MOVIE)?.value)
        assertEquals(RatingValue.DOWN, dao.get(2, 10, MediaType.MOVIE)?.value)
    }

    @Test
    fun `getForProfile returns all of one profile's ratings`() = runTest {
        val dao = db.ratingDao()
        dao.upsert(RatingEntity(profileId = 1, tmdbId = 10, mediaType = MediaType.MOVIE, value = RatingValue.UP, ratedAt = 1L))
        dao.upsert(RatingEntity(profileId = 1, tmdbId = 20, mediaType = MediaType.TV, value = RatingValue.DOWN, ratedAt = 1L))

        assertEquals(2, dao.getForProfile(1).size)
    }
}
