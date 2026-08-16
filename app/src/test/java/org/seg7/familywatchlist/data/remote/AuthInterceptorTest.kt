package org.seg7.familywatchlist.data.remote

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AuthInterceptorTest {
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

    @Test
    fun `adds bearer token header from the token provider`() {
        server.enqueue(MockResponse(body = "{}"))
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor { "test-token-abc" })
            .build()

        client.newCall(Request.Builder().url(server.url("/movie/1")).build()).execute().use {
            assertEquals(200, it.code)
        }

        val recorded = server.takeRequest()
        assertEquals("Bearer test-token-abc", recorded.headers["Authorization"])
        assertEquals("application/json", recorded.headers["Accept"])
    }

    @Test
    fun `re-reads the token provider on every request`() {
        server.enqueue(MockResponse(body = "{}"))
        server.enqueue(MockResponse(body = "{}"))
        var callCount = 0
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor { "token-${++callCount}" })
            .build()

        client.newCall(Request.Builder().url(server.url("/a")).build()).execute().close()
        client.newCall(Request.Builder().url(server.url("/b")).build()).execute().close()

        assertEquals("Bearer token-1", server.takeRequest().headers["Authorization"])
        assertEquals("Bearer token-2", server.takeRequest().headers["Authorization"])
    }
}
