# 0022. Family becomes a fully independent profile

- **Status:** Accepted
- **Date decided:** 2026-08-22
- **Provenance:** **SOURCED** (decision text found in the written record, linked under Source)

## Context

During his live-review pass (PLAN 5b) Kev found the blend design incoherent once profile scoping spread across the app (per-viewer age-cap dimming, My List scoping): "not making sense now that we've added so much profile scoping".

## Decision

M3j (Kev, confirmed 2026-08-22): Family is symmetric with every individual profile except its age cap, which stays derived from members. Watchlist add and rating are unblocked for Family (no schema change, since those entities have no foreign key to `profiles`). Family gets its own independent history and runs through the same `refreshProfileShortlist(profileId, region)` pipeline with the sentinel id, using the generic cold-start threshold (fewer than 5 of its own events), which replaced `familyIsColdStart`. Logging a watch while Family is active tags Family **and** every member. My List, Home carousels and the History filter work for Family. The ad-hoc Family Night blend is explicitly unaffected.

## Alternatives considered

- Keep the blend as designed in ADR 0021: Kev judged it incoherent.
- Give Family its own settable age cap: no, the derived cap is the one thing it inherits.

## Consequences

- Family's quality depends on watches logged while it was active.
- The Tune my picks screen is enabled for Family (M3k).
- PLAN section 4's earlier Family-blend paragraphs remain in the file as history; this one supersedes them.

## Source

[PLAN.md section 4b](../../PLAN.md); M3j and M3k in [PROGRESS.md](../../PROGRESS.md); commits `e7a3512`, `5d3cb70`, `05d087d`, `38f928e`, `ca1a1e4`. Decided by Kev, 2026-08-22.
