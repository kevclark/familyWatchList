# 0006. Search is search-then-check, gated to subscribed-service availability

- **Status:** Accepted
- **Date decided:** 2026-08-19
- **Provenance:** **SOURCED** (decision text found in the written record, linked under Source)

## Context

M2b built Search as a general TMDB catalogue finder, an unvalidated inference the orchestrator had presented as settled. Kev found Spider-Man: No Way Home reachable with no UK availability and said the app's premise is "only show me what's available on the services I actually pay for".

## Decision

Search results and the watchlist add-gate require availability on a subscribed provider in the active region. `/search/multi` has no provider filter, so Search runs `/search/multi`, resolves each result's availability (cache first, then a throttled fetch), and drops non-available titles. Results settle progressively; in-flight checks for a stale query are cancelled. Adding to the watchlist is blocked with an inline message when unavailable, but existing entries are never auto-removed (they are dimmed, with an explicit clean-up action). Logging a watch and History are explicitly **not** gated. Search with zero subscribed services shows an explanatory message.

## Alternatives considered

- Cache-only filtering: inaccurate, hides available titles that have not been checked.
- Unfiltered search with an "on your services" chip: Kev wants the restriction as the default.

## Consequences

- A few seconds of progressive fill for an uncached page; accepted by Kev.
- `AvailabilityGate` became the reused building block for M11, structured search and More like this.
- Widened for rent/buy in ADR 0004.

## Source

[PLAN.md section 5a](../../PLAN.md), "Search & watchlist availability gating (Kev's review, 2026-08-19)"; M2d and M2g in [PROGRESS.md](../../PROGRESS.md); commits `526cbd3` (spec), `5b59f91`, `a8138ef`. Decided by Kev.
