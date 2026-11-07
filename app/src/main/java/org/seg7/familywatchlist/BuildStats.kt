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
     * ISO date the numbers below were last re-derived from the session transcripts. 2026-11-07 refresh: the
     * 2026-08-22 figures are carried forward unchanged and everything after that date was mined fresh (user
     * prompts from the main transcript; per-dispatch duration, tokens and tool calls from `<task-notification>`
     * blocks, or from the agent's own transcript where no notification exists, joined to each agent's recorded
     * model). The alpha.18 build that was running at the time is not counted yet.
     */
    const val SNAPSHOT_DATE: String = "2026-11-07"

    /** Total prompts across the project's whole continuous session plus one post-reboot recovery session. */
    const val TOTAL_PROMPTS: Int = 249

    /** Total background agent dispatches across all models. */
    const val TOTAL_AGENT_TASKS: Int = 48

    /** Cumulative build time across all background agent dispatches, in hours. */
    const val TOTAL_HOURS: Double = 17.0

    data class ModelStats(
        val modelName: String,
        val taskCount: Int,
        val hours: Double,
        val tokens: Long?,
        val toolCalls: Int?,
        val usedFor: String,
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
            taskCount = 3,
            hours = 0.9,
            tokens = 588_000L,
            toolCalls = 174,
            usedFor = "Milestone builds from M15 on (refresh visibility, show-more paging, descriptive search) " +
                "after the project moved to Sonnet 5.5.",
        ),
        ModelStats(
            modelName = "Claude Haiku 4.5",
            taskCount = 1,
            hours = 0.6 / 60.0,
            tokens = 26_000L,
            toolCalls = null,
            usedFor = "A single lightweight session-recovery check after a machine reboot.",
        ),
    )
}
