# 0025. No runtime AI or LLM; the TMDB cache is never exported

- **Status:** Accepted
- **Date decided:** 2026-08-16
- **Provenance:** **SOURCED** (decision text found in the written record, linked under Source)

## Context

TMDB's terms (sections 1.C and 2.A) restrict using its data to train or validate ML or AI systems or to harvest datasets for that. Kev also set a hard rule for search in M17: no inference or AI in the app.

## Decision

Reviewed 2026-08-16: the recommender is a deterministic hand-written scoring function with fixed weights, so it stays outside the clause. Guardrails: never add runtime LLM features fed by TMDB data, and never export or share the TMDB cache (backup is user data only, ADR 0023). Kev restated it for descriptive search on 2026-11-07 (ADR 0007).

## Alternatives considered

- An LLM-based search interpreter: rejected by Kev and by the terms review.

## Consequences

- Features like query understanding must be explicit lookup tables; capability grows only by editing them.
- The Claude agents that built the app are development tools, not part of the product at runtime.

## Source

[PLAN.md section 8](../../PLAN.md) (TMDB AI/ML clause) and section 5f (hard constraint, Kev 2026-11-07).
