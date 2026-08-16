package org.seg7.familywatchlist.data.remote

import okhttp3.Interceptor
import okhttp3.Response

/** Seam so tests can fake time instead of sleeping for real. */
interface RateLimiterClock {
    fun nowMillis(): Long
    fun sleep(millis: Long)
}

class SystemRateLimiterClock : RateLimiterClock {
    override fun nowMillis(): Long = System.currentTimeMillis()
    override fun sleep(millis: Long) {
        if (millis > 0) Thread.sleep(millis)
    }
}

/**
 * PLAN.md §3: throttle to 4 req/s with 429 retry-after handling. A sliding one-second window
 * of request start times is kept under [lock]; once the window is full, the calling thread
 * blocks until the oldest entry ages out. OkHttp interceptors run on a background dispatcher
 * thread per call, so blocking here is safe and is exactly how OkHttp expects rate limiting
 * to be implemented.
 */
class ThrottleInterceptor(
    private val maxRequestsPerSecond: Int = 4,
    private val maxRetries: Int = 3,
    private val clock: RateLimiterClock = SystemRateLimiterClock(),
) : Interceptor {

    private val lock = Any()
    private val requestTimestamps = ArrayDeque<Long>()

    private fun awaitSlot() {
        synchronized(lock) {
            while (true) {
                val now = clock.nowMillis()
                while (requestTimestamps.isNotEmpty() && now - requestTimestamps.first() >= WINDOW_MS) {
                    requestTimestamps.removeFirst()
                }
                if (requestTimestamps.size < maxRequestsPerSecond) {
                    requestTimestamps.addLast(now)
                    return
                }
                val waitMs = (WINDOW_MS - (now - requestTimestamps.first())).coerceAtLeast(1)
                clock.sleep(waitMs)
            }
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        var attempt = 0
        while (true) {
            awaitSlot()
            val response = chain.proceed(chain.request())
            if (response.code != 429 || attempt >= maxRetries) {
                return response
            }
            val retryAfterSeconds = response.header("Retry-After")?.toLongOrNull() ?: 1L
            response.close()
            clock.sleep(retryAfterSeconds * 1000)
            attempt++
        }
    }

    private companion object {
        const val WINDOW_MS = 1000L
    }
}
