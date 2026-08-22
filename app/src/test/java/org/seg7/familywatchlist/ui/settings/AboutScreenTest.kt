package org.seg7.familywatchlist.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
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
        composeRule.onNodeWithText("${BuildStats.TOTAL_PROMPTS} prompts · 12.9h of build time").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Snapshot as of ${BuildStats.SNAPSHOT_DATE} — these figures don't update live as development continues.")
            .performScrollTo().assertIsDisplayed()

        BuildStats.MODEL_BREAKDOWN.forEach { model ->
            composeRule.onNodeWithText(model.modelName).performScrollTo().assertIsDisplayed()
        }
    }
}
