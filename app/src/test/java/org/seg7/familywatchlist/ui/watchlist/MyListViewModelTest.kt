package org.seg7.familywatchlist.ui.watchlist

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.seg7.familywatchlist.data.local.AppDatabase
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.TitleEntity
import org.seg7.familywatchlist.data.local.entity.WatchlistState
import org.seg7.familywatchlist.data.recommend.FamilyBlend
import org.seg7.familywatchlist.data.remote.TmdbClient
import org.seg7.familywatchlist.data.repository.DiscoverRepository
import org.seg7.familywatchlist.data.repository.FamilyProfileRepository
import org.seg7.familywatchlist.data.repository.ProfileRepository
import org.seg7.familywatchlist.data.repository.ProfileSlidersRepository
import org.seg7.familywatchlist.data.repository.ProviderRepository
import org.seg7.familywatchlist.data.repository.RecommendationRepository
import org.seg7.familywatchlist.data.repository.TitleRepository
import org.seg7.familywatchlist.data.repository.UserPreferencesRepository
import org.seg7.familywatchlist.data.repository.WatchlistRepository
import org.seg7.familywatchlist.testutil.FakeClock
import org.seg7.familywatchlist.testutil.MainDispatcherRule
import org.seg7.familywatchlist.testutil.buildInMemoryDb

