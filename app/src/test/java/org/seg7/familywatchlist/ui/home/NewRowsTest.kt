package org.seg7.familywatchlist.ui.home

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
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.TitleEntity
import org.seg7.familywatchlist.ui.theme.FamilyWatchListTheme

/** PLAN.md §5i: render checks for the New films / New series rows. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NewRowsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun title(id: Int, name: String, type: MediaType) = TitleEntity(
        tmdbId = id, mediaType = type, title = name, year = 2026, posterPath = null, backdropPath = null,
        overview = null, runtimeMin = null, certification = null, voteAverage = null, popularity = null,
        trailerKey = null, fetchedAt = 0L,
    )

    @Test
    fun `both rows render their headers and titles, with RENT-BUY only on the paid film`() {
        val state = HomeUiState(
            newMovies = listOf(title(1, "Fresh Film", MediaType.MOVIE), title(2, "Paid Film", MediaType.MOVIE)),
            newMoviePaidOnlyIds = setOf(2),
            newTv = listOf(title(3, "Fresh Show", MediaType.TV)),
        )
        composeRule.setContent {
            FamilyWatchListTheme {
                androidx.compose.foundation.layout.Column {
                    NewFilmsRow(state, { _, _ -> }, {}, {})
                    NewSeriesRow(state, { _, _ -> }, {}, {})
                }
            }
        }

        composeRule.onNodeWithText("New films").assertIsDisplayed()
        composeRule.onNodeWithText("New series").assertIsDisplayed()
        composeRule.onNodeWithText("Fresh Film").assertExists()
        composeRule.onNodeWithText("Fresh Show").assertExists()
        composeRule.onAllNodesWithText("RENT/BUY").assertCountEquals(1)
    }

    @Test
    fun `empty rows render nothing, not even a header`() {
        composeRule.setContent {
            FamilyWatchListTheme {
                androidx.compose.foundation.layout.Column {
                    NewFilmsRow(HomeUiState(), { _, _ -> }, {}, {})
                    NewSeriesRow(HomeUiState(), { _, _ -> }, {}, {})
                }
            }
        }

        composeRule.onAllNodesWithText("New films").assertCountEquals(0)
        composeRule.onAllNodesWithText("New series").assertCountEquals(0)
    }
}
