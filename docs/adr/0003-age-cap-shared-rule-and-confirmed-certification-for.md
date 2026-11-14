# 0003. Age cap: shared rule, and confirmed certification for capped Home rows

- **Status:** Accepted
- **Date decided:** 2026-08-21
- **Provenance:** **SOURCED** (decision text found in the written record, linked under Source)

## Context

Profiles may carry an `ageRatingCap` (U, PG, 12, 15, 18). The M3g audit (2026-08-21) found the "age-filtered" promise for the Popular row and cold-start hero had never been implemented: a child's profile could be shown an 18-rated title. TMDB `/discover` results are bare stubs with no certification, so a title that is over-cap is indistinguishable from one that is unknown unless its details were cached. Force-fetching every candidate's certification would cost up to about 240 throttled calls.

## Decision

1. One shared cert-rank check (`FamilyBlend.isOverCap`, `CERT_RANK`, which includes the M12 12A and Amazon equivalents) is used everywhere a title is surfaced for a scope. No second implementation. M3g (`b99c7a9`, 2026-08-21) applied it to Popular, the cold-start hero and Search.
2. M3h (Kev, 2026-08-21, `a94ca9d`): for a profile with a non-null cap, Popular and the cold-start hero require **confirmed** certification at or under the cap. This excludes both over-cap and unknown-certification titles. Zero extra network cost, since it only narrows already-fetched rows. Uncapped profiles are unaffected.
3. M11 (2026-08-23, `2587991`) widened the same rule to the shared warm recommender (`scoreCandidates`), by extracting it as `FamilyBlend.isConfirmedUnderCap`.
4. **Unknown is not unsafe elsewhere.** `isOverCap` itself stays permissive for unknown or unrecognised certifications. It is used for Search, My List dimming and the descriptive-search `AgeFilter`, where unrated titles (common for UK TV) are hidden at a cap of 12 or below and shown with an "Unrated" label above that.
5. My List shows an over-cap item dimmed with a reason rather than hiding it (Kev, 2026-08-22).

## Alternatives considered

- Let unknown certifications through everywhere (the original behaviour): rejected for capped Home rows because a fresh install is exactly when a new child's profile is set up.
- Force-fetch certifications before rendering: rejected, a multi-minute stall.
- Hide unknown certifications everywhere: rejected, it would blank most UK TV in Search.

## Consequences

- A capped profile's Home can be sparse until titles have been opened once. Kev accepted this: "showing nothing is a fine outcome, showing something wrong isn't".
- The M3h rule is no longer scoped to only Home: the plan text says the warm recommender keeps "unknown is not unsafe", but M11 changed that. The code (`isConfirmedUnderCap`) is the truth now.
- New rows needed certification enrichment for capped profiles (ADR 0014).

## Source

[PLAN.md section 4](../../PLAN.md), "Age-cap safety gap" and "Residual gap found by M3g, resolved by Kev 2026-08-21"; M3h in [PROGRESS.md](../../PROGRESS.md); commits `b99c7a9`, `a94ca9d`, `2587991`, `81b13e0` (M12); `FamilyBlend.kt`. Decided by Kev, 2026-08-21.
