package org.seg7.familywatchlist.ui.details

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.seg7.familywatchlist.ui.theme.FamilyWatchListTheme

/**
 * M4a-2: covers the one piece of [TrailerPlayerDialog] that's cleanly testable without a real,
 * rendering `WebView` — the dismiss wiring. The embedded YouTube IFrame Player itself (autoplay
 * actually firing, video actually rendering) is a live-verification concern per the milestone
 * brief, not something a JVM/Robolectric test can meaningfully assert without becoming brittle
 * against `WebView` internals, so it's deliberately left out here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrailerPlayerDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun closeButtonTap_invokesOnDismiss() {
        var dismissed = false
        composeRule.setContent {
            FamilyWatchListTheme {
                TrailerPlayerDialog(youTubeKey = "dQw4w9WgXcQ", onDismiss = { dismissed = true })
            }
        }

        composeRule.onNodeWithContentDescription("Close trailer").performClick()

        assertTrue(dismissed)
    }
}
