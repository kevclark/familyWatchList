package org.seg7.familywatchlist.ui.onboarding

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.seg7.familywatchlist.data.local.AppDatabase
import org.seg7.familywatchlist.data.remote.TmdbClient
import org.seg7.familywatchlist.data.repository.ProfileRepository
import org.seg7.familywatchlist.data.repository.ProviderRepository
import org.seg7.familywatchlist.data.repository.UserPreferencesRepository
import org.seg7.familywatchlist.testutil.FakeClock
import org.seg7.familywatchlist.testutil.MainDispatcherRule
import org.seg7.familywatchlist.testutil.buildInMemoryDb

/**
 * PLAN.md §5 screen 1: attribution -> services -> first profile. Covers the two pieces of real
 * logic in [OnboardingViewModel]: the GB-default services being pre-ticked on entering the
 * services step (delegated to [ProviderRepository.applyOnboardingDefaults], already unit-tested
 * on its own — this checks the ViewModel actually calls it), and onboarding-completion state
 * (setting the DataStore flag + active profile, and rejecting a blank profile name).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OnboardingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var db: AppDatabase
    private lateinit var server: MockWebServer
    private lateinit var userPreferencesRepository: UserPreferencesRepository
    private lateinit var viewModel: OnboardingViewModel

    @Before
    fun setUp() {
        db = buildInMemoryDb()
        server = MockWebServer()
        server.start()
        val api = TmdbClient.create(baseUrl = server.url("/").toString(), accessToken = { "t" })
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("onboarding_vm_test_${System.nanoTime()}") },
        )
        userPreferencesRepository = UserPreferencesRepository(dataStore)
        viewModel = OnboardingViewModel(
            ProviderRepository(db.providerDao(), api),
            ProfileRepository(db.profileDao(), FakeClock()),
            userPreferencesRepository,
        )
    }

    @After
    fun tearDown() {
        server.close()
        db.close()
    }

    private fun enqueueProviderFixtures() {
        val body = """{"results": [
            {"provider_id": 8, "provider_name": "Netflix", "display_priority": 1},
            {"provider_id": 337, "provider_name": "Disney Plus", "display_priority": 2},
            {"provider_id": 2, "provider_name": "Apple TV", "display_priority": 3}
        ]}"""
        server.enqueue(MockResponse(body = body))
        server.enqueue(MockResponse(body = body))
    }

    @Test
    fun `acknowledging attribution moves to the services step and seeds + pre-ticks GB defaults`() = runTest {
        enqueueProviderFixtures()

        viewModel.onAttributionAcknowledged()

        assertEquals(OnboardingStep.SERVICES, viewModel.step.first())
        // Wait for the whole seed-then-default-tick sequence to finish, not just for the list
        // to become non-empty — Room's Flow can emit the seeded-but-not-yet-ticked state first.
        assertEquals(ServicesLoadState.Loaded, viewModel.servicesLoadState.first { it != ServicesLoadState.Loading })
        val providers = viewModel.providers.first { list -> list.any { it.name == "Netflix" && it.subscribed } }
        assertTrue(providers.first { it.name == "Netflix" }.subscribed)
        assertTrue(providers.first { it.name == "Disney Plus" }.subscribed)
        assertTrue(!providers.first { it.name == "Apple TV" }.subscribed)
    }

    @Test
    fun `onBack from services returns to attribution, from profile returns to services`() = runTest {
        enqueueProviderFixtures()
        viewModel.onAttributionAcknowledged()
        viewModel.providers.first { it.isNotEmpty() }

        viewModel.onServicesConfirmed()
        assertEquals(OnboardingStep.PROFILE, viewModel.step.first())

        viewModel.onBack()
        assertEquals(OnboardingStep.SERVICES, viewModel.step.first())

        viewModel.onBack()
        assertEquals(OnboardingStep.ATTRIBUTION, viewModel.step.first())
    }

    @Test
    fun `completeOnboarding creates the profile, sets it active, and flips onboarding-complete`() = runTest {
        viewModel.completeOnboarding("Kev", "🍿|FFC24B", "12")

        assertEquals(OnboardingCompletionState.Done, viewModel.completionState.first { it != OnboardingCompletionState.Idle })
        assertEquals(true, userPreferencesRepository.onboardingComplete.first())
        val profile = db.profileDao().observeAll().first().single()
        assertEquals("Kev", profile.name)
        assertEquals("12", profile.ageRatingCap)
        assertEquals(profile.id, userPreferencesRepository.activeProfileId.first())
    }

    @Test
    fun `completeOnboarding rejects a blank name without creating a profile or completing onboarding`() = runTest {
        viewModel.completeOnboarding("   ", "🍿|FFC24B", null)

        assertTrue(viewModel.completionState.first() is OnboardingCompletionState.Error)
        assertEquals(0, db.profileDao().count())
        assertEquals(false, userPreferencesRepository.onboardingComplete.first())
    }
}
