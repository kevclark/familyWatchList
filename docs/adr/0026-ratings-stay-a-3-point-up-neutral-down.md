# 0026. Ratings stay a 3-point UP / NEUTRAL / DOWN scale

- **Status:** Accepted
- **Date decided:** 2026-08-22
- **Provenance:** **SOURCED** (decision text found in the written record, linked under Source)

## Context

Kev proposed a richer double-up/double-down scale during his live review.

## Decision

The scale stays UP, NEUTRAL, DOWN, per person and per title with the latest write winning. The orchestrator researched Netflix and Prime: Netflix dropped 5-star ratings in 2017 because users misread stars and binary thumbs got far more ratings; its 2022 "Two Thumbs Up" is asymmetric, and Prime is binary. Weights in the affinity engine are unchanged (+1.0, +0.4, -0.8). Decided by Kev, 2026-08-22 (originally locked 2026-08-16).

## Alternatives considered

- 5-star ratings; symmetric double thumbs: neither platform runs these.

## Consequences

- No schema migration. History now displays the existing rating (5b).
- Ratings are the dominant taste input along with watches, so rating discipline matters for quality.

## Source

[PLAN.md header](../../PLAN.md) (2026-08-16) and section 5b, "Rating scale: RESOLVED"; `AffinityEngine.kt`.
