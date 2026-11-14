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
     * ISO date the numbers below were last re-derived from the session transcripts. 2026-11-14 second refresh
     * (alpha.21): the About page gained a summary card and per-model prompt/reply/action counts, and every number
     * below was re-mined from the transcripts. The alpha.21 build itself (the agent dispatched at 16:44 UTC on
     * 2026-11-14, and anything the main session did after that dispatch) is NOT counted.
     *
     * Sources: the main session transcript (`<project>/<session>.jsonl`) and the background agents' transcripts
     * (`<session>/subagents/agent-*.jsonl`) under `~/.claude/projects/`, aggregate numbers only. The main
     * transcript is deduplicated by assistant message id (streamed duplicates collapse to one message); the
     * synthetic placeholder message is ignored.
     *
     * Definitions, main session (one row per assistant `model`):
     *  - Kev's prompts (summary card): user entries the harness itself marks `origin.kind = human`, i.e. typed,
     *    queued-while-busy or accepted-suggestion prompts. Tool results, task notifications, sub-agent hand-backs,
     *    slash commands, command output, compaction summaries and meta entries are not prompts. 235 at the cut-off.
     *    (The previous snapshot said 254; that figure also counted a short post-reboot recovery session whose
     *    transcript is empty on disk and so cannot be re-derived. The 235 is what the transcripts show.)
     *  - Prompts handled: a prompt belongs to the model of the first real assistant message that follows it. Per
     *    model these sum to the 235.
     *  - Replies to Kev: assistant messages whose `stop_reason` is `end_turn` and that carry a non-empty text
     *    block. That is the message that ends a turn with words for Kev. Narration written before a tool call
     *    does not count; it is part of an action step.
     *  - Actions in between: `tool_use` blocks (file reads, commands, edits, agent briefings, searches). A
     *    message that issues three parallel calls is three actions. Replies and actions are separate counts and
     *    do not sum to the number of messages.
     *  - Tokens: `input_tokens + output_tokens + cache_creation_input_tokens`. Cache reads are excluded: the same
     *    context is re-read on every turn, so including them would inflate the figure by ~40x.
     *  - Active hours: every user/assistant entry's timestamp, sorted; the gap to the previous entry counts only
     *    if it is at most 10 minutes, and is attributed to the model of the next assistant message. The rows sum
     *    to the whole main-session active time (17.4 h).
     *
     * Definitions, background agents (unchanged method): per-dispatch duration, tokens and tool calls from
     * `<task-notification>` blocks (or the agent's own transcript where none exists), joined to each agent's
     * recorded model. Added since the last snapshot: the alpha.20 Sonnet 5.5 build and its fix-up resume. That
     * dispatch is two runs of the same agent, 1,661,839 ms / 130,930 tokens / 60 tool calls, then 303,496 ms; the
     * agent's report gives 145,032 tokens and 9 more tool calls for the whole dispatch, so it counts as one task,
     * 1,965 s, 145,032 tokens, 69 tool calls. (Transcript-derived token sums run about 1.4x the notification
     * figure for the same agent, so notification figures are used throughout for agents.)
     *
     * Summary card:
     *  - [TOTAL_TOKENS] is every agent row plus every main-session row, cache reads excluded as above.
     *  - [HANDS_ON_HOURS] is the UNION of two interval sets, so time when both were running is counted once:
     *    main-session active intervals (each gap of at most 10 minutes between consecutive entries) and agent
     *    run intervals (notification end time minus its `duration_ms`; for the few dispatches with no usage
     *    notification, first to last transcript timestamp; the alpha.20 dispatch as its two runs; a dispatch
     *    nested inside another agent is covered by the union). Main active 17.4 h plus agent union 18.3 h would
     *    be 35.8 h if added; they overlap by 4.6 h, so the union is 31.2 h.
     *
     * Known gap: the one Haiku 4.5 recovery run has no transcript on disk, so it is in the agent rows (from its
     * notification) but not in [HANDS_ON_HOURS]; it was under a minute.
     */
    const val SNAPSHOT_DATE: String = "2026-11-14"

    /** Kev's prompts in the main-session transcript (see the definition in [SNAPSHOT_DATE]'s doc). */
    const val TOTAL_PROMPTS: Int = 235

    /** Total background agent dispatches across all models (50 at the last snapshot, plus alpha.20). */
    const val TOTAL_AGENT_TASKS: Int = 51

    /** Cumulative build time across all background agent dispatches, in hours (17.6 + 1,965 s = 18.1). */
    const val TOTAL_HOURS: Double = 18.1

    /**
     * Hands-on time: the union of main-session active intervals and agent run intervals, in hours. NOT the sum of
     * the two (17.4 h + 18.3 h), because the orchestrator is working or waiting while agents build.
     */
    const val HANDS_ON_HOURS: Double = 31.2

    /**
     * One row per model. Background-agent rows have a [taskCount]; orchestrator rows (the main session, see
     * [isOrchestrator]) have [promptsHandled], [replies] and [actions] instead (defined in [SNAPSHOT_DATE]'s doc)
     * and [hours] means active time.
     */
    data class ModelStats(
        val modelName: String,
        val taskCount: Int?,
        val hours: Double,
        val tokens: Long?,
        val toolCalls: Int?,
        val usedFor: String,
        val promptsHandled: Int? = null,
        val replies: Int? = null,
        val actions: Int? = null,
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
            taskCount = 6,
            hours = 7_366.0 / 3_600.0,
            tokens = 968_283L,
            toolCalls = 321,
            usedFor = "Milestone builds from M15 on (refresh visibility, show-more paging, descriptive search, " +
                "theme search, More like this, new releases rows) after the project moved to Sonnet 5.5.",
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
            promptsHandled = 178,
            replies = 223,
            actions = 1060,
            hours = 13.0101,
            tokens = 12_656_716L,
            toolCalls = null,
            isOrchestrator = true,
            usedFor = "The main session for most of the project: reading the spec, briefing and reviewing " +
                "the background agents, verifying on the emulator and talking with Kev.",
        ),
        ModelStats(
            modelName = "Claude Opus 5.5 (orchestrator)",
            taskCount = null,
            promptsHandled = 34,
            replies = 42,
            actions = 187,
            hours = 2.5509,
            tokens = 1_075_919L,
            toolCalls = null,
            isOrchestrator = true,
            usedFor = "The main session when Kev switched up for harder asks.",
        ),
        ModelStats(
            modelName = "Claude Sonnet 5.5 (orchestrator)",
            taskCount = null,
            promptsHandled = 4,
            replies = 7,
            actions = 25,
            hours = 0.5978,
            tokens = 392_127L,
            toolCalls = null,
            isOrchestrator = true,
            usedFor = "The main session after the project moved to Sonnet 5.5 for orchestration.",
        ),
        ModelStats(
            modelName = "Claude Fable 5 (orchestrator)",
            taskCount = null,
            promptsHandled = 19,
            replies = 21,
            actions = 47,
            hours = 1.2889,
            tokens = 236_300L,
            toolCalls = null,
            isOrchestrator = true,
            usedFor = "The main session for a short stretch before Fable became unavailable.",
        ),
    )

    /** Tokens across every agent row plus every main-session row, cache reads excluded. */
    val TOTAL_TOKENS: Long = MODEL_BREAKDOWN.sumOf { it.tokens ?: 0L }
}
