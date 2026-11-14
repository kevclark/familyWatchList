# 0002. Recommender: deterministic, content-based, fully local

- **Status:** Accepted
- **Date decided:** 2026-08-16
- **Provenance:** **SOURCED** (decision text found in the written record, linked under Source)

## Context

The family wants suggestions from what they have actually watched and rated, on services they pay for. There is no budget or wish for a server, an account system or a trained model.

## Decision

A hand-written content-based scorer in Kotlin (PLAN section 4):
- Per-profile affinity vector over genres, keywords, top-10 cast and director/creator. Each watch adds `ratingWeight x recencyWeight` (UP +1.0, unrated/NEUTRAL +0.4, DOWN -0.8; 180-day half-life). ACTIVE watchlist entries add +0.6. IDF damping and L2 normalisation per attribute type.
- Candidates: `/discover` on subscribed providers plus TMDB `/recommendations` of the profile's top UP-rated titles.
- `score = 0.70 x affinityMatch + 0.15 x tmdbQuality + 0.15 x freshness`; weekly shortlist of about 30 (raised from 8 at Kev's request on 2026-08-20), max 2 per primary genre plus one wildcard slot.
- Fewer than 5 own watch events means cold start: a "getting started" panel and a "Popular on your services" row, never presented as personalised.
- Four tunable sliders plus a suggestion-count control (Kev, 2026-08-20). Slider value 0 must reproduce the fixed spec exactly, and a fixture test pins that.
- Family Night ad-hoc blend: `0.5 x mean + 0.5 x min` (least misery), strictest age cap.

## Alternatives considered

- A hosted or LLM recommender: rejected, see ADR 0025.
- Collaborative filtering: impossible with a handful of users.
- Pure TMDB popularity: this is the cold-start fallback only.

## Consequences

- Unit-testable with fixtures and zero running cost.
- Scoring is deterministic, so with unchanged taste and availability the same top 30 come back each time. That is why "Show 30 more" exists (ADR 0012).
- Scoring code is shared by the weekly job, slider recompute, Family Night, and show-more paging, so a safety rule added in one place applies everywhere (ADR 0003).
- Dismiss was later turned into a learning signal (ADR 0011).

## Source

[PLAN.md section 4](../../PLAN.md) and the 4a sliders subsection (Kev, 2026-08-20); M3 in [PROGRESS.md](../../PROGRESS.md); constants in `AffinityEngine.kt` and `RecommenderSpec.kt` (`COLD_START_EVENT_THRESHOLD = 5`). Decided by Kev with the orchestrator.
