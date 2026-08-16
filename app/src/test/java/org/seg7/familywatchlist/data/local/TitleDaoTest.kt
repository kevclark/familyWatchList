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
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.TitleEntity
import org.seg7.familywatchlist.testutil.buildInMemoryDb

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TitleDaoTest {
    private lateinit var db: AppDatabase

    private fun title(tmdbId: Int = 1, mediaType: MediaType = MediaType.MOVIE, fetchedAt: Long = 100L) = TitleEntity(
        tmdbId = tmdbId,
        mediaType = mediaType,
        title = "Paddington",
        year = 2014,
        posterPath = "/poster.jpg",
        backdropPath = "/backdrop.jpg",
        overview = "A bear in London",
        runtimeMin = 95,
        certification = "PG",
        voteAverage = 7.2,
        popularity = 33.1,
        fetchedAt = fetchedAt,
    )

    @Before
    fun setUp() {
        db = buildInMemoryDb()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `same tmdbId as movie and tv are distinct rows`() = runTest {
        db.titleDao().upsert(title(tmdbId = 1, mediaType = MediaType.MOVIE))
        db.titleDao().upsert(title(tmdbId = 1, mediaType = MediaType.TV))

        assertEquals(MediaType.MOVIE, db.titleDao().get(1, MediaType.MOVIE)?.mediaType)
        assertEquals(MediaType.TV, db.titleDao().get(1, MediaType.TV)?.mediaType)
    }

    @Test
    fun `upsert on existing composite key replaces the row`() = runTest {
        db.titleDao().upsert(title(fetchedAt = 100L))
        db.titleDao().upsert(title(fetchedAt = 200L).copy(voteAverage = 9.0))

        val loaded = db.titleDao().get(1, MediaType.MOVIE)

        assertEquals(200L, loaded?.fetchedAt)
        assertEquals(9.0, loaded?.voteAverage)
    }

    @Test
    fun `get for missing key returns null`() = runTest {
        assertNull(db.titleDao().get(999, MediaType.MOVIE))
    }

    @Test
    fun `getByIds filters by media type`() = runTest {
        db.titleDao().upsertAll(
            listOf(
                title(tmdbId = 1, mediaType = MediaType.MOVIE),
                title(tmdbId = 2, mediaType = MediaType.MOVIE),
                title(tmdbId = 1, mediaType = MediaType.TV),
            )
        )

        val movies = db.titleDao().getByIds(listOf(1, 2), MediaType.MOVIE)

        assertEquals(2, movies.size)
        assertEquals(setOf(MediaType.MOVIE), movies.map { it.mediaType }.toSet())
    }
}
