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
import org.seg7.familywatchlist.data.local.dao.AvailabilityBadge
import org.seg7.familywatchlist.data.local.entity.ProviderKind
import org.seg7.familywatchlist.ui.theme.FamilyWatchListTheme

/**
 * M6 (PLAN.md §5 "Paid (rent/buy) titles" addendum): [AvailabilityRow]/[ProviderBadge] must
 * visually distinguish every [ProviderKind] — FLATRATE (the default "included" look, no extra
 * tag), FREE ("FREE"), and the two new paid kinds RENT/BUY ("RENT"/"BUY") — never implying a
 * price for the latter two, since TMDB's data has none to give.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AvailabilityRowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun badge(kind: ProviderKind) = AvailabilityBadge(
        providerId = 1,
        name = "Provider One",
        logoPath = null,
        kind = kind,
        subscribed = true,
    )

    @Test
    fun `FLATRATE renders with no kind tag`() {
        composeRule.setContent { FamilyWatchListTheme { AvailabilityRow(badges = listOf(badge(ProviderKind.FLATRATE))) } }

        composeRule.onAllNodesWithText("FREE").assertCountEquals(0)
        composeRule.onAllNodesWithText("RENT").assertCountEquals(0)
        composeRule.onAllNodesWithText("BUY").assertCountEquals(0)
    }

    @Test
    fun `FREE renders a FREE tag`() {
        composeRule.setContent { FamilyWatchListTheme { AvailabilityRow(badges = listOf(badge(ProviderKind.FREE))) } }

        composeRule.onNodeWithText("FREE").assertIsDisplayed()
    }

    @Test
    fun `RENT renders a RENT tag -- M6`() {
        composeRule.setContent { FamilyWatchListTheme { AvailabilityRow(badges = listOf(badge(ProviderKind.RENT))) } }

        composeRule.onNodeWithText("RENT").assertIsDisplayed()
    }

    @Test
    fun `BUY renders a BUY tag -- M6`() {
        composeRule.setContent { FamilyWatchListTheme { AvailabilityRow(badges = listOf(badge(ProviderKind.BUY))) } }

        composeRule.onNodeWithText("BUY").assertIsDisplayed()
    }
}
