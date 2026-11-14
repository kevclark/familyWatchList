# 0001. Offline-first: the UI reads Room only, TMDB fills the cache

- **Status:** Accepted
- **Date decided:** 2026-08-16
- **Provenance:** **SOURCED** (decision text found in the written record, linked under Source)

## Context

The app must feel like a streaming app on a phone that is often offline or on poor signal, and TMDB is a rate-limited third-party API. Screens that wait on the network would be slow and fragile.

## Decision

Room (SQLite) is the single source of truth. ViewModels read only from Room through repositories; the network layer fills and refreshes Room, never the UI directly. TTLs (PLAN section 3): title metadata 30 days, watch-provider rows 7 days, discover/candidate pages 24 h per query hash (`discover_cache` table). Stale rows refresh lazily on view and in the weekly job. `append_to_response=credits,keywords,videos,watch/providers,release_dates` means one call fills a title completely. A search or discover result persists as a stub row, and a stub is always treated as stale so a detail fetch happens before it is trusted (`isStubOnly`).

## Alternatives considered

- Network-first screens with a memory cache: rejected, poor offline behaviour and many more TMDB calls.
- Cache-only filtering of availability: rejected for search (see ADR 0006).

## Consequences

- Everything in the UI works from cache; first-use views of uncached titles settle progressively.
- Every new feature must say where its data is cached. Later features follow this: New rows reuse `discover_cache` (ADR 0014), More like this holds recommendation lists in memory per process (ADR 0009).
- TTL constants live in `TitleRepository` (`METADATA_TTL_MS` 30 d, `PROVIDER_TTL_MS` 7 d), matching the plan.
- CLAUDE.md restates the rule as part of the quality bar.

## Source

[PLAN.md section 1](../../PLAN.md) and [section 3](../../PLAN.md) (approved with Kev on 2026-08-16); [CLAUDE.md](../../CLAUDE.md) quality bar; M1 commit `5af6754` (Room schema and caching); `TitleRepository.kt` TTL constants. Decided by Kev's approval of the plan, written by the Fable orchestrator.
