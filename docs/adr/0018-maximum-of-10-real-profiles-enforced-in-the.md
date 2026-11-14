# 0018. Maximum of 10 real profiles, enforced in the repository

- **Status:** Accepted
- **Date decided:** 2026-08-16
- **Provenance:** **SOURCED** (decision text found in the written record, linked under Source)

## Context

Family size varies, and a profile picker and per-profile Home need a sensible bound.

## Decision

A hard cap of 10 real-person profiles, enforced in `ProfileRepository` (`MAX_PROFILES = 10`, `MaxProfilesReachedException`), not just in the UI. The Family profile is a separate concept and does not count against it.

## Alternatives considered

- UI-only limit: bypassable by other callers, such as restore.
- No limit: unbounded per-profile refresh cost.

## Consequences

- Each profile adds a weekly shortlist computation and API cost, so the limit also bounds refresh time.
- Backup restore must respect it.

## Source

[PLAN.md section 2](../../PLAN.md) (Profile) and section 5 (Profile picker); CLAUDE.md quality bar; `ProfileRepository.kt`. Decided in the approved plan.
