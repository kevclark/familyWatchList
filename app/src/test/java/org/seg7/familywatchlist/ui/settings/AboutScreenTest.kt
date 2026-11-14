package org.seg7.familywatchlist.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.seg7.familywatchlist.BuildConfig
import org.seg7.familywatchlist.BuildStats
import org.seg7.familywatchlist.ui.theme.FamilyWatchListTheme

/**
 * M7 (PROGRESS.md "In-app 'About this build' AI-transparency screen"): a static-data display
 * screen, so — per that task's own "proportionate" testing call — this only confirms the baked-in
 * [BuildStats] figures and each model name actually render, not any deeper logic (there isn't
 * any: these are constants).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AboutScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `renders version, prompt count, total hours, snapshot date and every model row`() {
        composeRule.setContent {
            FamilyWatchListTheme {
                AboutScreen(onBack = {})
            }
        }

        composeRule.onNodeWithText("Version ${BuildConfig.VERSION_NAME}").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(
            "${BuildStats.TOTAL_AGENT_TASKS} background agent dispatches across " +
                "${BuildStats.MODEL_BREAKDOWN.count { !it.isOrchestrator }} Claude models · " +
                "${"%.1f".format(BuildStats.TOTAL_HOURS)}h of cumulative agent build time",
        ).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Snapshot as of ${BuildStats.SNAPSHOT_DATE} — these figures don't update live as development continues.")
            .performScrollTo().assertIsDisplayed()

        BuildStats.MODEL_BREAKDOWN.forEach { model ->
            composeRule.onNodeWithText(model.modelName).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun `summary card shows prompts, total tokens and union hands-on time`() {
        composeRule.setContent { FamilyWatchListTheme { AboutScreen(onBack = {}) } }

        composeRule.onNodeWithText("Kev's prompts").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(BuildStats.TOTAL_PROMPTS.toString()).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Total tokens").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(formatTokenCount(BuildStats.TOTAL_TOKENS)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Hands-on time").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(formatHours(BuildStats.HANDS_ON_HOURS)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `every orchestrator row shows prompts handled, replies and actions`() {
        composeRule.setContent { FamilyWatchListTheme { AboutScreen(onBack = {}) } }

        BuildStats.MODEL_BREAKDOWN.filter { it.isOrchestrator }.forEach { model ->
            composeRule.onNodeWithText(formatOrchestratorCounts(model)).performScrollTo().assertIsDisplayed()
        }
        composeRule.onNodeWithText("178 prompts handled · 223 replies · 1,060 actions").assertExists()
    }

    @Test
    fun `summary numbers are consistent with the rows`() {
        val orchestrators = BuildStats.MODEL_BREAKDOWN.filter { it.isOrchestrator }
        // Every prompt is handled by exactly one model.
        assertEquals(BuildStats.TOTAL_PROMPTS, orchestrators.sumOf { it.promptsHandled!! })
        // Total tokens are agents plus main session.
        assertEquals(BuildStats.MODEL_BREAKDOWN.sumOf { it.tokens ?: 0L }, BuildStats.TOTAL_TOKENS)
        // Hands-on time is a union: strictly less than the plain sum of the main-session and agent hours,
        // and at least as long as either alone.
        val main = orchestrators.sumOf { it.hours }
        assertTrue(BuildStats.HANDS_ON_HOURS < main + BuildStats.TOTAL_HOURS)
        assertTrue(BuildStats.HANDS_ON_HOURS >= main)
    }
}
