# 0023. Backup and restore: one versioned user-data JSON via the Storage Access Framework

- **Status:** Accepted
- **Date decided:** 2026-08-16
- **Provenance:** **SOURCED** (decision text found in the written record, linked under Source)

## Context

The family's logged history and lists live only on the phone, and a phone change should not lose it. TMDB content is refetchable.

## Decision

Settings export and import a single JSON file through the Storage Access Framework (`BackupRepository`, versioned `BackupPayload`, `CURRENT_VERSION = 1`). It holds user data only: profiles, the family profile and members, watch events, ratings, watchlist entries, subscribed providers, slider values, notification preferences and user preferences. The TMDB cache is excluded, which also keeps the app clear of the TMDB AI clause (ADR 0025). Tests cover round trip, malformed file, wrong version and corrupt references. `72a5cdc` fixed a restore that rejected exports containing Family-owned data (after ADR 0022).

## Alternatives considered

- Room database file copy: ties the backup to a schema version and includes the cache.
- Cloud sync: not requested, adds accounts.

## Consequences

- Backup includes slider and notification data that the original plan list did not name; the code is the current truth.
- A format change needs a new `version` and a migration path in restore.

## Source

[PLAN.md section 2](../../PLAN.md) (Backup/restore) and section 5 (Settings); M4b in [PROGRESS.md](../../PROGRESS.md); commits `5240f36`, `72a5cdc`; `BackupModels.kt`. Decided in the approved plan; contents cross-checked against the code.
