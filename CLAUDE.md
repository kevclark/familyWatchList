# Family Watchlist — project instructions

Android app (Kotlin + Jetpack Compose) that tracks what the family watched, learns taste
locally, and recommends titles available on our UK streaming services via TMDB.

## Model & billing
Default orchestrator is **Sonnet 5** (`/model sonnet`); Fable bills Kev's usage credits and
he has okayed spending them on this project when its judgment genuinely helps — but routine
implementation work never needs it. Delegate to the pinned subagents instead of doing work inline:
- `toolchain-setup` (Opus 4.8) → JDK/SDK/Gradle/emulator/ADB, anything environment
- `feature-builder` (Sonnet 5) → all app code and tests, one milestone task at a time

## Source of truth
- `PLAN.md` — the approved spec (data model §2, TMDB §3, recommender §4, screens §5,
  milestones §7). Deviations need Kev's sign-off, not silent improvisation.
- `PROGRESS.md` — living checklist; update it as milestones advance so cold sessions resume.
- `docs/PREVIEW.md` — how Kev views the app from his laptop (scrcpy) or phone (wireless ADB).

## Environment facts
- Host `agent101`: Ubuntu 26.04, 16GB RAM, headless SSH, **fish** shell, inside tmux.
- **No sudo.** Toolchain lives in `~/android-dev/`; bash tooling must `source env.sh` first.
- `/dev/kvm` access confirmed (Kev is in the kvm group) — the emulator needs no sudo.
- `TMDB_ACCESS_TOKEN` lives in git-ignored `local.properties`; never commit or print it.
- Don't run full Gradle builds and emulator first-boot simultaneously (RAM).

## Quality bar
- Every task ends with `./gradlew test assembleDebug` green — report actual output.
- Offline-first: UI reads Room only; TMDB fills the cache (TTLs in PLAN.md §3).
- JustWatch attribution on every availability UI; TMDB attribution in About (terms require it).
- UK region (`watch_region=GB`) everywhere; max 10 profiles enforced in the repository layer.
