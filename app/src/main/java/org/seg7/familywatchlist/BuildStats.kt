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

    /** ISO date the numbers below were last re-derived from the session transcripts. */
    const val SNAPSHOT_DATE: String = "2026-08-22"

    /** Total prompts across the project's whole continuous session plus one post-reboot recovery session. */
    const val TOTAL_PROMPTS: Int = 173

    /** Total background agent dispatches across all models. */
    const val TOTAL_AGENT_TASKS: Int = 34

    /** Cumulative build time across all background agent dispatches, in hours. */
    const val TOTAL_HOURS: Double = 12.9

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
            taskCount = 28,
            hours = 10.0,
            tokens = 6_850_000L,
            toolCalls = 4_000,
            usedFor = "The great majority of milestone feature implementation — well-specified, " +
                "routine build work once a design/investigation decision was already made.",
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
