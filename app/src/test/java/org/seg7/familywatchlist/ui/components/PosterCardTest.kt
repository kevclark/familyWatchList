package org.seg7.familywatchlist.ui.components

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.seg7.familywatchlist.ui.theme.FamilyWatchListTheme

/**
 * M6 (PLAN.md §5 "Paid (rent/buy) titles" addendum): Search results and My List don't render
 * per-provider badges on each card ([AvailabilityRowTest] covers those, on the details screen) —
 * [PosterCard]'s "RENT/BUY" tag is the one signal on those two surfaces that a title isn't
 * included with a subscription.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PosterCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `paidOnly renders a RENT-BUY tag -- M6`() {
        composeRule.setContent {
            FamilyWatchListTheme {
                PosterCard(title = "Central Intelligence", posterPath = null, onClick = {}, paidOnly = true)
            }
        }

        composeRule.onNodeWithText("RENT/BUY").assertIsDisplayed()
    }

    @Test
    fun `paidOnly=false renders no tag at all -- default, pre-M6 behaviour unchanged`() {
        composeRule.setContent {
            FamilyWatchListTheme {
                PosterCard(title = "Paddington", posterPath = null, onClick = {})
            }
        }

        composeRule.onAllNodesWithText("RENT/BUY").assertCountEquals(0)
    }

    @Test
    fun `a dimmed My List item never shows the paid tag alongside the dim caption -- avoids two competing overlays`() {
        composeRule.setContent {
            FamilyWatchListTheme {
                PosterCard(
                    title = "Paddington",
                    posterPath = null,
                    onClick = {},
                    paidOnly = true,
                    dimmed = true,
                )
            }
        }

        composeRule.onAllNodesWithText("RENT/BUY").assertCountEquals(0)
    }
}
