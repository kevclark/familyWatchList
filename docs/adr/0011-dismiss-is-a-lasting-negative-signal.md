# 0011. Dismiss is a lasting negative signal

- **Status:** Accepted
- **Date decided:** 2026-09-11
- **Provenance:** **SOURCED** (decision text found in the written record, linked under Source)

## Context

Kev asked whether long-press dismiss feeds the algorithm. It did not. Dismissals only suppressed a title for the current week although the dialog promised "won't be suggested to you again", Family Night's ad-hoc dismissals were written under a scope key nothing read, and dismissing never touched the affinity vector.

## Decision

M13 (`edefd84`): dismissals are suppressed permanently, since DISMISSED rows are read without a `weekStart` filter (`getDismissedForScope`). Family Night dismissals persist under `adHocScopeKey`. Dismissing feeds the affinity vector as a `DismissSignal` with weight `-0.6`, the mirror of the watchlist signal `+0.6`, weaker than a DOWN rating (-0.8), recency-weighted. This applies only to real per-profile vectors (a real profile, or Family's own), never to the ad-hoc Family Night blend, because a group dismissal does not mean each member dislikes the title.

## Alternatives considered

- Keep pure suppression: contradicts the dialog copy.
- Feed ad-hoc dismissals into every member's vector: rejected, wrong inference about individuals.

## Consequences

- `dismissedAt` uses the row's `weekStart` as a proxy, off by less than a week.
- IDF damping corpus stays watched-titles-only.
- Dismissed titles are excluded from show-more batches and New rows too.

## Source

[PLAN.md section 4c](../../PLAN.md); M13 in [PROGRESS.md](../../PROGRESS.md); commits `760c83f`, `edefd84`, `805ba32`; `AffinityEngine.DISMISS_SIGNAL_WEIGHT`. Decided by Kev, 2026-09-11.
