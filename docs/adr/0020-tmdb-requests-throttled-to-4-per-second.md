# 0020. TMDB requests throttled to 4 per second

- **Status:** Accepted
- **Date decided:** 2026-08-16
- **Provenance:** **SOURCED** (decision text found in the written record, linked under Source)

## Context

TMDB's stated limit is about 50 requests per second, but search-then-check, scoring and refreshes fan out into many detail calls, and the app should be a polite client.

## Decision

An OkHttp `ThrottleInterceptor` allows at most 4 requests per rolling second, blocking the calling thread when the window is full, and retries on HTTP 429 honouring Retry-After (up to 3 retries). Time is behind a `RateLimiterClock` seam so tests do not sleep. The plan says to behave as if the limit is tight.

## Alternatives considered

- Rely on TMDB's real ceiling: rejected as impolite and brittle.
- A per-feature limiter: the single interceptor covers every caller.

## Consequences

- Large fan-outs are slow by design: a 294-title Family Night run took minutes and drove the staged progress UI (ADR 0010) and show-more paging.
- Every feature's cost estimate is in throttled calls.

## Source

[PLAN.md section 3](../../PLAN.md); M1 commit `5af6754`; kdoc and defaults in `ThrottleInterceptor.kt`. Decided in the approved plan.
