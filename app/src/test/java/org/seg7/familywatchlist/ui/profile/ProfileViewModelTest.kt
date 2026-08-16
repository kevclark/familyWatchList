package org.seg7.familywatchlist.ui.profile

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
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
import org.seg7.familywatchlist.data.repository.ProfileRepository
import org.seg7.familywatchlist.data.repository.UserPreferencesRepository
import org.seg7.familywatchlist.testutil.FakeClock
import org.seg7.familywatchlist.testutil.MainDispatcherRule
import org.seg7.familywatchlist.testutil.buildInMemoryDb

/**
 * PLAN.md §5 screen 2: profile CRUD, the 10-profile cap surfacing as a UI event (enforcement
 * itself is [org.seg7.familywatchlist.data.repository.ProfileRepositoryTest]'s job — this just
 * checks the ViewModel reacts to it correctly), and setting the active profile.
 *
 * Every ViewModel action here fires a `viewModelScope.launch` that goes through real Room/
 * DataStore suspend calls before the observable state updates — so assertions always wait on a
 * *predicate* over a Flow rather than reading a StateFlow's current value immediately after
 * firing the action (a bare `.first()` on a StateFlow just returns whatever's cached *right
 * now*, racing the in-flight coroutine). For the one-shot `events` SharedFlow, the collector is
 * registered — and confirmed attached via `subscriptionCount` — *before* the triggering call,
 * so the emission can't be missed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProfileViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var db: AppDatabase
    private lateinit var viewModel: ProfileViewModel
    private lateinit var userPreferencesRepository: UserPreferencesRepository

    @Before
    fun setUp() {
        db = buildInMemoryDb()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("profile_vm_test_${System.nanoTime()}") },
        )
        userPreferencesRepository = UserPreferencesRepository(dataStore)
        viewModel = ProfileViewModel(ProfileRepository(db.profileDao(), FakeClock()), userPreferencesRepository)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `addProfile creates a profile and makes it active`() = runTest {
        viewModel.addProfile("Kev", "🍿|FFC24B", null)

        val profile = viewModel.profiles.first { it.isNotEmpty() }.first()
        assertEquals("Kev", profile.name)
        assertEquals(profile.id, userPreferencesRepository.activeProfileId.first { it != null })
    }

    @Test
    fun `addProfile with a blank name is rejected before touching the repository`() = runTest {
        viewModel.addProfile("   ", "🍿|FFC24B", null)

        assertEquals(0, db.profileDao().count())
    }

    @Test
    fun `the 11th profile is rejected, surfaces an error event, and does not change the active profile`() = runTest {
        for (i in 0 until 10) {
            viewModel.addProfile("P$i", "🍿|FFC24B", null)
            viewModel.profiles.first { it.size == i + 1 }
        }
        val activeAfterTen = userPreferencesRepository.activeProfileId.first { it != null }
        assertTrue(viewModel.isAtProfileCap.first { it })

        // CoroutineStart.UNDISPATCHED runs this synchronously up to its first suspension point —
        // i.e. `collect`'s subscribe — so the collector is guaranteed attached by the time this
        // call returns, before `addProfile` below can possibly emit (SharedFlow with no replay
        // silently drops emissions with no attached subscriber).
        val eventChannel = Channel<ProfileUiEvent>(capacity = 1)
        val collectorJob = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            viewModel.events.collect { eventChannel.trySend(it) }
        }

        viewModel.addProfile("P10", "🍿|FFC24B", null)
        val event = eventChannel.receive()

        assertTrue(event is ProfileUiEvent.Error)
        assertEquals(10, db.profileDao().count())
        assertEquals(activeAfterTen, userPreferencesRepository.activeProfileId.first())
        collectorJob.cancel()
    }

    @Test
    fun `deleteProfile removes it from the observed list`() = runTest {
        viewModel.addProfile("Kev", "🍿|FFC24B", null)
        val profile = viewModel.profiles.first { it.isNotEmpty() }.first()

        viewModel.deleteProfile(profile)

        assertTrue(viewModel.profiles.first { it.isEmpty() }.isEmpty())
    }

    @Test
    fun `selectActive sets the DataStore active profile id`() = runTest {
        viewModel.addProfile("Kev", "🍿|FFC24B", null)
        viewModel.profiles.first { it.size == 1 }
        viewModel.addProfile("Alex", "🦊|EF6C00", null)
        val alex = viewModel.profiles.first { it.size == 2 }.first { it.name == "Alex" }

        viewModel.selectActive(alex.id)

        assertEquals(alex.id, userPreferencesRepository.activeProfileId.first { it == alex.id })
    }
}
