package org.seg7.familywatchlist.data.repository

import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.seg7.familywatchlist.data.local.AppDatabase
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.ProfileEntity
import org.seg7.familywatchlist.data.local.entity.WatchlistEntryEntity
import org.seg7.familywatchlist.data.local.entity.WatchlistState
import org.seg7.familywatchlist.testutil.buildInMemoryDb

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WatchEventRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: WatchEventRepository

    @Before
    fun setUp() {
        db = buildInMemoryDb()
        repo = WatchEventRepository(db.watchEventDao(), db.watchlistDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `logging a watch flips a matching ACTIVE watchlist entry to WATCHED`() = runTest {
        val profileId = db.profileDao().insert(ProfileEntity(name = "Kev", avatarKey = "a", ageRatingCap = null, createdAt = 0L))
        db.watchlistDao().upsert(
            WatchlistEntryEntity(tmdbId = 38700, mediaType = MediaType.MOVIE, addedByProfileId = profileId, addedAt = 0L, state = WatchlistState.ACTIVE)
        )

        repo.logWatch(38700, MediaType.MOVIE, LocalDate.of(2026, 8, 16), note = null, profileIds = listOf(profileId))

        val entry = db.watchlistDao().get(38700, MediaType.MOVIE)
        assertEquals(WatchlistState.WATCHED, entry?.state)
    }

    @Test
    fun `logging a watch for a title not on the list does not create a watchlist entry`() = runTest {
        val profileId = db.profileDao().insert(ProfileEntity(name = "Kev", avatarKey = "a", ageRatingCap = null, createdAt = 0L))

        repo.logWatch(999, MediaType.MOVIE, LocalDate.of(2026, 8, 16), note = null, profileIds = listOf(profileId))

        assertEquals(null, db.watchlistDao().get(999, MediaType.MOVIE))
    }

    @Test
    fun `a REMOVED watchlist entry is not resurrected to WATCHED`() = runTest {
        val profileId = db.profileDao().insert(ProfileEntity(name = "Kev", avatarKey = "a", ageRatingCap = null, createdAt = 0L))
        db.watchlistDao().upsert(
            WatchlistEntryEntity(tmdbId = 1, mediaType = MediaType.MOVIE, addedByProfileId = profileId, addedAt = 0L, state = WatchlistState.REMOVED)
        )

        repo.logWatch(1, MediaType.MOVIE, LocalDate.of(2026, 8, 16), note = null, profileIds = listOf(profileId))

        assertEquals(WatchlistState.REMOVED, db.watchlistDao().get(1, MediaType.MOVIE)?.state)
    }
}
