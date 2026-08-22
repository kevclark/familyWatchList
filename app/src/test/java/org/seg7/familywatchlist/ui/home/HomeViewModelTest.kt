package org.seg7.familywatchlist.ui.home

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.seg7.familywatchlist.data.local.AppDatabase
import org.seg7.familywatchlist.data.local.entity.FamilyProfileEntity
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.ProfileEntity
import org.seg7.familywatchlist.data.local.entity.ProviderEntity
import org.seg7.familywatchlist.data.local.entity.RatingEntity
import org.seg7.familywatchlist.data.local.entity.RatingValue
import org.seg7.familywatchlist.data.local.entity.TitleEntity
import org.seg7.familywatchlist.data.local.entity.WatchlistState
import org.seg7.familywatchlist.data.remote.TmdbClient
import org.seg7.familywatchlist.data.repository.DiscoverRepository
import org.seg7.familywatchlist.data.repository.FAMILY_SCOPE_KEY
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
import org.seg7.familywatchlist.ui.ActiveProfile

/**
 * PLAN.md §5a's M2g refinement, exercised through Home's own "My List" carousel data: an item
 * that's since lost availability must come back flagged so the card can render dimmed, and
 * [HomeViewModel.removeFromWatchlist] must be a real, working clean-up action reachable straight
 * from that carousel — not just a details-screen detour. No subscribed providers are ever seeded
 * here, so [DiscoverRepository.discoverMovies]/`discoverTv` short-circuit to an empty list without
 * touching the network (PLAN.md §7 M2e) — this test is about the My List row alone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var db: AppDatabase
    private lateinit var server: MockWebServer
    private lateinit var clock: FakeClock
    private lateinit var discoverRepository: DiscoverRepository
    private lateinit var providerRepository: ProviderRepository
    private lateinit var userPreferencesRepository: UserPreferencesRepository
    private lateinit var recommendationRepository: RecommendationRepository
    private lateinit var titleRepository: TitleRepository
    private lateinit var profileRepository: ProfileRepository
    private lateinit var familyProfileRepository: FamilyProfileRepository

    private val profileId = 7L
    private val activeProfile = ActiveProfile.Individual(
        ProfileEntity(id = profileId, name = "P$profileId", avatarKey = "a", ageRatingCap = null, createdAt = 0),
    )

    @Before
    fun setUp() = runTest {
        db = buildInMemoryDb()
        server = MockWebServer()
        server.start()
        clock = FakeClock(startMillis = 1_000L)
        val api = TmdbClient.create(baseUrl = server.url("/").toString(), accessToken = { "t" })
        discoverRepository = DiscoverRepository(db.discoverCacheDao(), db.titleDao(), api, clock)
        providerRepository = ProviderRepository(db.providerDao(), api, discoverRepository)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        userPreferencesRepository = UserPreferencesRepository(
            PreferenceDataStoreFactory.create(produceFile = { context.preferencesDataStoreFile("home_vm_prefs_${System.nanoTime()}") }),
        )
        titleRepository = TitleRepository(db.titleDao(), db.titleAttributeDao(), db.providerAvailabilityDao(), api, clock)
        profileRepository = ProfileRepository(db.profileDao(), clock)
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

        listOf(38700 to "Spider-Man: No Way Home", 12345 to "Paddington").forEach { (id, name) ->
            db.titleDao().upsert(
                TitleEntity(
                    tmdbId = id, mediaType = MediaType.MOVIE, title = name, year = 2021,
                    posterPath = null, backdropPath = null, overview = null, runtimeMin = null,
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

    private fun viewModel(watchlistRepository: WatchlistRepository, activeProfile: ActiveProfile = this.activeProfile) =
        HomeViewModel(
            discoverRepository,
            providerRepository,
            watchlistRepository,
            userPreferencesRepository,
            recommendationRepository,
            titleRepository,
            profileRepository,
            familyProfileRepository,
            activeProfile,
        )

    @Test
    fun `an item that has lost availability is flagged so Home's My List carousel can dim it`() = runTest {
        val addingRepo = WatchlistRepository(db.watchlistDao(), clock) { _, _, _ -> true }
        addingRepo.add(38700, MediaType.MOVIE, profileId)
        addingRepo.add(12345, MediaType.MOVIE, profileId)
        val readingRepo = WatchlistRepository(db.watchlistDao(), clock) { tmdbId, _, _ -> tmdbId != 38700 }

        val state = viewModel(readingRepo).myList.first { it.size == 2 }

        val byId = state.associateBy { it.item.tmdbId }
        assertFalse("Spider-Man has lost availability and must be flagged", byId.getValue(38700).isAvailable)
        assertTrue("Paddington is still available and must not be flagged", byId.getValue(12345).isAvailable)
    }

    @Test
    fun `removeFromWatchlist actually removes the entry — the carousel's direct clean-up action`() = runTest {
        val watchlistRepository = WatchlistRepository(db.watchlistDao(), clock) { _, _, _ -> true }
        watchlistRepository.add(38700, MediaType.MOVIE, profileId)
        val vm = viewModel(watchlistRepository)
        vm.myList.first { it.isNotEmpty() }

        vm.removeFromWatchlist(38700, MediaType.MOVIE)

        assertTrue(vm.myList.first { it.isEmpty() }.isEmpty())
        assertEquals(WatchlistState.REMOVED, watchlistRepository.get(38700, MediaType.MOVIE)?.state)
    }

    /** PLAN.md §4: cold-start profiles (< 5 events) never get a personalised "For You" — the row falls back to popular-on-your-services state. */
    @Test
    fun `a cold-start profile is flagged and has no For You titles`() = runTest {
        val watchlistRepository = WatchlistRepository(db.watchlistDao(), clock) { _, _, _ -> true }

        val state = viewModel(watchlistRepository).uiState.first { !it.isLoading }

        assertTrue(state.isColdStartForYou)
        assertEquals(emptyList<TitleEntity>(), state.forYouTitles)
    }

    /**
     * PLAN.md §4's 2026-08-19 design note: the hero sources from the profile's *top-scored*
     * personalised pick, not raw popularity — proven here by pre-seeding a real persisted
     * shortlist ranked the *opposite* way to popularity and confirming the higher-scored (lower
     * popularity) title wins the hero slot. No subscribed providers and no UP ratings means
     * `HomeViewModel.refresh()`'s own `refreshProfileShortlist` call (which always fires for a
     * warm profile) computes an empty candidate pool and upserts nothing — see
     * `RecommendationRepository.gatherCandidatePool` — so it can never clobber the seeded rows.
     */
    @Test
    fun `For You is sourced from the persisted shortlist, top-scored pick first, not popularity`() = runTest {
        // 5 events -> past PLAN.md §4's cold-start threshold.
        repeat(5) { i ->
            db.watchEventDao().logWatch(
                org.seg7.familywatchlist.data.local.entity.WatchEventEntity(
                    tmdbId = 900 + i, mediaType = MediaType.MOVIE, watchedAt = java.time.LocalDate.now(), note = null,
                ),
                listOf(profileId),
            )
        }
        db.titleDao().upsertAll(
            listOf(
                TitleEntity(
                    tmdbId = 501, mediaType = MediaType.MOVIE, title = "Higher Score, Less Popular",
                    year = 2024, posterPath = null, backdropPath = null, overview = null, runtimeMin = null,
                    certification = null, voteAverage = null, popularity = 1.0, trailerKey = null, fetchedAt = clock.current,
                ),
                TitleEntity(
                    tmdbId = 502, mediaType = MediaType.MOVIE, title = "Lower Score, More Popular",
                    year = 2024, posterPath = null, backdropPath = null, overview = null, runtimeMin = null,
                    certification = null, voteAverage = null, popularity = 99.0, trailerKey = null, fetchedAt = clock.current,
                ),
            )
        )
        val weekStart = recommendationRepository.currentWeekStart()
        db.shortlistDao().upsertAll(
            listOf(
                org.seg7.familywatchlist.data.local.entity.ShortlistEntryEntity(
                    weekStart, profileId.toString(), 501, MediaType.MOVIE, score = 0.9, reasons = "[]",
                    state = org.seg7.familywatchlist.data.local.entity.ShortlistState.SUGGESTED,
                ),
                org.seg7.familywatchlist.data.local.entity.ShortlistEntryEntity(
                    weekStart, profileId.toString(), 502, MediaType.MOVIE, score = 0.5, reasons = "[]",
                    state = org.seg7.familywatchlist.data.local.entity.ShortlistState.SUGGESTED,
                ),
            )
        )

        val watchlistRepository = WatchlistRepository(db.watchlistDao(), clock) { _, _, _ -> true }
        val state = viewModel(watchlistRepository).uiState.first { it.forYouTitles.size == 2 }

        assertFalse(state.isColdStartForYou)
        assertEquals(listOf(501, 502), state.forYouTitles.map { it.tmdbId })
        assertEquals(501, state.hero?.tmdbId)
    }

    /**
     * PLAN.md §4's "Age-cap safety gap" (M3g), tightened by "Residual gap found by M3g" (M3h):
     * a cold-start profile's Popular row (and the cold-start hero fallback, which reads the same
     * [DiscoverRepository] data) previously applied **zero** age-rating filtering. Seeds three
     * already-detail-fetched titles (real certification data, matching how a title genuinely gets
     * a certification cached — discover summary payloads never carry one, see
     * [DiscoverRepositoryTest]'s "discover never clobbers a title that already has full detail data
     * cached") and confirms: over-cap excluded, at-cap survives (regression check against M3g's
     * own correctness), **unknown-cert now also excluded** — the M3h flip, specific to this one
     * call site — unlike [FamilyBlend.isOverCap]'s own "unknown != unsafe" default, which stays
     * unchanged for every other caller (see [HomeViewModel.survivesAgeCap]'s kdoc).
     */
    @Test
    fun `Popular rows for a capped profile require confirmed at-or-under-cap certification — over and unknown-cert both excluded, at-cap survives`() = runTest {
        db.titleDao().upsertAll(
            listOf(
                TitleEntity(
                    tmdbId = 1001, mediaType = MediaType.MOVIE, title = "Too Old", year = 2020,
                    posterPath = null, backdropPath = null, overview = null, runtimeMin = 100,
                    certification = "18", voteAverage = 7.0, popularity = 90.0, trailerKey = null, fetchedAt = clock.current,
                ),
                TitleEntity(
                    tmdbId = 1002, mediaType = MediaType.MOVIE, title = "Right At The Cap", year = 2020,
                    posterPath = null, backdropPath = null, overview = null, runtimeMin = 100,
                    certification = "12", voteAverage = 7.0, popularity = 80.0, trailerKey = null, fetchedAt = clock.current,
                ),
                TitleEntity(
                    tmdbId = 1003, mediaType = MediaType.MOVIE, title = "Unknown Cert", year = 2020,
                    posterPath = null, backdropPath = null, overview = null, runtimeMin = 100,
                    certification = null, voteAverage = 7.0, popularity = 70.0, trailerKey = null, fetchedAt = clock.current,
                ),
            )
        )
        db.providerDao().upsertAll(listOf(ProviderEntity(8, "Netflix", null, subscribed = true, displayPriority = 1)))
        server.enqueue(MockResponse(body = discoverMoviePageJson(listOf(1001 to "Too Old", 1002 to "Right At The Cap", 1003 to "Unknown Cert"))))
        server.enqueue(MockResponse(body = """{"page":1,"results":[],"total_pages":1,"total_results":0}"""))

        // resolveAgeRatingCap resolves against the real, persisted row (RecommendationRepository ->
        // ProfileRepository.getById) — not the ActiveProfile wrapper's own embedded snapshot — so
        // the profile must actually be added, not just constructed in memory.
        val cappedProfileId = profileRepository.addProfile("Capped Kid", "avatar", "12").getOrThrow()
        val cappedProfile = ActiveProfile.Individual(profileRepository.getById(cappedProfileId)!!)
        val watchlistRepository = WatchlistRepository(db.watchlistDao(), clock) { _, _, _ -> true }
        val state = viewModel(watchlistRepository, cappedProfile).uiState.first { it.popularMovies.isNotEmpty() }

        assertEquals(setOf(1002), state.popularMovies.map { it.tmdbId }.toSet())
        // PLAN.md §5b M3i item 5: the same resolved cap that filtered these rows is also
        // published on the state for the Home avatar badge.
        assertEquals("12", state.ageRatingCap)
    }

    /**
     * PLAN.md §4 "Residual gap found by M3g, resolved by Kev 2026-08-21" (M3h): the uncertain-
     * certification exclusion is a targeted flip for a *capped* profile only — an uncapped profile
     * ([ProfileEntity.ageRatingCap] null) must see **zero** behavioural difference from before this
     * pass. Same "Unknown Cert" (1003, `certification = null`) title as the capped-profile test
     * above, but against the default (uncapped) [activeProfile] used throughout this file — it must
     * survive exactly as it always has, proving the M3h change doesn't leak into the uncapped path.
     */
    @Test
    fun `Popular rows for an uncapped profile are unaffected by an uncertain-certification title`() = runTest {
        db.titleDao().upsertAll(
            listOf(
                TitleEntity(
                    tmdbId = 1002, mediaType = MediaType.MOVIE, title = "Right At The Cap", year = 2020,
                    posterPath = null, backdropPath = null, overview = null, runtimeMin = 100,
                    certification = "12", voteAverage = 7.0, popularity = 80.0, trailerKey = null, fetchedAt = clock.current,
                ),
                TitleEntity(
                    tmdbId = 1003, mediaType = MediaType.MOVIE, title = "Unknown Cert", year = 2020,
                    posterPath = null, backdropPath = null, overview = null, runtimeMin = 100,
                    certification = null, voteAverage = 7.0, popularity = 70.0, trailerKey = null, fetchedAt = clock.current,
                ),
            )
        )
        db.providerDao().upsertAll(listOf(ProviderEntity(8, "Netflix", null, subscribed = true, displayPriority = 1)))
        server.enqueue(MockResponse(body = discoverMoviePageJson(listOf(1002 to "Right At The Cap", 1003 to "Unknown Cert"))))
        server.enqueue(MockResponse(body = """{"page":1,"results":[],"total_pages":1,"total_results":0}"""))

        val watchlistRepository = WatchlistRepository(db.watchlistDao(), clock) { _, _, _ -> true }
        // `activeProfile` (this file's default) has `ageRatingCap = null`.
        val state = viewModel(watchlistRepository).uiState.first { it.popularMovies.isNotEmpty() }

        assertEquals(setOf(1002, 1003), state.popularMovies.map { it.tmdbId }.toSet())
        // PLAN.md §5b M3i item 5: no cap set -> nothing published for the avatar badge either.
        assertEquals(null, state.ageRatingCap)
    }

    /**
     * PLAN.md §4 "Cold-start Home treatment" (M3g): a cold-start profile's hero must never be a
     * title — [HomeUiState.hero] is null regardless of how much popular data exists — while the
     * Popular row itself still populates normally (just filtered, proven by the test above).
     */
    @Test
    fun `a cold-start profile's hero is null even though Popular data exists`() = runTest {
        db.providerDao().upsertAll(listOf(ProviderEntity(8, "Netflix", null, subscribed = true, displayPriority = 1)))
        server.enqueue(MockResponse(body = discoverMoviePageJson(listOf(38700 to "Spider-Man: No Way Home"))))
        server.enqueue(MockResponse(body = """{"page":1,"results":[],"total_pages":1,"total_results":0}"""))

        val watchlistRepository = WatchlistRepository(db.watchlistDao(), clock) { _, _, _ -> true }
        val state = viewModel(watchlistRepository).uiState.first { it.popularMovies.isNotEmpty() }

        assertTrue(state.isColdStartForYou)
        assertEquals(null, state.hero)
    }

    private fun discoverMoviePageJson(entries: List<Pair<Int, String>>): String {
        val results = entries.joinToString(",") { (id, title) ->
            """{"id": $id, "title": "$title", "poster_path": "/p.jpg", "release_date": "2020-01-01", "vote_average": 7.0, "popularity": 50.0}"""
        }
        return """{"page":1,"results":[$results],"total_pages":1,"total_results":${entries.size}}"""
    }

    /**
     * PLAN.md §7 M2f: refresh() must source region from the live preference — not the
     * compile-time GB default — and actually send it, not silently keep hitting TMDB for GB.
     */
    @Test
    fun `refresh sends the live region preference to discover, not a hardcoded GB`() = runTest {
        userPreferencesRepository.setRegion("US")
        db.providerDao().upsertAll(listOf(ProviderEntity(8, "Netflix", null, subscribed = true, displayPriority = 1)))
        server.enqueue(MockResponse(body = """{"page":1,"results":[],"total_pages":1,"total_results":0}"""))
        server.enqueue(MockResponse(body = """{"page":1,"results":[],"total_pages":1,"total_results":0}"""))

        val watchlistRepository = WatchlistRepository(db.watchlistDao(), clock) { _, _, _ -> true }
        viewModel(watchlistRepository).uiState.first { !it.isLoading }

        val movieRequest = server.takeRequest()
        val tvRequest = server.takeRequest()
        assertTrue(movieRequest.target.contains("watch_region=US"))
        assertTrue(tvRequest.target.contains("watch_region=US"))
    }

    /**
     * M3c: the who's-watching chip row's selection is what actually reaches
     * [RecommendationRepository.refreshFamilyShortlist] — proven by giving only profile B the UP
     * rating that drives a `/recommendations` hit for a specific candidate (999), then showing
     * that candidate only ever surfaces in [HomeUiState.familyNightTitles] once B is genuinely
     * part of the current selection, never for a same-size selection that excludes B. `persist =
     * false` is proven by the FAMILY scope staying empty in Room throughout — a mistaken
     * `persist = true` call would show up there.
     */
    @Test
    fun `selecting 2+ profiles triggers the ad-hoc family blend with exactly those profile IDs, and never persists`() = runTest {
        val a = profileRepository.addProfile("A", "avatar", null).getOrThrow()
        val b = profileRepository.addProfile("B", "avatar", null).getOrThrow()
        val c = profileRepository.addProfile("C", "avatar", null).getOrThrow()
        db.ratingDao().upsert(RatingEntity(b, 1, MediaType.MOVIE, RatingValue.UP, clock.current))
        server.enqueue(
            MockResponse(
                body = """
                    {"page":1,"results":[{"id":999,"title":"Family Pick","poster_path":"/p.jpg","release_date":"2026-08-01","vote_average":8.0,"vote_count":500,"popularity":50.0}],"total_pages":1,"total_results":1}
                """.trimIndent()
            )
        )
        server.enqueue(
            MockResponse(
                body = """
                    {"id":999,"title":"Family Pick","release_date":"2026-08-01","runtime":100,"vote_average":8.0,"vote_count":500,"popularity":50.0,
                     "genres":[{"id":35,"name":"Comedy"}],"credits":{"cast":[],"crew":[]},"keywords":{"keywords":[]},"videos":{"results":[]},
                     "watch/providers":{"results":{}},
                     "release_dates":{"results":[{"iso_3166_1":"GB","release_dates":[{"certification":"PG","type":3,"release_date":"2026-08-01T00:00:00.000Z"}]}]}}
                """.trimIndent()
            )
        )

        val watchlistRepository = WatchlistRepository(db.watchlistDao(), clock) { _, _, _ -> true }
        val vm = viewModel(watchlistRepository)
        vm.uiState.first { it.familyNightProfiles.size == 3 }

        // Fewer than 2 selected: the < 2 gate is a hard code-level branch (never even reaches
        // RecommendationRepository), so this holds regardless of how much time has passed.
        vm.toggleFamilyNightProfile(a)
        assertEquals(setOf(a), vm.uiState.first { it.familyNightSelectedIds == setOf(a) }.familyNightSelectedIds)
        assertEquals(0, server.requestCount)

        // A + C (neither carries the UP rating that drives the recommendation candidate, and
        // nothing is subscribed) then swap C for B — all before ever awaiting anything, so the
        // debounce coalesces every toggle here into a single compute of whatever the selection
        // is once we finally await it: {A, B}. Waiting on the *titles* actually appearing (not
        // just the selection updating) forces a real wait for refreshFamilyShortlist's full
        // round trip through B's UP rating -> /recommendations -> detail fetch -> scoring.
        vm.toggleFamilyNightProfile(c)
        vm.toggleFamilyNightProfile(c)
        vm.toggleFamilyNightProfile(b)
        val finalState = vm.uiState.first { it.familyNightTitles.isNotEmpty() }

        assertEquals(setOf(a, b), finalState.familyNightSelectedIds)
        assertEquals(listOf(999), finalState.familyNightTitles.map { it.tmdbId })
        // Exactly one /recommendations + one detail fetch — proving the transient A+C selection
        // never itself triggered a network round trip (it would have consumed one of these two
        // enqueued responses, leaving too few for the real {A, B} compute to succeed at all).
        assertEquals(2, server.requestCount)

        val weekStart = recommendationRepository.currentWeekStart()
        assertEquals(emptyList<Any>(), db.shortlistDao().getForScope(weekStart, FAMILY_SCOPE_KEY))
    }

    /**
     * PLAN.md §4b (M3j, supersedes M3d): when [org.seg7.familywatchlist.ui.ActiveProfile] is
     * [org.seg7.familywatchlist.ui.ActiveProfile.Family], Home's For You/hero must read the
     * *persisted* [FAMILY_SCOPE_KEY] shortlist — not a per-profile scope key keyed off the
     * sentinel id's raw string form (which would be "-1", matching nothing real;
     * [RecommendationRepository]'s `scopeKeyFor` resolves the sentinel to [FAMILY_SCOPE_KEY]).
     * As of M3j, Family's cold-start is the plain per-profile check against its *own* watch
     * events — no member blend involved any more — so this fixture seeds 5 events tagged directly
     * to [FAMILY_PROFILE_SENTINEL_ID] to get past that gate. No `familyProfileRepository.save`
     * call here deliberately: with no Family profile actually persisted,
     * `RecommendationRepository.refreshProfileShortlist`'s existence check finds
     * `familyProfileRepository.get() == null` and returns early without persisting anything, so
     * these directly-seeded shortlist rows are never clobbered — isolating the *read* side of the
     * wiring from the write side, which `RecommendationRepositoryTest`'s M3j additions cover
     * separately.
     */
    @Test
    fun `Family active reads the persisted FAMILY scope shortlist once it has 5+ of its own events`() = runTest {
        val weekStart = recommendationRepository.currentWeekStart()
        db.titleDao().upsert(
            TitleEntity(
                tmdbId = 777, mediaType = MediaType.MOVIE, title = "Family Pick", year = 2024,
                posterPath = null, backdropPath = null, overview = null, runtimeMin = null,
                certification = null, voteAverage = null, popularity = 1.0, trailerKey = null, fetchedAt = clock.current,
            )
        )
        db.shortlistDao().upsertAll(
            listOf(
                org.seg7.familywatchlist.data.local.entity.ShortlistEntryEntity(
                    weekStart, FAMILY_SCOPE_KEY, 777, MediaType.MOVIE, score = 0.8, reasons = "[]",
                    state = org.seg7.familywatchlist.data.local.entity.ShortlistState.SUGGESTED,
                ),
            )
        )
        // PLAN.md §4b (M3j): Family's own cold-start now depends on its own watch event count.
        repeat(5) { i ->
            db.watchEventDao().logWatch(
                org.seg7.familywatchlist.data.local.entity.WatchEventEntity(
                    tmdbId = 800 + i, mediaType = MediaType.MOVIE, watchedAt = java.time.LocalDate.now(), note = null,
                ),
                listOf(org.seg7.familywatchlist.data.local.entity.FAMILY_PROFILE_SENTINEL_ID),
            )
        }
        val family = ActiveProfile.Family(
            FamilyProfileEntity(name = "Family", avatarKey = "a", createdAt = 0),
            memberProfileIds = listOf(1L, 2L),
        )

        val watchlistRepository = WatchlistRepository(db.watchlistDao(), clock) { _, _, _ -> true }
        val state = viewModel(watchlistRepository, family).uiState.first { it.forYouTitles.isNotEmpty() }

        assertFalse("Family has 5+ of its own events, so it must not be cold-start", state.isColdStartForYou)
        assertEquals(listOf(777), state.forYouTitles.map { it.tmdbId })
        assertEquals(777, state.hero?.tmdbId)
    }

    /**
     * PLAN.md §4b (M3j): Family's cold-start no longer derives from its members at all — replaces
     * the old M3i "cold only if every curated member is individually cold" design
     * ([familyIsColdStart], removed). Proven directly: a member (B) is warm (5+ of its own
     * events), but none of those events are tagged to Family's own sentinel id, so Family itself
     * must still read as cold-start.
     */
    @Test
    fun `Family cold-start depends on its own event count, not its members'`() = runTest {
        val a = profileRepository.addProfile("A", "avatar", null).getOrThrow()
        val b = profileRepository.addProfile("B", "avatar", null).getOrThrow()
        familyProfileRepository.save(name = "Family", avatarKey = "a", memberProfileIds = listOf(a, b))
        repeat(5) { i ->
            db.watchEventDao().logWatch(
                org.seg7.familywatchlist.data.local.entity.WatchEventEntity(
                    tmdbId = 900 + i, mediaType = MediaType.MOVIE, watchedAt = java.time.LocalDate.now(), note = null,
                ),
                listOf(b),
            )
        }
        val family = ActiveProfile.Family(
            FamilyProfileEntity(name = "Family", avatarKey = "a", createdAt = 0),
            memberProfileIds = listOf(a, b),
        )

        val watchlistRepository = WatchlistRepository(db.watchlistDao(), clock) { _, _, _ -> true }
        val state = viewModel(watchlistRepository, family).uiState.first { !it.isLoading }

        assertTrue(
            "Family itself has zero events of its own; a warm member must not make Family warm any more",
            state.isColdStartForYou,
        )
    }

    /**
     * PLAN.md §5b M3i item 10's explicit regression guard: an *individual* profile's existing
     * cold-start behaviour must be completely untouched by this pass — same assertion as the
     * pre-existing "a cold-start profile is flagged" test above, kept here as a named regression
     * check specific to this milestone rather than relying on that older test not to bit-rot.
     */
    @Test
    fun `an individual profile's cold-start detection is unchanged by the Family fix`() = runTest {
        val watchlistRepository = WatchlistRepository(db.watchlistDao(), clock) { _, _, _ -> true }

        val state = viewModel(watchlistRepository, activeProfile).uiState.first { !it.isLoading }

        assertTrue("a fresh individual profile (0 events) must still be cold-start", state.isColdStartForYou)
        assertEquals(emptyList<TitleEntity>(), state.forYouTitles)
    }
}
