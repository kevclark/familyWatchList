package org.seg7.familywatchlist.data.repository

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.seg7.familywatchlist.data.local.AppDatabase
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.ProviderEntity
import org.seg7.familywatchlist.data.remote.TmdbClient
import org.seg7.familywatchlist.testutil.FakeClock
import org.seg7.familywatchlist.testutil.buildInMemoryDb

/**
 * PLAN.md §5a: the shared "is this on a service we pay for" check. These pin the three filtering
 * outcomes the plan calls out by name (available on a subscribed provider survives; available
 * only on a provider nobody's subscribed to is dropped; no GB availability at all is dropped),
 * plus the "nothing subscribed at all" edge case and the cache-reuse behaviour PLAN.md §5a
 * requires ("reuse cached ProviderAvailability rows... fetch fresh where it's not cached").
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AvailabilityGateTest {
    private lateinit var db: AppDatabase
    private lateinit var server: MockWebServer
    private lateinit var gate: AvailabilityGate

    @Before
    fun setUp() {
        db = buildInMemoryDb()
        server = MockWebServer()
        server.start()
        val api = TmdbClient.create(baseUrl = server.url("/").toString(), accessToken = { "t" })
        val clock = FakeClock(startMillis = 1_000L)
        val titleRepository = TitleRepository(db.titleDao(), db.titleAttributeDao(), db.providerAvailabilityDao(), api, clock)
        val providerRepository = ProviderRepository(db.providerDao(), api)
        gate = AvailabilityGate(titleRepository, providerRepository)
    }

    @After
    fun tearDown() {
        server.close()
        db.close()
    }

    @Test
    fun `available on a subscribed provider survives`() = runTest {
        subscribeProviders(subscribed = setOf(8), unsubscribed = setOf(337))
        server.enqueue(MockResponse(body = movieDetailJson(providerIds = listOf(8))))

        assertTrue(gate.isAvailableOnSubscribedProvider(38700, MediaType.MOVIE))
    }

    @Test
    fun `available only on a non-subscribed provider is dropped`() = runTest {
        subscribeProviders(subscribed = setOf(8), unsubscribed = setOf(337))
        server.enqueue(MockResponse(body = movieDetailJson(providerIds = listOf(337))))

        assertFalse(gate.isAvailableOnSubscribedProvider(38700, MediaType.MOVIE))
    }

    @Test
    fun `no GB availability at all is dropped`() = runTest {
        subscribeProviders(subscribed = setOf(8), unsubscribed = emptySet())
        server.enqueue(MockResponse(body = movieDetailJson(providerIds = emptyList())))

        assertFalse(gate.isAvailableOnSubscribedProvider(38700, MediaType.MOVIE))
    }

    @Test
    fun `nothing subscribed at all means nothing can ever pass, without even reaching the network`() = runTest {
        db.providerDao().upsertAll(listOf(ProviderEntity(8, "Netflix", null, subscribed = false, displayPriority = 1)))

        assertFalse(gate.isAvailableOnSubscribedProvider(38700, MediaType.MOVIE))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a second check within the 7-day TTL reuses cached rows instead of refetching`() = runTest {
        subscribeProviders(subscribed = setOf(8), unsubscribed = emptySet())
        server.enqueue(MockResponse(body = movieDetailJson(providerIds = listOf(8))))

        assertTrue(gate.isAvailableOnSubscribedProvider(38700, MediaType.MOVIE))
        assertTrue(gate.isAvailableOnSubscribedProvider(38700, MediaType.MOVIE))

        assertEquals(1, server.requestCount)
    }

    private suspend fun subscribeProviders(subscribed: Set<Int>, unsubscribed: Set<Int>) {
        val rows = subscribed.map { ProviderEntity(it, "Provider $it", null, subscribed = true, displayPriority = it) } +
            unsubscribed.map { ProviderEntity(it, "Provider $it", null, subscribed = false, displayPriority = it) }
        db.providerDao().upsertAll(rows)
    }

    private fun movieDetailJson(providerIds: List<Int>): String {
        val flatrate = providerIds.joinToString(",") { """{"provider_id": $it, "provider_name": "Provider $it"}""" }
        return """
            {
              "id": 38700,
              "title": "Paddington",
              "release_date": "2014-11-28",
              "runtime": 95,
              "watch/providers": { "results": { "GB": { "flatrate": [$flatrate] } } }
            }
        """.trimIndent()
    }
}
