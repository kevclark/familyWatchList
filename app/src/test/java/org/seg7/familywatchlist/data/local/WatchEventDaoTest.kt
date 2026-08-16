package org.seg7.familywatchlist.data.local

import java.time.LocalDate
import kotlinx.coroutines.flow.first
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
import org.seg7.familywatchlist.data.local.entity.ProfileEntity
import org.seg7.familywatchlist.data.local.entity.WatchEventEntity
import org.seg7.familywatchlist.testutil.buildInMemoryDb

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WatchEventDaoTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = buildInMemoryDb()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun addProfile(name: String): Long =
        db.profileDao().insert(ProfileEntity(name = name, avatarKey = "a", ageRatingCap = null, createdAt = 0L))

    @Test
    fun `logWatch tags multiple profiles on one event — family night`() = runTest {
        val kev = addProfile("Kev")
        val kid = addProfile("Kid")

        val eventId = db.watchEventDao().logWatch(
            WatchEventEntity(
                tmdbId = 1,
                mediaType = MediaType.MOVIE,
                watchedAt = LocalDate.of(2026, 8, 15),
                note = "Family night",
            ),
            profileIds = listOf(kev, kid),
        )

        val taggedProfiles = db.watchEventDao().getProfileIdsForEvent(eventId)
        assertEquals(setOf(kev, kid), taggedProfiles.toSet())
    }

    @Test
    fun `observeForProfile only returns events tagged with that profile`() = runTest {
        val kev = addProfile("Kev")
        val kid = addProfile("Kid")

        db.watchEventDao().logWatch(
            WatchEventEntity(tmdbId = 1, mediaType = MediaType.MOVIE, watchedAt = LocalDate.of(2026, 8, 1), note = null),
            listOf(kev),
        )
        db.watchEventDao().logWatch(
            WatchEventEntity(tmdbId = 2, mediaType = MediaType.MOVIE, watchedAt = LocalDate.of(2026, 8, 2), note = null),
            listOf(kid),
        )

        val kevEvents = db.watchEventDao().observeForProfile(kev).first()

        assertEquals(1, kevEvents.size)
        assertEquals(1, kevEvents.first().tmdbId)
    }

    @Test
    fun `events order newest watchedAt first`() = runTest {
        val kev = addProfile("Kev")

        db.watchEventDao().logWatch(
            WatchEventEntity(tmdbId = 1, mediaType = MediaType.MOVIE, watchedAt = LocalDate.of(2026, 1, 1), note = null),
            listOf(kev),
        )
        db.watchEventDao().logWatch(
            WatchEventEntity(tmdbId = 2, mediaType = MediaType.MOVIE, watchedAt = LocalDate.of(2026, 6, 1), note = null),
            listOf(kev),
        )

        val events = db.watchEventDao().observeAll().first()

        assertEquals(listOf(2, 1), events.map { it.tmdbId })
    }

    @Test
    fun `countForProfile counts only that profile's tagged events`() = runTest {
        val kev = addProfile("Kev")
        val kid = addProfile("Kid")
        db.watchEventDao().logWatch(
            WatchEventEntity(tmdbId = 1, mediaType = MediaType.MOVIE, watchedAt = LocalDate.of(2026, 1, 1), note = null),
            listOf(kev, kid),
        )

        assertEquals(1, db.watchEventDao().countForProfile(kev))
        assertEquals(1, db.watchEventDao().countForProfile(kid))
        assertTrue(db.watchEventDao().countForProfile(999L) == 0)
    }
}
