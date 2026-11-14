# 0024. Tech stack: Kotlin, Compose, Room, manual DI, single module

- **Status:** Accepted
- **Date decided:** 2026-08-16
- **Provenance:** **SOURCED** (decision text found in the written record, linked under Source)

## Context

A small family app built on a 16 GB headless box where Gradle build speed matters.

## Decision

Kotlin with Jetpack Compose (Material 3); Room with KSP; Retrofit, OkHttp and kotlinx.serialization; Coil 3; WorkManager; DataStore (Preferences); MVVM with repositories and unidirectional data flow; **manual dependency injection** via a single `AppContainer` (no Hilt, to avoid KSP overhead); a single Gradle module `app`. minSdk 26; compileSdk 35 raised to 37 with AGP 9 (approved by Kev after M0). Design tokens (near-black, accent default Obsidian `#8B5CF6`) were locked after Kev's M2a review (PLAN 5a).

## Alternatives considered

- Hilt: slower builds for a small app.
- Multi-module: ceremony not worth it at this size.
- Embedded trailer player library: replaced by ADR 0027's WebView.

## Consequences

- ViewModels are constructed by hand from the container; process-scoped repositories hold in-memory caches (ADR 0009).
- Robolectric DAO tests pin SDK 34 because JDK 17 is installed (test-harness ceiling only).

## Source

[PLAN.md section 1](../../PLAN.md) and header notes; `di/AppContainer.kt`; commits `445f210` (M0), `5af6754` (M1). Decided by Kev with the Fable orchestrator, 2026-08-16.
