package org.seg7.familywatchlist.ui.home

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
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

    @Test
    fun `the More card ends each New row with its page caption and taps load the next page`() {
        val state = HomeUiState(
            newMovies = listOf(title(1, "Fresh Film", MediaType.MOVIE)),
            newMoviesMore = NewMoreCardState(nextPage = 2, totalPages = 5, loading = false),
            newTv = listOf(title(3, "Fresh Show", MediaType.TV)),
            newSeriesMore = NewMoreCardState(nextPage = 3, totalPages = 3, loading = false),
        )
        var filmTaps = 0
        var seriesTaps = 0
        composeRule.setContent {
            FamilyWatchListTheme {
                androidx.compose.foundation.layout.Column {
                    NewFilmsRow(state, { _, _ -> }, {}, {}, onShowMore = { filmTaps++ })
                    NewSeriesRow(state, { _, _ -> }, {}, {}, onShowMore = { seriesTaps++ })
                }
            }
        }

        composeRule.onNodeWithText("Page 2 of 5").assertExists()
        composeRule.onNodeWithText("Page 3 of 3").assertExists()
        composeRule.onAllNodesWithText("Show more").assertCountEquals(2)
        composeRule.onNodeWithText("Page 2 of 5").performClick()
        assertEquals(1, filmTaps)
        assertEquals(0, seriesTaps)
        composeRule.onNodeWithText("Page 3 of 3").performClick()
        assertEquals(1, seriesTaps)
    }

    @Test
    fun `no More card when TMDB has no further page, and none for a loading card tap`() {
        var taps = 0
        composeRule.setContent {
            FamilyWatchListTheme {
                androidx.compose.foundation.layout.Column {
                    NewFilmsRow(HomeUiState(newMovies = listOf(title(1, "Fresh Film", MediaType.MOVIE))), { _, _ -> }, {}, {})
                    NewSeriesRow(
                        HomeUiState(
                            newTv = listOf(title(3, "Fresh Show", MediaType.TV)),
                            newSeriesMore = NewMoreCardState(2, 4, loading = true),
                        ),
                        { _, _ -> }, {}, {}, onShowMore = { taps++ },
                    )
                }
            }
        }

        // Films row: no card at all. Series row: loading card swallows taps and drops the page caption.
        composeRule.onAllNodesWithText("Show more").assertCountEquals(1)
        composeRule.onAllNodesWithText("Page 2 of 4").assertCountEquals(0)
        composeRule.onNodeWithText("Show more").performClick()
        assertEquals(0, taps)
    }
}
