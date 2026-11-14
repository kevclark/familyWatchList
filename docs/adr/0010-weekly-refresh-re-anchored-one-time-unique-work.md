# 0010. Weekly refresh: re-anchored one-time unique work, not periodic work

- **Status:** Accepted
- **Date decided:** 2026-10-09
- **Provenance:** **SOURCED** (decision text found in the written record, linked under Source)

## Context

The weekly refresh was a 7-day `PeriodicWorkRequest` (PLAN section 4, made configurable by M3f in `7ed8ecf`). On his phone Kev reported the Friday notification not always arriving. Only the first periodic run honours the initial delay; later runs are 7 days after the previous actual run, so Doze, standby buckets or constraints carry lateness forward and the slot drifts. Nothing recorded whether it ran.

## Decision

M15 (Kev confirmed all parts, 2026-10-09, `ec239e2`):
- A unique **one-time** request targets the next configured day and hour. At the end of every run, success or final failure, the worker enqueues the next slot, so lateness never accumulates. `ExistingWorkPolicy.KEEP` on app start (routine launches never reset a pending run), `REPLACE` on a Settings change and when the worker re-books. The old periodic unique work is cancelled on upgrade so two jobs never run. Exact alarms were offered and declined.
- **Catch-up rule:** on app foreground, if the most recent scheduled slot has passed and no successful refresh happened since, run the same refresh in-process. Home shows a slim "Refreshing picks" banner (later with the profile name and "n of m") and "Picks updated" afterwards. One shared `RefreshCoordinator` Mutex guard covers scheduled, catch-up and pull-to-refresh, so two refreshes never run at once.
- A `refresh_log` table (`MIGRATION_10_11`) and Activity screen record each run, per-profile new and dropped picks, and why a notification was posted or suppressed. A "New" badge marks picks not in the previous shortlist.
- Only the scheduled run posts the notification.

## Alternatives considered

- Keep periodic work: it was the cause of the drift.
- Exact alarms: offered, declined by Kev.
- Expedited work for catch-up: allowed by the spec, in-process was built.

## Consequences

- The first scheduled slot after install depends on the next run existing; the catch-up covers a missed one.
- Verification of a real scheduled Friday run was still pending in PROGRESS at the time of writing.
- The 7-day periodic text in PLAN section 4 is historical; the code (`RecommendationScheduler`) is the one-time design.

## Source

[PLAN.md section 5d](../../PLAN.md); M15 in [PROGRESS.md](../../PROGRESS.md); commits `1b4985c` (spec), `a1ad62b`, `ec239e2`, `8da8a2a`; kdoc in `RecommendationScheduler.kt`. Decided by Kev, 2026-10-09.
