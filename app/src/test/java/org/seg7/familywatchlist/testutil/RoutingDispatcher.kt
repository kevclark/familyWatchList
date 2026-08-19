package org.seg7.familywatchlist.testutil

import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest

/**
 * Routes MockWebServer responses by request path prefix rather than `enqueue()`'s FIFO order.
 * Needed once a test issues *concurrent* requests to different endpoints (PLAN.md §5a's gated
 * search fires one availability check per result, in parallel) — with plain `enqueue()`, whichever
 * request happens to reach the server first gets the next queued response, regardless of which
 * endpoint it actually hit, which makes the response mapping to the wrong title a flaky race
 * rather than a deterministic mapping.
 */
class RoutingDispatcher(private val routes: Map<String, () -> MockResponse>) : Dispatcher() {
    override fun dispatch(request: RecordedRequest): MockResponse {
        val path = request.url.encodedPath
        val route = routes.entries.firstOrNull { (prefix, _) -> path.startsWith(prefix) }
        return route?.value?.invoke() ?: MockResponse(code = 404)
    }
}