/**
 * PLAN.md §2/§5: the Want-to-Watch list is one *shared* family list tagged with who added each
 * title, and the "added by me" toggle is a view over it — never a second list. These tests pin
 * that distinction, plus the added-by attribution the My List screen renders.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MyListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var db: AppDatabase
    private lateinit var server: MockWebServer
    private lateinit var watchlistRepository: WatchlistRepository
    private lateinit var profileRepository: ProfileRepository
    private lateinit var userPreferencesRepository: UserPreferencesRepository
    private lateinit var recommendationRepository: RecommendationRepository
    private lateinit var familyProfileRepository: FamilyProfileRepository
    private lateinit var clock: FakeClock

    private var kevId = 0L
    private var samId = 0L

    @Before
    fun setUp() = runTest {
        db = buildInMemoryDb()
        server = MockWebServer()
        server.start()
        clock = FakeClock(startMillis = 1_000L)
        watchlistRepository = WatchlistRepository(db.watchlistDao(), clock)
        profileRepository = ProfileRepository(db.profileDao(), clock)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        userPreferencesRepository = UserPreferencesRepository(
            PreferenceDataStoreFactory.create(produceFile = { context.preferencesDataStoreFile("mylist_vm_prefs_${System.nanoTime()}") }),
        )
        val api = TmdbClient.create(baseUrl = server.url("/").toString(), accessToken = { "t" })
        val discoverRepository = DiscoverRepository(db.discoverCacheDao(), db.titleDao(), api, clock)
        val providerRepository = ProviderRepository(db.providerDao(), api, discoverRepository)
        val titleRepository = TitleRepository(db.titleDao(), db.titleAttributeDao(), db.providerAvailabilityDao(), api, clock)
        familyProfileRepository = FamilyProfileRepository(db.familyProfileDao(), db.profileDao(), clock)
        recommendationRepository = RecommendationRepository(
            watchEventDao = db.watchEventDao(),
            ratingDao = db.ratingDao(),
            watchlistDao = db.watchlistDao(),
            titleAttributeDao = db.titleAttributeDao(),
            titleRepository = titleRepository,
            discoverRepository = discoverRepository,
            providerRepository = providerRepository,
            profileRepository = profileRepository,
            profileSlidersRepository = ProfileSlidersRepository(db.profileSlidersDao()),
            familyProfileRepository = familyProfileRepository,
            shortlistDao = db.shortlistDao(),
            clock = clock,
        )

        kevId = profileRepository.addProfile("Kev", "INITIAL|7C5C4A|", null).getOrThrow()
        samId = profileRepository.addProfile("Sam", "INITIAL|4A6357|", null).getOrThrow()

        listOf(38700 to "Paddington", 12345 to "Arrival").forEach { (id, name) ->
            db.titleDao().upsert(
                TitleEntity(
                    tmdbId = id, mediaType = MediaType.MOVIE, title = name, year = 2014,
                    posterPath = "/p.jpg", backdropPath = null, overview = null, runtimeMin = null,
                    certification = null, voteAverage = null, popularity = null,
                    trailerKey = null, fetchedAt = 1_000L,
                )
            )
        }
    }

    @After
    fun tearDown() {
        server.close()
        db.close()
    }

    private fun viewModel(
        watchlist: WatchlistRepository = watchlistRepository,
        activeProfileId: Long = kevId,
    ) = MyListViewModel(
        watchlist, profileRepository, activeProfileId, userPreferencesRepository, recommendationRepository,
        familyProfileRepository,
    )

    @Test
    fun `the list is shared - it shows titles added by anyone by default`() = runTest {
        watchlistRepository.add(38700, MediaType.MOVIE, kevId)
        clock.advanceBy(1_000)
        watchlistRepository.add(12345, MediaType.MOVIE, samId)

        val state = viewModel().uiState.first { it.rows.size == 2 }
        assertEquals(setOf("Paddington", "Arrival"), state.rows.map { it.item.title }.toSet())
    }

    @Test
    fun `each row is attributed to the profile that added it`() = runTest {
        watchlistRepository.add(38700, MediaType.MOVIE, samId)

        val row = viewModel().uiState.first { it.rows.isNotEmpty() }.rows.first()
        assertEquals("Sam", row.addedBy?.name)
    }

    /**
     * PLAN.md §4b (M3k): Family isn't a row in `profiles`, so the row-resolution lookup used to
     * miss it entirely (blank/generic "Added by" for items Family itself added) — mirrors
     * HistoryViewModel's `familyFilterOption` fix from M3j. Real-profile resolution (Sam, above)
     * must stay unaffected by threading this in.
     */
    @Test
    fun `an item added by Family resolves Family's own name and avatar, once Family exists`() = runTest {
        familyProfileRepository.save("Family", "avatar", listOf(kevId, samId)).getOrThrow()
        watchlistRepository.add(38700, MediaType.MOVIE, org.seg7.familywatchlist.data.local.entity.FAMILY_PROFILE_SENTINEL_ID)

        val row = viewModel().uiState.first { it.rows.isNotEmpty() }.rows.first()

        assertEquals("Family", row.addedBy?.name)
        assertEquals(org.seg7.familywatchlist.data.local.entity.FAMILY_PROFILE_SENTINEL_ID, row.addedBy?.id)
        assertEquals("avatar", row.addedBy?.avatarKey)
    }

    @Test
    fun `'added by me' filters to the active profile without touching the shared list`() = runTest {
        watchlistRepository.add(38700, MediaType.MOVIE, kevId)
        clock.advanceBy(1_000)
        watchlistRepository.add(12345, MediaType.MOVIE, samId)
        val vm = viewModel()
        vm.uiState.first { it.rows.size == 2 }

        vm.setMineOnly(true)

        val mine = vm.uiState.first { it.mineOnly }
        assertEquals(listOf("Paddington"), mine.rows.map { it.item.title })
        // The underlying list is untouched — both entries are still ACTIVE in the database.
        assertEquals(2, db.watchlistDao().observeByState(WatchlistState.ACTIVE).first().size)
    }

    /**
     * PLAN.md §4b (M3j): Family is now symmetric with any individual profile for "added by me" —
     * it can genuinely own watchlist entries. `mineOnly` filtering already works purely off
     * [activeProfileId] as a `Long` comparison, so this proves it holds for the Family sentinel
     * with no code change needed to [MyListViewModel] itself.
     */
    @Test
    fun `'added by me' filters correctly when Family is the active profile`() = runTest {
        watchlistRepository.add(38700, MediaType.MOVIE, org.seg7.familywatchlist.data.local.entity.FAMILY_PROFILE_SENTINEL_ID)
        clock.advanceBy(1_000)
        watchlistRepository.add(12345, MediaType.MOVIE, kevId)
        val vm = viewModel(activeProfileId = org.seg7.familywatchlist.data.local.entity.FAMILY_PROFILE_SENTINEL_ID)
        vm.uiState.first { it.rows.size == 2 }

        vm.setMineOnly(true)

        val mine = vm.uiState.first { it.mineOnly }
        assertEquals(listOf("Paddington"), mine.rows.map { it.item.title })
    }

    @Test
    fun `newest addition comes first`() = runTest {
        watchlistRepository.add(38700, MediaType.MOVIE, kevId)
        clock.advanceBy(60_000)
        watchlistRepository.add(12345, MediaType.MOVIE, kevId)

        val state = viewModel().uiState.first { it.rows.size == 2 }
        assertEquals(listOf("Arrival", "Paddington"), state.rows.map { it.item.title })
    }

    /**
     * PLAN.md §5a's M2g refinement: a title added while available can quietly lose it later
     * (add-time gating only) — the screen needs `isAvailable` per row so it knows which cards to
     * dim. This exercises the whole path through [MyListViewModel], not just the repository.
     */
    @Test
    fun `an item that has lost availability is flagged so the screen can dim it`() = runTest {
        watchlistRepository.add(38700, MediaType.MOVIE, kevId) // Paddington
        watchlistRepository.add(12345, MediaType.MOVIE, kevId) // Arrival
        val readingRepo = WatchlistRepository(db.watchlistDao(), clock) { tmdbId, _, _ -> tmdbId != 38700 }

        val state = viewModel(readingRepo).uiState.first { it.rows.size == 2 }

        val byTitle = state.rows.associateBy { it.item.title }
        assertFalse("Paddington has lost availability and must be flagged", byTitle.getValue("Paddington").isAvailable)
        assertTrue("Arrival is still available and must not be flagged", byTitle.getValue("Arrival").isAvailable)
    }

    /**
     * PLAN.md §5b M3i item 9: an item over the *viewing* profile's age cap must resolve as
     * over-cap via the exact same [FamilyBlend.isOverCap] check the recommender/Search/Home
     * already use — proven here against [MyListUiState.ageRatingCap] rather than a second
     * mechanism. The screen combines these two into `dimmed`; this test pins the state-level
     * data the screen reads to make that combination, without needing Compose.
     */
    @Test
    fun `a capped viewer's age cap is resolved onto the state, and an over-cap item is flagged`() = runTest {
        db.titleDao().upsert(
            TitleEntity(
                tmdbId = 5555, mediaType = MediaType.MOVIE, title = "Too Old For Kev Jr", year = 2020,
                posterPath = null, backdropPath = null, overview = null, runtimeMin = null,
                certification = "15", voteAverage = null, popularity = null, trailerKey = null, fetchedAt = 1_000L,
            )
        )
        val kevJrId = profileRepository.addProfile("Kev Jr", "INITIAL|000000|", "12").getOrThrow()
        watchlistRepository.add(5555, MediaType.MOVIE, kevId)

        val state = viewModel(activeProfileId = kevJrId).uiState.first { it.rows.isNotEmpty() && it.ageRatingCap != null }

        assertEquals("12", state.ageRatingCap)
        val row = state.rows.single { it.item.tmdbId == 5555 }
        assertTrue(
            "a '15' title must be over a '12' cap",
            FamilyBlend.isOverCap(row.item.certification, state.ageRatingCap),
        )
    }

    /** PLAN.md §5b M3i item 9: an uncapped viewer (no ageRatingCap set) never flags anything over-cap. */
    @Test
    fun `an uncapped viewer never flags an item over-cap`() = runTest {
        db.titleDao().upsert(
            TitleEntity(
                tmdbId = 5555, mediaType = MediaType.MOVIE, title = "An 18", year = 2020,
                posterPath = null, backdropPath = null, overview = null, runtimeMin = null,
                certification = "18", voteAverage = null, popularity = null, trailerKey = null, fetchedAt = 1_000L,
            )
        )
        watchlistRepository.add(5555, MediaType.MOVIE, kevId) // kevId itself has no ageRatingCap

        val state = viewModel(activeProfileId = kevId).uiState.first { it.rows.isNotEmpty() }

        assertNull(state.ageRatingCap)
        val row = state.rows.single { it.item.tmdbId == 5555 }
        assertFalse(FamilyBlend.isOverCap(row.item.certification, state.ageRatingCap))
    }

    /** PLAN.md §5b M3i item 9: a title whose certification clears the viewer's cap is never flagged. */
    @Test
    fun `a title at-or-under the viewer's cap is never flagged over-cap`() = runTest {
        db.titleDao().upsert(
            TitleEntity(
                tmdbId = 5555, mediaType = MediaType.MOVIE, title = "A U", year = 2020,
                posterPath = null, backdropPath = null, overview = null, runtimeMin = null,
                certification = "U", voteAverage = null, popularity = null, trailerKey = null, fetchedAt = 1_000L,
            )
        )
        val kevJrId = profileRepository.addProfile("Kev Jr", "INITIAL|000000|", "12").getOrThrow()
        watchlistRepository.add(5555, MediaType.MOVIE, kevId)

        val state = viewModel(activeProfileId = kevJrId).uiState.first { it.rows.isNotEmpty() && it.ageRatingCap != null }

        assertEquals("12", state.ageRatingCap)
        val row = state.rows.single { it.item.tmdbId == 5555 }
        assertFalse(FamilyBlend.isOverCap(row.item.certification, state.ageRatingCap))
    }

    @Test
    fun `removing a title drops it from the list`() = runTest {
        watchlistRepository.add(38700, MediaType.MOVIE, kevId)
        val vm = viewModel()
        vm.uiState.first { it.rows.isNotEmpty() }

        vm.remove(38700, MediaType.MOVIE)

        assertTrue(vm.uiState.first { it.rows.isEmpty() }.rows.isEmpty())
        // REMOVED, not deleted — PLAN.md §2 models this as a state on the entry.
        assertEquals(WatchlistState.REMOVED, watchlistRepository.get(38700, MediaType.MOVIE)?.state)
    }

    @Test
    fun `re-adding a removed title credits whoever added it back`() = runTest {
        watchlistRepository.add(38700, MediaType.MOVIE, kevId)
        watchlistRepository.remove(38700, MediaType.MOVIE)
        clock.advanceBy(90_000)

        watchlistRepository.toggle(38700, MediaType.MOVIE, samId)

        val entry = watchlistRepository.get(38700, MediaType.MOVIE)
        assertEquals(WatchlistState.ACTIVE, entry?.state)
        assertEquals(samId, entry?.addedByProfileId)
        // The fresh timestamp matters to PLAN.md §4's recencyWeight(addedAt) watchlist signal.
        assertEquals(clock.current, entry?.addedAt)
    }
}
