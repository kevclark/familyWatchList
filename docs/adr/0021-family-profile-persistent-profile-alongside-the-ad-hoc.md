# 0021. Family profile: persistent profile alongside the ad-hoc Family Night blend

- **Status:** Superseded by 0022 (in part)
- **Date decided:** 2026-08-21
- **Provenance:** **SOURCED** (decision text found in the written record, linked under Source)

## Context

Households have a standing combination of people who usually watch together, in addition to the ad-hoc "who's watching tonight?" subset picker (M3c).

## Decision

M3d (Kev, 2026-08-21): a singleton Family profile is selectable as the active profile with its own Home. Membership (2 or more, editable, cascade on member deletion) is picked from existing profiles. The active profile is distinguished with the reserved sentinel `FAMILY_PROFILE_SENTINEL_ID = -1L`, since real Room ids are positive, with no change to the Profile table. Its age cap is derived (strictest member) and never settable. Logging a watch while Family is active auto-tags every member. Its shortlist came from the mean/min blend of member vectors (slider 4 "Everyone's happy" to "Average taste wins", visible with 2 or more profiles). The ad-hoc Family Night chip row (`refreshFamilyShortlist`, persist false) keeps the blend permanently. The weekly job refreshes every profile including Family.

## Alternatives considered

- Treat "Family" as just a multi-select: that is what the ad-hoc path already is.
- A real `Profile` row for Family: would count against the 10 cap and touch the cap logic.

## Consequences

- Superseded in part: the blend for Family's own persisted shortlist, the block on watchlist add and rating, and `familyIsColdStart` were reversed by ADR 0022 on 2026-08-22. The ad-hoc blend, sentinel, membership and derived cap remain current.

## Source

[PLAN.md section 4](../../PLAN.md), "The Family profile (Kev, 2026-08-21)" and the ad-hoc blend paragraph; M3c and M3d in [PROGRESS.md](../../PROGRESS.md); commits `c273775`, `1d5e0f3`, `7849ec5`, `6fc3d2d`, `d4807cd`; `FamilyProfileEntity.kt`. Decided by Kev.
