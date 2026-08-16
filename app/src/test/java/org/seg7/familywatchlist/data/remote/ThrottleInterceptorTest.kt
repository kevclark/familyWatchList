package org.seg7.familywatchlist.data.remote

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Fake clock: sleep() doesn't block wall time, just advances a counter and records the call. */
private class FakeRateLimiterClock(start: Long = 0L) : RateLimiterClock {
    var current = start
        private set
    val sleepCalls = mutableListOf<Long>()

    override fun nowMillis(): Long = current

    override fun sleep(millis: Long) {
        sleepCalls.add(millis)
        current += millis
    }
}

class ThrottleInterceptorTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun clientWith(clock: RateLimiterClock, maxRetries: Int = 3) = OkHttpClient.Builder()
        .addInterceptor(ThrottleInterceptor(maxRequestsPerSecond = 4, maxRetries = maxRetries, clock = clock))
        .build()

    @Test
    fun `first N requests within the per-second budget do not sleep`() {
        repeat(4) { server.enqueue(MockResponse(body = "{}")) }
        val clock = FakeRateLimiterClock()
        val client = clientWith(clock)

        repeat(4) {
            client.newCall(Request.Builder().url(server.url("/x")).build()).execute().close()
        }

        assertTrue("expected no throttling within budget, but slept: ${clock.sleepCalls}", clock.sleepCalls.isEmpty())
    }

    @Test
    fun `the 5th request within one second is throttled`() {
        repeat(5) { server.enqueue(MockResponse(body = "{}")) }
        val clock = FakeRateLimiterClock()
        val client = clientWith(clock)

        repeat(5) {
            client.newCall(Request.Builder().url(server.url("/x")).build()).execute().close()
        }

        assertTrue("expected a throttling sleep before the 5th request", clock.sleepCalls.isNotEmpty())
        assertEquals(5, server.requestCount)
    }

    @Test
    fun `429 is retried after the Retry-After delay and eventually succeeds`() {
        server.enqueue(MockResponse(code = 429, headers = okhttp3.Headers.headersOf("Retry-After", "2")))
        server.enqueue(MockResponse(body = "{}"))
        val clock = FakeRateLimiterClock()
        val client = clientWith(clock)

        val response = client.newCall(Request.Builder().url(server.url("/x")).build()).execute()

        assertEquals(200, response.code)
        response.close()
        assertTrue("expected a 2000ms retry-after sleep, got: ${clock.sleepCalls}", clock.sleepCalls.contains(2000L))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `429 without a Retry-After header falls back to 1 second`() {
        server.enqueue(MockResponse(code = 429))
        server.enqueue(MockResponse(body = "{}"))
        val clock = FakeRateLimiterClock()
        val client = clientWith(clock)

        val response = client.newCall(Request.Builder().url(server.url("/x")).build()).execute()

        assertEquals(200, response.code)
        response.close()
        assertTrue(clock.sleepCalls.contains(1000L))
    }

    @Test
    fun `gives up after maxRetries on persistent 429`() {
        repeat(4) { server.enqueue(MockResponse(code = 429, headers = okhttp3.Headers.headersOf("Retry-After", "0"))) }
        val clock = FakeRateLimiterClock()
        val client = clientWith(clock, maxRetries = 2)

        val response = client.newCall(Request.Builder().url(server.url("/x")).build()).execute()

        assertEquals(429, response.code)
        response.close()
        // 1 initial attempt + 2 retries = 3 requests total.
        assertEquals(3, server.requestCount)
    }
}
