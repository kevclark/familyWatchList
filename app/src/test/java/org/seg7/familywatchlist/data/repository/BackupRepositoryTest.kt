package org.seg7.familywatchlist.data.repository

import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.seg7.familywatchlist.data.backup.BackupPayload
import org.seg7.familywatchlist.data.local.AppDatabase
import org.seg7.familywatchlist.data.local.entity.FamilyProfileEntity
import org.seg7.familywatchlist.data.local.entity.FamilyProfileMemberEntity
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.NotificationPreferenceEntity
import org.seg7.familywatchlist.data.local.entity.ProfileEntity
import org.seg7.familywatchlist.data.local.entity.ProfileSlidersEntity
import org.seg7.familywatchlist.data.local.entity.ProviderEntity
import org.seg7.familywatchlist.data.local.entity.RatingEntity
import org.seg7.familywatchlist.data.local.entity.RatingValue
import org.seg7.familywatchlist.data.local.entity.WatchlistEntryEntity
import org.seg7.familywatchlist.data.local.entity.WatchlistState
import org.seg7.familywatchlist.testutil.FakeClock
import org.seg7.familywatchlist.testutil.buildInMemoryDb

/**
 * PLAN.md §5 screen 8 "JSON backup/restore" (M4b). The round-trip test is the most valuable
 * single test here per the task brief: export a populated database, restore it into a completely
 * fresh one, and confirm the data matches. The malformed/wrong-version tests confirm the other
 * hard requirement — a bad file fails cleanly and leaves whatever's already in the database
 * untouched, never a silent partial write.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupRepositoryTest {
    private lateinit var sourceDb: AppDatabase
    private lateinit var targetDb: AppDatabase
    private val clock = FakeClock(10_000L)
    private lateinit var tempFile: File

    @Before
    fun setUp() {
        sourceDb = buildInMemoryDb()
        targetDb = buildInMemoryDb()
        tempFile = File.createTempFile("backup-test", ".json").apply { deleteOnExit() }
    }

    @After
    fun tearDown() {
        sourceDb.close()
        targetDb.close()
        tempFile.delete()
    }

    private fun newPrefsRepo(name: String): UserPreferencesRepository {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { context.preferencesDataStoreFile(name) })
        return UserPreferencesRepository(dataStore)
    }

    /** Seeds [sourceDb] + [sourcePrefs] with one of everything the backup covers. */
    private suspend fun seedSource(sourcePrefs: UserPreferencesRepository) {
        val kev = sourceDb.profileDao().insert(ProfileEntity(name = "Kev", avatarKey = "a", ageRatingCap = "15", createdAt = 1L))
        val sam = sourceDb.profileDao().insert(ProfileEntity(name = "Sam", avatarKey = "b", ageRatingCap = null, createdAt = 2L))

        sourceDb.familyProfileDao().upsert(FamilyProfileEntity(name = "Family", avatarKey = "f", createdAt = 3L))
        sourceDb.familyProfileDao().insertMembers(listOf(FamilyProfileMemberEntity(kev), FamilyProfileMemberEntity(sam)))

        val eventId = sourceDb.watchEventDao().logWatch(
            org.seg7.familywatchlist.data.local.entity.WatchEventEntity(
                tmdbId = 100, mediaType = MediaType.MOVIE, watchedAt = LocalDate.of(2026, 1, 1), note = "family night",
            ),
            listOf(kev, sam),
        )

        sourceDb.ratingDao().upsert(RatingEntity(profileId = kev, tmdbId = 100, mediaType = MediaType.MOVIE, value = RatingValue.UP, ratedAt = 5L))
        sourceDb.watchlistDao().upsert(WatchlistEntryEntity(tmdbId = 200, mediaType = MediaType.TV, addedByProfileId = sam, addedAt = 6L, state = WatchlistState.ACTIVE))

        sourceDb.providerDao().upsertAll(
            listOf(
                ProviderEntity(providerId = 8, name = "Netflix", logoPath = null, subscribed = true, displayPriority = 1),
                ProviderEntity(providerId = 9, name = "Prime Video", logoPath = null, subscribed = false, displayPriority = 2),
            ),
        )

        sourceDb.profileSlidersDao().upsert(ProfileSlidersEntity(profileId = kev, discovery = 0.5, recency = -0.2, personalMatch = 0.1, suggestionCount = 20, eligibleCandidateCount = 40))
        sourceDb.notificationPreferenceDao().upsert(NotificationPreferenceEntity(profileId = sam, enabled = false))

        sourcePrefs.setRegion("IE")
        sourcePrefs.setNotificationsEnabled(false)
        sourcePrefs.setRefreshSchedule(DayOfWeek.SUNDAY, 20)
        sourcePrefs.setFamilyBlendSlider(-0.4)

        check(eventId > 0)
    }

    @Test
    fun `export then restore into a fresh database reproduces every table`() = runTest {
        val sourcePrefs = newPrefsRepo("backup_source")
        seedSource(sourcePrefs)
        val sourceRepo = BackupRepository(sourceDb, sourcePrefs, clock)

        val uri = Uri.fromFile(tempFile)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        sourceRepo.exportTo(context, uri)

        val targetPrefs = newPrefsRepo("backup_target")
        val targetRepo = BackupRepository(targetDb, targetPrefs, clock)
        val outcome = targetRepo.importFrom(context, uri)

        assertTrue(outcome is BackupRepository.RestoreOutcome.Success)

        val profiles = targetDb.profileDao().getAllOnce().sortedBy { it.id }
        assertEquals(2, profiles.size)
        assertEquals("Kev", profiles[0].name)
        assertEquals("15", profiles[0].ageRatingCap)
        assertEquals("Sam", profiles[1].name)

        val family = targetDb.familyProfileDao().get()
        assertEquals("Family", family?.name)
        assertEquals(setOf(profiles[0].id, profiles[1].id), targetDb.familyProfileDao().getMemberIds().toSet())

        val events = targetDb.watchEventDao().getAllOnce()
        assertEquals(1, events.size)
        assertEquals("family night", events[0].note)
        assertEquals(LocalDate.of(2026, 1, 1), events[0].watchedAt)
        assertEquals(setOf(profiles[0].id, profiles[1].id), targetDb.watchEventDao().getProfileIdsForEvent(events[0].id).toSet())

        val ratings = targetDb.ratingDao().getAllOnce()
        assertEquals(1, ratings.size)
        assertEquals(RatingValue.UP, ratings[0].value)

        val watchlist = targetDb.watchlistDao().getAllOnce()
        assertEquals(1, watchlist.size)
        assertEquals(WatchlistState.ACTIVE, watchlist[0].state)

        // Providers themselves aren't exported — but the subscribed *flag* is, applied against
        // whatever provider rows already exist on the restoring device (seeded here to match).
        targetDb.providerDao().upsertAll(
            listOf(
                ProviderEntity(providerId = 8, name = "Netflix", logoPath = null, subscribed = false, displayPriority = 1),
                ProviderEntity(providerId = 9, name = "Prime Video", logoPath = null, subscribed = false, displayPriority = 2),
            ),
        )
        // Re-run restore now that providers exist, to prove the subscribed flag round-trips too.
        targetRepo.importFrom(context, uri)
        val restoredProviders = targetDb.providerDao().getAll().associateBy { it.providerId }
        assertTrue(restoredProviders.getValue(8).subscribed)
        assertEquals(false, restoredProviders.getValue(9).subscribed)

        val sliders = targetDb.profileSlidersDao().getAllOnce()
        assertEquals(1, sliders.size)
        assertEquals(0.5, sliders[0].discovery, 0.0001)
        assertEquals(40, sliders[0].eligibleCandidateCount)

        val notifPrefs = targetDb.notificationPreferenceDao().getAllOnce()
        assertEquals(1, notifPrefs.size)
        assertEquals(false, notifPrefs[0].enabled)

        assertEquals("IE", targetPrefs.region.first())
        assertEquals(false, targetPrefs.notificationsEnabled.first())
        assertEquals(DayOfWeek.SUNDAY, targetPrefs.refreshDayOfWeek.first())
        assertEquals(20, targetPrefs.refreshHour.first())
        assertEquals(-0.4, targetPrefs.familyBlendSlider.first(), 0.0001)
    }

    @Test
    fun `restoring a malformed file fails cleanly and leaves the database untouched`() = runTest {
        tempFile.writeText("{ this is not valid json at all")
        val targetPrefs = newPrefsRepo("backup_malformed")
        val targetRepo = BackupRepository(targetDb, targetPrefs, clock)

        // Seed the target with something first, to prove a failed restore doesn't wipe it.
        targetDb.profileDao().insert(ProfileEntity(name = "Existing", avatarKey = "x", ageRatingCap = null, createdAt = 1L))

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val outcome = targetRepo.importFrom(context, Uri.fromFile(tempFile))

        assertTrue(outcome is BackupRepository.RestoreOutcome.Error)
        val profiles = targetDb.profileDao().getAllOnce()
        assertEquals(1, profiles.size)
        assertEquals("Existing", profiles[0].name)
    }

    @Test
    fun `restoring a wrong-version file fails cleanly with a clear error`() = runTest {
        val sourcePrefs = newPrefsRepo("backup_version_source")
        seedSource(sourcePrefs)
        val sourceRepo = BackupRepository(sourceDb, sourcePrefs, clock)
        val payload = sourceRepo.buildSnapshot()
        val json = Json { prettyPrint = true }
        val tampered = json.encodeToJsonElement(BackupPayload.serializer(), payload).jsonObject.toMutableMap()
        tampered["version"] = JsonPrimitive(999)
        tempFile.writeText(JsonObject(tampered).toString())

        val targetPrefs = newPrefsRepo("backup_version_target")
        val targetRepo = BackupRepository(targetDb, targetPrefs, clock)
        targetDb.profileDao().insert(ProfileEntity(name = "Existing", avatarKey = "x", ageRatingCap = null, createdAt = 1L))

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val outcome = targetRepo.importFrom(context, Uri.fromFile(tempFile))

        assertTrue(outcome is BackupRepository.RestoreOutcome.Error)
        assertTrue((outcome as BackupRepository.RestoreOutcome.Error).message.contains("version"))
        val profiles = targetDb.profileDao().getAllOnce()
        assertEquals(1, profiles.size)
        assertEquals("Existing", profiles[0].name)
    }

    @Test
    fun `restoring a file that references an unknown profile id fails validation`() = runTest {
        val sourcePrefs = newPrefsRepo("backup_corrupt_source")
        seedSource(sourcePrefs)
        val sourceRepo = BackupRepository(sourceDb, sourcePrefs, clock)
        val payload = sourceRepo.buildSnapshot()
        val corrupted = payload.copy(ratings = payload.ratings.map { it.copy(profileId = 999_999L) })
        val json = Json { prettyPrint = true }
        tempFile.writeText(json.encodeToString(BackupPayload.serializer(), corrupted))

        val targetPrefs = newPrefsRepo("backup_corrupt_target")
        val targetRepo = BackupRepository(targetDb, targetPrefs, clock)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val outcome = targetRepo.importFrom(context, Uri.fromFile(tempFile))

        assertTrue(outcome is BackupRepository.RestoreOutcome.Error)
        assertEquals(0, targetDb.profileDao().getAllOnce().size)
    }
}
