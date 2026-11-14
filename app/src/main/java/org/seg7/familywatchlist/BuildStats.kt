package org.seg7.familywatchlist

/**
 * PLAN.md has no section for this — it's a Kev-requested M7 add-on (PROGRESS.md "M7 — In-app
 * 'About this build' AI-transparency screen"), not app-domain data. This describes *how the app
 * itself was built* (mined from this project's Claude Code session transcripts), not anything
 * computable at runtime from Room/TMDB/user data — so it's baked in as static values the same way
 * [BuildConfig.VERSION_NAME] is a static build-time fact, not a live counter. It will go stale as
 * development continues past [SNAPSHOT_DATE]; that's expected, same as a version number going
 * stale until the next bump — see [AboutStats] for the point-in-time framing shown in the UI.
 */
object BuildStats {

    /**
     * ISO date the numbers below were last re-derived from the session transcripts. 2026-11-14 refresh: the
     * 2026-11-07 figures are carried forward and everything after that date added.
     *
     * Method, background agents (unchanged from 2026-11-07): per-dispatch duration, tokens and tool calls from
     * `<task-notification>` blocks (or the agent's own transcript where none exists), joined to each agent's
     * recorded model. Added since: two Sonnet 5.5 builds, alpha.18 (1,588 s, 149,585 tokens, 55 tool calls) and
     * alpha.19 (480 s, 85,666 tokens, 23 tool calls). The alpha.20 build is NOT counted: its duration and token
     * use can't be measured until it has finished, and a figure written during the build would be an estimate.
     *
     * Method, orchestrator rows (new 2026-11-14; the page previously counted only background agents): the
     * top-level `.jsonl` transcript of the main session (subagent transcripts live elsewhere), aggregate numbers
     * only. One row per `model` seen on assistant messages.
     *  - messages: distinct assistant message ids (streamed duplicates collapsed); the synthetic placeholder
     *    message with zero tokens is ignored.
     *  - tokens: `input_tokens + output_tokens + cache_creation_input_tokens`. Cache reads are excluded: the same
     *    context is re-read on every turn, so including them would inflate the figure by ~40x.
     *  - active hours: every user/assistant entry's timestamp, sorted; the gap to the previous entry counts only
     *    if it is at most 10 minutes, and is attributed to the model of the next assistant message. The rows
     *    therefore sum to the whole active time (16.9 h). Orchestrator hours run in parallel with agent hours
     *    (the orchestrator is working or waiting while agents build), so they are shown separately and are NOT
     *    added into [TOTAL_HOURS], which stays "cumulative background-agent build time".
     *
     * Prompts: user-typed messages in the main transcript, excluding tool results, task notifications, system
     * and command injections and meta entries. 249 at 2026-11-07, plus 5 typed after that snapshot.
     */
    const val SNAPSHOT_DATE: String = "2026-11-14"

    /** Total prompts across the project's whole continuous session plus one post-reboot recovery session. */
    const val TOTAL_PROMPTS: Int = 254

    /** Total background agent dispatches across all models (48 at 2026-11-07, plus alpha.18 and alpha.19). */
    const val TOTAL_AGENT_TASKS: Int = 50

    /** Cumulative build time across all background agent dispatches, in hours (17.0 + 2,068 s = 17.6). */
    const val TOTAL_HOURS: Double = 17.6

    /**
     * One row per model. Background-agent rows have a [taskCount]; orchestrator rows (the main session, see
     * [isOrchestrator]) have a [messageCount] instead and [hours] means active time.
     */
    data class ModelStats(
        val modelName: String,
        val taskCount: Int?,
        val hours: Double,
        val tokens: Long?,
        val toolCalls: Int?,
        val usedFor: String,
        val messageCount: Int? = null,
        val isOrchestrator: Boolean = false,
    )

    val MODEL_BREAKDOWN: List<ModelStats> = listOf(
        ModelStats(
            modelName = "Claude Opus 5",
            taskCount = 5,
            hours = 2.8,
            tokens = 908_000L,
            toolCalls = 404,
            usedFor = "Judgment-heavy investigation, debugging, and creative/visual design work — " +
                "toolchain/environment setup, the emulator SIGSEGV crash investigation, the M2b " +
                "visual redesign pass, and app icon concept design.",
        ),
        ModelStats(
            modelName = "Claude Sonnet 5",
            taskCount = 39,
            hours = 13.2,
            tokens = 8_950_000L,
            toolCalls = 5_345,
            usedFor = "The great majority of milestone feature implementation — well-specified, " +
                "routine build work once a design/investigation decision was already made.",
        ),
        ModelStats(
            modelName = "Claude Sonnet 5.5",
            taskCount = 5,
            hours = 1.5,
            tokens = 823_251L,
            toolCalls = 252,
            usedFor = "Milestone builds from M15 on (refresh visibility, show-more paging, descriptive search, " +
                "theme search, More like this) after the project moved to Sonnet 5.5.",
        ),
        ModelStats(
            modelName = "Claude Haiku 4.5",
            taskCount = 1,
            hours = 0.6 / 60.0,
            tokens = 26_000L,
            toolCalls = null,
            usedFor = "A single lightweight session-recovery check after a machine reboot.",
        ),
        ModelStats(
            modelName = "Claude Sonnet 5 (orchestrator)",
            taskCount = null,
            messageCount = 1_262,
            hours = 13.0,
            tokens = 12_656_716L,
            toolCalls = null,
            isOrchestrator = true,
            usedFor = "The main session for most of the project: reading the spec, briefing and reviewing " +
                "the background agents, verifying on the emulator and talking with Kev.",
        ),
        ModelStats(
            modelName = "Claude Opus 5.5 (orchestrator)",
            taskCount = null,
            messageCount = 180,
            hours = 2.0,
            tokens = 1_029_518L,
            toolCalls = null,
            isOrchestrator = true,
            usedFor = "The main session when Kev switched up for harder asks.",
        ),
        ModelStats(
            modelName = "Claude Sonnet 5.5 (orchestrator)",
            taskCount = null,
            messageCount = 32,
            hours = 0.6,
            tokens = 392_127L,
            toolCalls = null,
            isOrchestrator = true,
            usedFor = "The main session after the project moved to Sonnet 5.5 for orchestration.",
        ),
        ModelStats(
            modelName = "Claude Fable 5 (orchestrator)",
            taskCount = null,
            messageCount = 57,
            hours = 1.3,
            tokens = 236_300L,
            toolCalls = null,
            isOrchestrator = true,
            usedFor = "The main session for a short stretch before Fable became unavailable.",
        ),
    )
}
