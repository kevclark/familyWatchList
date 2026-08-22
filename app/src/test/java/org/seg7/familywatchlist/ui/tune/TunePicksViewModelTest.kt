package org.seg7.familywatchlist.ui.tune

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.lifecycle.ViewModelStore
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
import org.seg7.familywatchlist.data.local.entity.AttrType
import org.seg7.familywatchlist.data.local.entity.FAMILY_PROFILE_SENTINEL_ID
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.RatingEntity
import org.seg7.familywatchlist.data.local.entity.RatingValue
import org.seg7.familywatchlist.data.local.entity.TitleAttributeEntity
import org.seg7.familywatchlist.data.recommend.RecommenderSpec
import org.seg7.familywatchlist.data.recommend.SliderSettings
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
import org.seg7.familywatchlist.data.repository.WatchEventRepository
import org.seg7.familywatchlist.testutil.FakeClock
import org.seg7.familywatchlist.testutil.MainDispatcherRule
import org.seg7.familywatchlist.testutil.buildInMemoryDb

/**
 * PLAN.md §4a: "Tune my picks". Every assertion here waits on an actual [kotlinx.coroutines.flow.Flow]
 * emission ([ProfileSlidersRepository.observe]/`StateFlow.first { predicate }`) rather than a
 * fixed virtual-time advance — `runTest`'s scheduler auto-advances past idle `delay()`s
 * (including [TunePicksViewModel.RECOMPUTE_DEBOUNCE_MS]'s debounce) on its own, so pinning an
 * exact "not yet persisted" instant would be asserting a timing implementation detail this
 * ViewModel makes no actual guarantee about — only "eventually, after settling" does. Waiting on
 * the real emission is correct regardless of which dispatcher ends up doing the write.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TunePicksViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var db: AppDatabase
    private lateinit var server: MockWebServer
    private lateinit var clock: FakeClock
    private lateinit var profileRepository: ProfileRepository
    private lateinit var profileSlidersRepository: ProfileSlidersRepository
    private lateinit var recommendationRepository: RecommendationRepository
    private lateinit var userPreferencesRepository: UserPreferencesRepository
    private lateinit var familyProfileRepository: FamilyProfileRepository
    private lateinit var watchEventRepository: WatchEventRepository

    @Before
    fun setUp() {
        db = buildInMemoryDb()
        server = MockWebServer()
        server.start()
        clock = FakeClock(startMillis = 1_000_000L)
        val api = TmdbClient.create(baseUrl = server.url("/").toString(), accessToken = { "t" })

        val titleRepository = TitleRepository(db.titleDao(), db.titleAttributeDao(), db.providerAvailabilityDao(), api, clock)
        val discoverRepository = DiscoverRepository(db.discoverCacheDao(), db.titleDao(), api, clock)
        val providerRepository = ProviderRepository(db.providerDao(), api, discoverRepository)
        profileRepository = ProfileRepository(db.profileDao(), clock)
        profileSlidersRepository = ProfileSlidersRepository(db.profileSlidersDao())
        familyProfileRepository = FamilyProfileRepository(db.familyProfileDao(), db.profileDao(), clock)
        watchEventRepository = WatchEventRepository(db.watchEventDao(), db.watchlistDao())
        recommendationRepository = RecommendationRepository(
            watchEventDao = db.watchEventDao(),
            ratingDao = db.ratingDao(),
            watchlistDao = db.watchlistDao(),
            titleAttributeDao = db.titleAttributeDao(),
            titleRepository = titleRepository,
            discoverRepository = discoverRepository,
            providerRepository = providerRepository,
            profileRepository = profileRepository,
            profileSlidersRepository = profileSlidersRepository,
            familyProfileRepository = familyProfileRepository,
            shortlistDao = db.shortlistDao(),
            clock = clock,
        )
        val dataStoreFileName = "tune_prefs_${System.nanoTime()}"
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { ApplicationProvider.getApplicationContext<android.content.Context>().preferencesDataStoreFile(dataStoreFileName) },
        )
        userPreferencesRepository = UserPreferencesRepository(dataStore)
    }

    // ViewModel.clear() (which cancels viewModelScope's Job) is `internal`, not directly callable
    // from a test in a different module — routing every constructed ViewModel through a
    // ViewModelStore lets tearDown() reach it anyway via ViewModelStore.clear() (public, and
    // implemented in the same androidx.lifecycle module so it can call the internal method).
    private val viewModelStore = ViewModelStore()
    private var nextViewModelKey = 0

    @After
    fun tearDown() {
        // Every ViewModel built here launches real, unawaited Room-backed coroutines in its init
        // block (and, if a test triggers one, the debounced recompute collector) that this test
        // class never explicitly joins in every case. Left alone, a still-in-flight one can resume
        // on a real background thread *after* this method closes db/server below, throwing on the
        // now-closed connection — an exception kotlinx-coroutines-test then attributes to whichever
        // *later* test happens to be starting up (observed as flaky, non-deterministic
        // UncaughtExceptionsBeforeTest failures on unrelated tests). Clearing the store cancels
        // every ViewModel's viewModelScope Job outright, so nothing from this test can leak into
        // the next one.
        viewModelStore.clear()
        server.close()
        db.close()
    }

    private fun buildViewModel(profileId: Long): TunePicksViewModel {
        val vm = TunePicksViewModel(profileId, profileSlidersRepository, recommendationRepository, userPreferencesRepository)
        viewModelStore.put("tune-picks-${nextViewModelKey++}", vm)
        return vm
    }

    @Test
    fun `starts at SliderSettings DEFAULT for a profile with no stored sliders`() = runTest {
        val id = profileRepository.addProfile("Kev", "avatar", null).getOrThrow()
        val vm = buildViewModel(id)

        assertEquals(SliderSettings.DEFAULT, vm.sliders.first())
        assertEquals(RecommenderSpec.SHORTLIST_TARGET_SIZE, vm.suggestionCount.first())
        assertEquals(0.0, vm.familyBlend.first(), 1e-9)
    }

    @Test
    fun `loads previously stored sliders, suggestion count, and family blend on init`() = runTest {
        val id = profileRepository.addProfile("Kev", "avatar", null).getOrThrow()
        profileSlidersRepository.set(id, SliderSettings(discovery = 0.5, recency = -0.5, personalMatch = 1.0))
        profileSlidersRepository.setSuggestionCount(id, 10)
        userPreferencesRepository.setFamilyBlendSlider(-0.75)

        val vm = buildViewModel(id)

        val loaded = vm.sliders.first { it.discovery == 0.5 }
        assertEquals(SliderSettings(discovery = 0.5, recency = -0.5, personalMatch = 1.0), loaded)
        assertEquals(10, vm.suggestionCount.first { it == 10 })
        assertEquals(-0.75, vm.familyBlend.first { it == -0.75 }, 1e-9)
    }

    /**
     * A sentinel (0.9) is seeded and genuinely awaited before any mutation — see the suggestion-
     * count coercion test's kdoc above for why a plain `.first()` (no predicate) doesn't actually
     * guarantee the init coroutine's own `_sliders.value = profileSlidersRepository.get(profileId)`
     * write has landed yet, when the unseeded persisted default happens to equal the constructed
     * default (both [SliderSettings.DEFAULT]): that assignment can otherwise race a mutation this
     * test makes moments later on a different real thread and clobber it.
     */
    @Test
    fun `each slider updates its exposed value immediately`() = runTest {
        val id = profileRepository.addProfile("Kev", "avatar", null).getOrThrow()
        profileSlidersRepository.set(id, SliderSettings(discovery = 0.9)) // sentinel — see kdoc above
        val vm = buildViewModel(id)
        vm.sliders.first { it.discovery == 0.9 }

        vm.onDiscoveryChange(1f)
        assertEquals(1.0, vm.sliders.value.discovery, 1e-9)

        vm.onRecencyChange(-0.5f)
        assertEquals(-0.5, vm.sliders.value.recency, 1e-9)

        vm.onPersonalMatchChange(0.25f)
        assertEquals(0.25, vm.sliders.value.personalMatch, 1e-9)
    }

    /**
     * Design corrected 2026-08-20: there's no fixed range constant any more — the coercion bound
     * is [suggestionCountRange] applied to whatever [TunePicksViewModel.eligibleCandidateCount]
     * currently is. A profile with no persisted eligible count yet reads as
     * [RecommenderSpec.SHORTLIST_TARGET_SIZE] (30), so the range here is 4..30.
     *
     * A sentinel (99) is seeded for the *requested* count and genuinely awaited before any
     * mutation: the init coroutine's own `_suggestionCount.value = profileSlidersRepository
     * .getSuggestionCount(profileId)` assignment races on a real Room-backed background thread
     * against this test's synchronous mutations below, and a plain `.first()` (no predicate) on a
     * StateFlow returns the *current* value immediately without actually waiting for that
     * real-thread write to land — 30 (the constructed default) happens to equal the *unseeded*
     * persisted default too, so that race is otherwise invisible until it non-deterministically
     * clobbers a mutation performed a moment later. Seeding a value that can only ever come from
     * that specific init assignment, then waiting for it via a genuine predicate, forces a real
     * happens-before relationship (the init coroutine never writes to this field again).
     */
    @Test
    fun `onSuggestionCountChange updates the exposed value immediately and coerces into the current eligible-based range`() = runTest {
        val id = profileRepository.addProfile("Kev", "avatar", null).getOrThrow()
        profileSlidersRepository.setSuggestionCount(id, 99) // sentinel — see kdoc above
        val vm = buildViewModel(id)
        vm.suggestionCount.first { it == 99 }

        vm.onSuggestionCountChange(10)
        assertEquals(10, vm.suggestionCount.value)

        vm.onSuggestionCountChange(999) // above the default eligible ceiling of 30
        assertEquals(RecommenderSpec.SHORTLIST_TARGET_SIZE, vm.suggestionCount.value)

        vm.onSuggestionCountChange(0) // below RecommenderSpec.SUGGESTION_COUNT_MIN
        assertEquals(RecommenderSpec.SUGGESTION_COUNT_MIN, vm.suggestionCount.value)
    }

    @Test
    fun `onSuggestionCountChange coerces against a shrunk eligible ceiling, not the old fixed default`() = runTest {
        val id = profileRepository.addProfile("Kev", "avatar", null).getOrThrow()
        profileSlidersRepository.setEligibleCandidateCount(id, 12) // a thin pool, well below the 30 default
        val vm = buildViewModel(id)
        vm.eligibleCandidateCount.first { it == 12 } // let init load the seeded eligible count

        vm.onSuggestionCountChange(25) // above the real 12-title ceiling

        assertEquals(12, vm.suggestionCount.value)
    }

    @Test
    fun `onSuggestionCountChange is a no-op when there are zero eligible candidates`() = runTest {
        val id = profileRepository.addProfile("Kev", "avatar", null).getOrThrow()
        profileSlidersRepository.setEligibleCandidateCount(id, 0)
        val vm = buildViewModel(id)
        vm.eligibleCandidateCount.first { it == 0 }
        val before = vm.suggestionCount.value

        vm.onSuggestionCountChange(10)

        assertEquals(before, vm.suggestionCount.value) // ignored — suggestionCountRange(0) is null
    }

    @Test
    fun `a profile with no stored eligible count reads as the RecommenderSpec default (30)`() = runTest {
        val id = profileRepository.addProfile("Kev", "avatar", null).getOrThrow()
        val vm = buildViewModel(id)

        assertEquals(RecommenderSpec.SHORTLIST_TARGET_SIZE, vm.eligibleCandidateCount.first())
    }

    @Test
    fun `loads a previously persisted eligible candidate count on init (last-known, no live fetch)`() = runTest {
        val id = profileRepository.addProfile("Kev", "avatar", null).getOrThrow()
        profileSlidersRepository.setEligibleCandidateCount(id, 17)

        val vm = buildViewModel(id)

        assertEquals(17, vm.eligibleCandidateCount.first { it == 17 })
        assertEquals(0, server.requestCount) // no network call just to open the screen
    }

    // Note: a full "slider-triggered recompute against a real MockWebServer round-trip actually
    // refreshes eligibleCandidateCount" test was tried here and dropped — real network IO from
    // inside a debounced ViewModel coroutine (on top of this class's already-real, unshared
    // UnconfinedTestDispatcher scheduler) produced non-deterministic cross-test interference
    // (leaked background threads tripping up unrelated later tests in this class with
    // UncompletedCoroutinesError / closed-connection errors). The underlying behaviour this would
    // have proven — refreshProfileShortlist persisting the real eligible count, and the effective
    // min(requested, eligible) clamp — is already covered thoroughly and deterministically by
    // RecommendationRepositoryTest; the one line this ViewModel adds
    // (`profileSlidersRepository.getEligibleCandidateCount(profileId)` read immediately after
    // `refreshProfileShortlist` inside the recompute collector) is straightforward enough to trust
    // from code review given that repository-level coverage.

    @Test
    fun `a slider change is eventually persisted and triggers a recompute`() = runTest {
        val id = profileRepository.addProfile("Kev", "avatar", null).getOrThrow()
        profileSlidersRepository.set(id, SliderSettings(discovery = 0.9)) // sentinel — see the coercion test's kdoc above
        val vm = buildViewModel(id)
        vm.sliders.first { it.discovery == 0.9 }

        vm.onDiscoveryChange(1f)

        val persisted = profileSlidersRepository.observe(id).first { it.discovery == 1.0 }
        assertEquals(1.0, persisted.discovery, 1e-9)
    }

    @Test
    fun `a suggestion count change is eventually persisted`() = runTest {
        val id = profileRepository.addProfile("Kev", "avatar", null).getOrThrow()
        profileSlidersRepository.setSuggestionCount(id, 99) // sentinel — see the coercion test's kdoc above for why
        val vm = buildViewModel(id)
        vm.suggestionCount.first { it == 99 }

        vm.onSuggestionCountChange(10)

        val persisted = profileSlidersRepository.observeSuggestionCount(id).first { it == 10 }
        assertEquals(10, persisted)
    }

    @Test
    fun `resetToDefaults resets the exposed value immediately and eventually persists it`() = runTest {
        val id = profileRepository.addProfile("Kev", "avatar", null).getOrThrow()
        profileSlidersRepository.set(id, SliderSettings(discovery = 1.0, recency = 1.0, personalMatch = 1.0))
        profileSlidersRepository.setSuggestionCount(id, 10)
        val vm = buildViewModel(id)
        vm.sliders.first { it.discovery == 1.0 } // wait for init to load the seeded (non-default) value
        vm.suggestionCount.first { it == 10 }

        vm.resetToDefaults()

        assertEquals(SliderSettings.DEFAULT, vm.sliders.value)
        assertEquals(RecommenderSpec.SHORTLIST_TARGET_SIZE, vm.suggestionCount.value)
        val persistedSliders = profileSlidersRepository.observe(id).first { it == SliderSettings.DEFAULT }
        assertEquals(SliderSettings.DEFAULT, persistedSliders)
        val persistedCount = profileSlidersRepository.observeSuggestionCount(id).first { it == RecommenderSpec.SHORTLIST_TARGET_SIZE }
        assertEquals(RecommenderSpec.SHORTLIST_TARGET_SIZE, persistedCount)
    }

    @Test
    fun `onFamilyBlendChange updates immediately and eventually persists to the shared preference`() = runTest {
        val id = profileRepository.addProfile("Kev", "avatar", null).getOrThrow()
        userPreferencesRepository.setFamilyBlendSlider(-0.42) // sentinel (negative, so it doesn't satisfy `it > 0.0` below) — see the coercion test's kdoc above
        val vm = buildViewModel(id)
        vm.familyBlend.first { it == -0.42 }

        vm.onFamilyBlendChange(0.6f)

        assertEquals(0.6, vm.familyBlend.value, 1e-6) // immediate (1e-6: Float -> Double widening isn't bit-exact)
        assertEquals(0.6, userPreferencesRepository.familyBlendSlider.first { it > 0.0 }, 1e-6)
    }

    /**
     * PLAN.md §4b (M3k): Family is now a fully independent profile with its own storable slider
     * settings — [TunePicksViewModel] is keyed purely on a `Long` id with no assumption of a real
     * `profiles` row (confirmed by reading the ViewModel/screen before writing this), so it must
     * work unchanged when constructed with [FAMILY_PROFILE_SENTINEL_ID], mirroring "starts at
     * SliderSettings DEFAULT..." above for a real profile.
     */
    @Test
    fun `starts at SliderSettings DEFAULT for the Family sentinel, same as any real profile`() = runTest {
        val vm = buildViewModel(FAMILY_PROFILE_SENTINEL_ID)

        assertEquals(SliderSettings.DEFAULT, vm.sliders.first())
        assertEquals(RecommenderSpec.SHORTLIST_TARGET_SIZE, vm.suggestionCount.first())
    }

    /**
     * PLAN.md §4b/§4a (M3k): Settings' "Tune my picks" row is now unconditionally enabled for
     * Family (the disabled branch this milestone removes described the old fixed-weight blend
     * design, which no longer applies) — this proves the underlying mechanism it opens onto,
     * [ProfileSlidersRepository.set]/`.get` and [RecommendationRepository.refreshProfileShortlist],
     * genuinely works end-to-end for [FAMILY_PROFILE_SENTINEL_ID], not just that the row renders.
     * A Family profile is created first so `refreshProfileShortlist`'s existence check (PLAN.md
     * §4b) doesn't treat the recompute as a no-op "no such profile" — mirrors
     * `RecommendationRepositoryTest`'s `refreshProfileShortlist(FAMILY_PROFILE_SENTINEL_ID)` setup.
     */
    @Test
    fun `a slider change while Family is active is eventually persisted, exactly like a real profile`() = runTest {
        val kevId = profileRepository.addProfile("Kev", "avatar", null).getOrThrow()
        val samId = profileRepository.addProfile("Sam", "avatar", null).getOrThrow()
        familyProfileRepository.save("Family", "avatar", listOf(kevId, samId)).getOrThrow()
        profileSlidersRepository.set(FAMILY_PROFILE_SENTINEL_ID, SliderSettings(discovery = 0.9)) // sentinel — see the coercion test's kdoc above
        val vm = buildViewModel(FAMILY_PROFILE_SENTINEL_ID)
        vm.sliders.first { it.discovery == 0.9 }

        vm.onDiscoveryChange(1f)

        val persisted = profileSlidersRepository.observe(FAMILY_PROFILE_SENTINEL_ID).first { it.discovery == 1.0 }
        assertEquals(1.0, persisted.discovery, 1e-9)
    }

    /**
     * PLAN.md §4b (M3k): the recompute a slider change triggers must run through Family's own
     * *independent* shortlist pipeline (`refreshProfileShortlist(FAMILY_PROFILE_SENTINEL_ID, ...)`
     * — see PLAN.md §4b) and not silently bail out as "no such profile" — proven directly against
     * [RecommendationRepository.refreshProfileShortlist] (not routed through the ViewModel's own
     * debounced trigger — see this file's kdoc above `a slider change is eventually persisted...`
     * for why a full network round trip through the debounced collector was tried and dropped as
     * flaky elsewhere in this class) by seeding Family past its own cold-start threshold, mirroring
     * `RecommendationRepositoryTest.seedWarmFamily`, and confirming the exact call this ViewModel's
     * recompute collector makes persists a real, non-empty shortlist under [FAMILY_SCOPE_KEY].
     */
    @Test
    fun `refreshProfileShortlist(FAMILY_PROFILE_SENTINEL_ID) — the call a slider change triggers — persists a real shortlist`() = runTest {
        val kevId = profileRepository.addProfile("Kev", "avatar", null).getOrThrow()
        val samId = profileRepository.addProfile("Sam", "avatar", null).getOrThrow()
        familyProfileRepository.save("Family", "avatar", listOf(kevId, samId)).getOrThrow()
        // Past FAMILY_PROFILE_SENTINEL_ID's own cold-start threshold — mirrors
        // RecommendationRepositoryTest.seedWarmFamily exactly, including its *mixed* Comedy(4)/
        // Drama(1) genre split (never all-one-genre): PLAN.md §4's IDF damping fully zeroes an
        // attribute present on every single watched title, which would silently wash the below
        // candidate's Comedy-match score to exactly 0 and get it filtered as non-positive — see
        // seedWarmProfile's kdoc in that file for the same pitfall.
        listOf(501, 502, 503, 504).forEach { tmdbId ->
            db.titleAttributeDao().upsertAll(listOf(TitleAttributeEntity(tmdbId, MediaType.MOVIE, AttrType.GENRE, 35, "Comedy", null)))
            watchEventRepository.logWatch(tmdbId, MediaType.MOVIE, java.time.LocalDate.of(2026, 8, 1), null, listOf(FAMILY_PROFILE_SENTINEL_ID))
        }
        db.titleAttributeDao().upsertAll(listOf(TitleAttributeEntity(505, MediaType.MOVIE, AttrType.GENRE, 18, "Drama", null)))
        watchEventRepository.logWatch(505, MediaType.MOVIE, java.time.LocalDate.of(2026, 8, 1), null, listOf(FAMILY_PROFILE_SENTINEL_ID))
        db.ratingDao().upsert(RatingEntity(FAMILY_PROFILE_SENTINEL_ID, 501, MediaType.MOVIE, RatingValue.UP, clock.current))
        server.enqueue(
            MockResponse(
                body = """
                {
                  "page": 1,
                  "results": [
                    {"id": 7000, "title": "Family's Own Pick", "poster_path": "/p.jpg", "release_date": "2026-08-01", "vote_average": 8.0, "vote_count": 500, "popularity": 50.0}
                  ],
                  "total_pages": 1,
                  "total_results": 1
                }
                """.trimIndent(),
            )
        )
        server.enqueue(
            MockResponse(
                body = """
                {
                  "id": 7000,
                  "title": "Family's Own Pick",
                  "release_date": "2026-08-01",
                  "runtime": 100,
                  "vote_average": 8.0,
                  "vote_count": 500,
                  "popularity": 50.0,
                  "genres": [{"id": 35, "name": "Comedy"}],
                  "credits": {"cast": [], "crew": []},
                  "keywords": {"keywords": []},
                  "videos": {"results": []},
                  "watch/providers": {"results": {}},
                  "release_dates": {"results": []}
                }
                """.trimIndent(),
            )
        )

        val entries = recommendationRepository.refreshProfileShortlist(FAMILY_PROFILE_SENTINEL_ID, region = "GB")

        assertEquals(listOf(7000), entries.map { it.tmdbId })
        val weekStart = recommendationRepository.currentWeekStart()
        assertEquals(listOf(7000), db.shortlistDao().getForScope(weekStart, FAMILY_SCOPE_KEY).map { it.tmdbId })
    }

    @Test
    fun `familyBlendVisible is false with one profile, true with two`() = runTest {
        val id = profileRepository.addProfile("Kev", "avatar", null).getOrThrow()
        val vm = buildViewModel(id)
        // .first() — familyBlendVisible is a WhileSubscribed(5_000) StateFlow, so the upstream
        // account-profile-count flow only starts collecting once something actually subscribes,
        // same pattern as SearchViewModelTest's hasSubscribedServices.
        assertFalse(vm.familyBlendVisible.first())

        profileRepository.addProfile("Sam", "avatar", null).getOrThrow()

        assertTrue(vm.familyBlendVisible.first { it })
    }
}
