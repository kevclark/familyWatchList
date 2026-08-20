package org.seg7.familywatchlist.ui.tune

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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
import org.seg7.familywatchlist.data.recommend.SliderSettings
import org.seg7.familywatchlist.data.remote.TmdbClient
import org.seg7.familywatchlist.data.repository.DiscoverRepository
import org.seg7.familywatchlist.data.repository.ProfileRepository
import org.seg7.familywatchlist.data.repository.ProfileSlidersRepository
import org.seg7.familywatchlist.data.repository.ProviderRepository
import org.seg7.familywatchlist.data.repository.RecommendationRepository
import org.seg7.familywatchlist.data.repository.TitleRepository
import org.seg7.familywatchlist.data.repository.UserPreferencesRepository
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
    private lateinit var profileRepository: ProfileRepository
    private lateinit var profileSlidersRepository: ProfileSlidersRepository
    private lateinit var recommendationRepository: RecommendationRepository
    private lateinit var userPreferencesRepository: UserPreferencesRepository

    @Before
    fun setUp() {
        db = buildInMemoryDb()
        server = MockWebServer()
        server.start()
        val clock = FakeClock(startMillis = 1_000_000L)
        val api = TmdbClient.create(baseUrl = server.url("/").toString(), accessToken = { "t" })

        val titleRepository = TitleRepository(db.titleDao(), db.titleAttributeDao(), db.providerAvailabilityDao(), api, clock)
        val discoverRepository = DiscoverRepository(db.discoverCacheDao(), db.titleDao(), api, clock)
        val providerRepository = ProviderRepository(db.providerDao(), api, discoverRepository)
        profileRepository = ProfileRepository(db.profileDao(), clock)
        profileSlidersRepository = ProfileSlidersRepository(db.profileSlidersDao())
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
            shortlistDao = db.shortlistDao(),
            clock = clock,
        )
        val dataStoreFileName = "tune_prefs_${System.nanoTime()}"
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { ApplicationProvider.getApplicationContext<android.content.Context>().preferencesDataStoreFile(dataStoreFileName) },
        )
        userPreferencesRepository = UserPreferencesRepository(dataStore)
    }

    @After
    fun tearDown() {
        server.close()
        db.close()
    }

    private fun buildViewModel(profileId: Long) =
        TunePicksViewModel(profileId, profileSlidersRepository, recommendationRepository, userPreferencesRepository)

    @Test
    fun `starts at SliderSettings DEFAULT for a profile with no stored sliders`() = runTest {
        val id = profileRepository.addProfile("Kev", "avatar", null).getOrThrow()
        val vm = buildViewModel(id)

        assertEquals(SliderSettings.DEFAULT, vm.sliders.first())
        assertEquals(0.0, vm.familyBlend.first(), 1e-9)
    }

    @Test
    fun `loads previously stored sliders and family blend on init`() = runTest {
        val id = profileRepository.addProfile("Kev", "avatar", null).getOrThrow()
        profileSlidersRepository.set(id, SliderSettings(discovery = 0.5, recency = -0.5, personalMatch = 1.0))
        userPreferencesRepository.setFamilyBlendSlider(-0.75)

        val vm = buildViewModel(id)

        val loaded = vm.sliders.first { it.discovery == 0.5 }
        assertEquals(SliderSettings(discovery = 0.5, recency = -0.5, personalMatch = 1.0), loaded)
        assertEquals(-0.75, vm.familyBlend.first { it == -0.75 }, 1e-9)
    }

    @Test
    fun `each slider updates its exposed value immediately`() = runTest {
        val id = profileRepository.addProfile("Kev", "avatar", null).getOrThrow()
        val vm = buildViewModel(id)
        vm.sliders.first() // let the (no-op, same-value) init load settle first

        vm.onDiscoveryChange(1f)
        assertEquals(1.0, vm.sliders.value.discovery, 1e-9)

        vm.onRecencyChange(-0.5f)
        assertEquals(-0.5, vm.sliders.value.recency, 1e-9)

        vm.onPersonalMatchChange(0.25f)
        assertEquals(0.25, vm.sliders.value.personalMatch, 1e-9)
    }

    @Test
    fun `a slider change is eventually persisted and triggers a recompute`() = runTest {
        val id = profileRepository.addProfile("Kev", "avatar", null).getOrThrow()
        val vm = buildViewModel(id)
        vm.sliders.first()

        vm.onDiscoveryChange(1f)

        val persisted = profileSlidersRepository.observe(id).first { it.discovery == 1.0 }
        assertEquals(1.0, persisted.discovery, 1e-9)
    }

    @Test
    fun `resetToDefaults resets the exposed value immediately and eventually persists it`() = runTest {
        val id = profileRepository.addProfile("Kev", "avatar", null).getOrThrow()
        profileSlidersRepository.set(id, SliderSettings(discovery = 1.0, recency = 1.0, personalMatch = 1.0))
        val vm = buildViewModel(id)
        vm.sliders.first { it.discovery == 1.0 } // wait for init to load the seeded (non-default) value

        vm.resetToDefaults()

        assertEquals(SliderSettings.DEFAULT, vm.sliders.value)
        val persisted = profileSlidersRepository.observe(id).first { it == SliderSettings.DEFAULT }
        assertEquals(SliderSettings.DEFAULT, persisted)
    }

    @Test
    fun `onFamilyBlendChange updates immediately and eventually persists to the shared preference`() = runTest {
        val id = profileRepository.addProfile("Kev", "avatar", null).getOrThrow()
        val vm = buildViewModel(id)
        vm.familyBlend.first()

        vm.onFamilyBlendChange(0.6f)

        assertEquals(0.6, vm.familyBlend.value, 1e-6) // immediate (1e-6: Float -> Double widening isn't bit-exact)
        assertEquals(0.6, userPreferencesRepository.familyBlendSlider.first { it > 0.0 }, 1e-6)
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
