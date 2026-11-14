# 0019. Region defaults to GB (UK) but is a preference, not a hardcode

- **Status:** Accepted
- **Date decided:** 2026-08-19
- **Provenance:** **SOURCED** (decision text found in the written record, linked under Source)

## Context

The app targets a UK family and CLAUDE.md says `watch_region=GB` everywhere. TMDB does no IP geolocation: the region is an explicit parameter our code sends, so a family travelling abroad would be stuck on UK results.

## Decision

GB is the default and the plan's baseline (provider seed, onboarding defaults Netflix, Disney+, Prime Video, iPlayer, Channel 4, ITVX). Kev asked (2026-08-19) for region to be configurable: it is a `UserPreferencesRepository` preference threaded into every discover, detail, search and watchlist call, with a Settings picker sourced from TMDB `/watch/providers/regions`. Subscribed-provider ids are region-specific, so changing region shows a dismissible notice asking to revisit services rather than auto-clearing them.

## Alternatives considered

- Hardcode GB: simplest, but breaks abroad.
- Detect region from the device: not what TMDB does and not requested.

## Consequences

- CLAUDE.md's wording "UK region everywhere" describes the default; the code reads the preference. M2f fixed a stale cross-region cache bug.
- A GB to US to GB round trip was verified live.

## Source

[PLAN.md section 8](../../PLAN.md) ("Region should be configurable, not hardcoded"); M2f in [PROGRESS.md](../../PROGRESS.md); [CLAUDE.md](../../CLAUDE.md). Requested by Kev, built by feature-builder, 2026-08-19.
