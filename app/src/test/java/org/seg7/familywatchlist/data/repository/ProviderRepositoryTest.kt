package org.seg7.familywatchlist.data.repository

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.seg7.familywatchlist.data.local.AppDatabase
import org.seg7.familywatchlist.data.local.entity.ProviderEntity
import org.seg7.familywatchlist.data.remote.TmdbClient
import org.seg7.familywatchlist.testutil.buildInMemoryDb

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProviderRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var server: MockWebServer
    private lateinit var repo: ProviderRepository

    @Before
    fun setUp() {
        db = buildInMemoryDb()
        server = MockWebServer()
        server.start()
        val api = TmdbClient.create(baseUrl = server.url("/").toString(), accessToken = { "t" })
        repo = ProviderRepository(db.providerDao(), api)
    }

    @After
    fun tearDown() {
        server.close()
        db.close()
    }

    @Test
    fun `seedIfEmpty merges movie and tv provider lists, deduplicated, defaulting to unsubscribed`() = runTest {
        server.enqueue(
            MockResponse(
                body = """{"results": [{"provider_id": 8, "provider_name": "Netflix", "display_priority": 1}]}"""
            )
        )
        server.enqueue(
            MockResponse(
                body = """{"results": [
                    {"provider_id": 8, "provider_name": "Netflix", "display_priority": 1},
                    {"provider_id": 38, "provider_name": "BBC iPlayer", "display_priority": 2}
                ]}"""
            )
        )

        repo.seedIfEmpty()

        val all = db.providerDao().count()
        assertEquals(2, all)
        assertTrue(db.providerDao().getSubscribed().isEmpty())
    }

    @Test
    fun `seedIfEmpty is a no-op once already seeded`() = runTest {
        db.providerDao().upsertAll(listOf(ProviderEntity(8, "Netflix", null, subscribed = true, displayPriority = 1)))

        repo.seedIfEmpty()

        assertEquals(0, server.requestCount)
    }

    @Test
    fun `setSubscribed toggles a provider on`() = runTest {
        db.providerDao().upsertAll(listOf(ProviderEntity(8, "Netflix", null, subscribed = false, displayPriority = 1)))

        repo.setSubscribed(8, true)

        assertEquals(listOf(8), repo.getSubscribedIds())
    }

    @Test
    fun `applyOnboardingDefaults subscribes the PLAN md §2 GB default services, case-insensitively, and leaves the rest alone`() = runTest {
        db.providerDao().upsertAll(
            listOf(
                ProviderEntity(8, "Netflix", null, subscribed = false, displayPriority = 1),
                ProviderEntity(337, "Disney Plus", null, subscribed = false, displayPriority = 2),
                ProviderEntity(9, "amazon prime video", null, subscribed = false, displayPriority = 3),
                ProviderEntity(38, "BBC iPlayer", null, subscribed = false, displayPriority = 4),
                ProviderEntity(103, "Channel 4", null, subscribed = false, displayPriority = 5),
                ProviderEntity(104, "ITVX", null, subscribed = false, displayPriority = 6),
                ProviderEntity(2, "Apple TV", null, subscribed = false, displayPriority = 7),
            )
        )

        repo.applyOnboardingDefaults()

        assertEquals(setOf(8, 337, 9, 38, 103, 104), repo.getSubscribedIds().toSet())
    }

    @Test
    fun `applyOnboardingDefaults is a no-op once anything is already subscribed — safe to call again from Settings`() = runTest {
        db.providerDao().upsertAll(
            listOf(
                ProviderEntity(8, "Netflix", null, subscribed = false, displayPriority = 1),
                ProviderEntity(99, "Some Other Service", null, subscribed = true, displayPriority = 2),
            )
        )

        repo.applyOnboardingDefaults()

        assertEquals(listOf(99), repo.getSubscribedIds())
    }
}
