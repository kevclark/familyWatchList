# 0012. Show 30 more on For You and Family Night

- **Status:** Accepted
- **Date decided:** 2026-10-09
- **Provenance:** **SOURCED** (decision text found in the written record, linked under Source)

## Context

Kev (alpha.15) saw the same picks after a refresh. Scoring is deterministic, so unchanged taste and availability return the same top 30.

## Decision

M16 (`8155048`): an end card on both rows reading "Show 30 more" over a muted "31-60 of 294". Tapping appends the next 30 ranked titles in rank order, keeps scroll position, repeats until the pool is exhausted. Extra batches are view-only and in-memory: they never change the persisted 30-title shortlist, refresh diff, New badges or log, and reset on profile change or refresh. Same filters as the top 30 (availability, age cap, scope-keyed dismissals, watched). For You scores on demand using the same pipeline (`scoreProfilePool`), with ranks 31+; Family Night gets the rest of the ranked pool from the ad-hoc blend. Missing details go through the throttled `ensureFresh` path with a loading card. The pager is pure (`ExtraPicksPager`).

## Alternatives considered

- A pager and shuffle: offered, declined by Kev.
- Persist the extra batches: rejected, it would alter the diff and badges.

## Consequences

- Reuse, not a fork, of the scoring code. No schema change.
- The first tap on For You can take time (on-demand scoring).

## Source

[PLAN.md section 5e](../../PLAN.md); M16 in [PROGRESS.md](../../PROGRESS.md); commits `e421636`, `8155048`, `efbaa2f`; `data/recommend/ExtraPicks.kt`. Decided by Kev, 2026-10-09.
