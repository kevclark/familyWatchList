# 0009. More like this uses TMDB /recommendations, not /similar

- **Status:** Accepted
- **Date decided:** 2026-11-07
- **Provenance:** **SOURCED** (decision text found in the written record, linked under Source)

## Context

Kev asked (2026-11-07) whether TMDB can provide similar titles at the bottom of a title detail screen.

## Decision

A "More like this" poster row at the bottom of the detail screen from page 1 of `movie|tv/{id}/recommendations` (the same endpoints the recommender uses), not `/similar`. The orchestrator compared both live on 2026-11-07 with Slow Horses: recommendations gave Spooks, The Night Manager and The Capture; `/similar` gave unrelated titles. Gating is shared with structured search (`passesBrowseGate`): availability on a subscribed provider, no rent/buy-only, then the age filter with the usual unrated rule. `MoreLikeThisRepository` checks 5 at a time and emits in TMDB order. Lists are cached in memory per `(type, id)` in the process-scoped repository; per-title checks are already Room-cached. No schema change. The row hides when nothing passes or on failure.

## Alternatives considered

- `/similar`: rejected on the live comparison.
- Computing similarity locally: rejected, no inference and extra cost.

## Consequences

- The recommendations endpoint has no region concept, hence the availability gate. The same fact caused the M11 gap in the recommender (stubs bypassed availability).
- Header may flash and vanish when no recommendation passes. A failed fetch is not retried until the screen reopens.

## Source

[PLAN.md section 5h](../../PLAN.md); "Queued for alpha.19" in [PROGRESS.md](../../PROGRESS.md); commits `fede828`, `fa5d8ae`. Decided by Kev (the ask) and the orchestrator (the live endpoint comparison), 2026-11-07.
