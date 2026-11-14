# 0028. Explicit Room migrations, schemas exported, no destructive fallback

- **Status:** Accepted
- **Date decided:** 2026-08-22
- **Provenance:** **INFERRED** (reconstructed from code only; no written decision found)

## Context

User data (watches, ratings, lists) lives only in Room on the phone, and the schema has changed many times (at least version 11).

## Decision

Inferred from code only: the database is built with explicit `addMigrations(...)` and exported schemas (`exportSchema = true`), and no `fallbackToDestructiveMigration` appears anywhere in the source. Migration tests accompany schema changes (e.g. the `MIGRATION_10_11` test in M15). Changes needing a data reset use a real migration, e.g. `MIGRATION_7_8` and `MIGRATION_8_9` reset `titles.fetchedAt` to re-trip stale caches. Migrations are numbered in `AppDatabase`; `MIGRATION_10_11` shipped with M15.

## Alternatives considered

- Destructive fallback: would erase the family's history on a schema bump. Not found in code, so presumably avoided deliberately.

## Consequences

- Every entity change needs a migration plus a test; a migration only runs when the app version changes (found on Kev's phone, `0d9a8b9`).
- PROGRESS.md mentions an unrelated, unshipped `MIGRATION_10_11` in a `git stash`; the shipped one is M15's.
- No written decision was found for this policy, so Kev should confirm it.

## Source

Code only: `di/AppContainer.kt` (`addMigrations`), `data/local/AppDatabase.kt` (`exportSchema = true`); related commits `8203297`, `0d9a8b9`, `a1ad62b`. Mentioned in passing in [PROGRESS.md](../../PROGRESS.md) M15 and PLAN 5d; no explicit decision record.
