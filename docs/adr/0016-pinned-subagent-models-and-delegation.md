# 0016. Pinned subagent models and delegation

- **Status:** Accepted
- **Date decided:** 2026-10-09
- **Provenance:** **SOURCED** (decision text found in the written record, linked under Source)

## Context

Work is split between an orchestrating session and subagents to control cost and keep the environment separate from app code. Model availability changed: Kev's Fable usage credits ran out.

## Decision

`feature-builder` is pinned to `claude-sonnet-5-5` and does all app code and tests, one milestone task at a time. `toolchain-setup` is pinned to `claude-opus-5-5` and does anything environment-related (JDK, SDK, Gradle, emulator, ADB). The orchestrator defaults to Sonnet 5.5 and Kev switches to Opus 5.5 himself for harder asks. **Fable is no longer available**; it must never be picked or suggested. History: on 2026-08-16 the pins were `claude-opus-4-8` (toolchain) and `claude-sonnet-5` (builder), with occasional Fable orchestration for judgement calls. Both agents must finish a task with `./gradlew test assembleDebug` green and report real output.

## Alternatives considered

- Orchestrating on a larger model by default: too costly.
- One agent for everything: rejected; the split keeps toolchain changes out of feature work.

## Consequences

- The `.claude/agents/*.md` frontmatter is the source of truth for the current pins; models change over time.
- Older commits and PLAN text mention Opus 4.8, Sonnet 5 and Fable and are historical.
- The About screen's BuildStats reports per-model usage.

## Source

[CLAUDE.md](../../CLAUDE.md) (Model & billing); `.claude/agents/feature-builder.md` and `.claude/agents/toolchain-setup.md`; commits `3d8a815` and `a8d9312` (2026-10-09); [PLAN.md section 0](../../PLAN.md) (original pins). Decided by Kev, 2026-10-09.
