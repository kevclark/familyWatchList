package org.seg7.familywatchlist.ui.search

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.TitleEntity
import org.seg7.familywatchlist.testutil.MainDispatcherRule
import org.seg7.familywatchlist.ui.theme.FamilyWatchListTheme

/**
 * Regression for the emulator bug where the "Titles matching" section never appeared. The title
 * search finishes *after* the structured results are on screen, so the matches are prepended to a
 * list the user is already looking at, and a lazy grid keeps the first visible item in place, so the
 * new section sat just above the viewport. State-level tests could not see this; it needs a real
 * layout.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SearchResultsGridTest {
    @get:Rule(order = 0)
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    private fun title(id: Int, name: String) = TitleEntity(
        tmdbId = id, mediaType = MediaType.MOVIE, title = name, year = 2020,
        posterPath = null, backdropPath = null, overview = null, runtimeMin = 90,
        certification = "12", voteAverage = 7.0, popularity = 10.0, trailerKey = null, fetchedAt = 1_000L,
    )

    /** Exact-text match (cards expose the title more than once), first node, must be on screen. */
    private fun shown(text: String) =
        composeRule.onAllNodesWithText(text).onFirst().assertIsDisplayed()

    @Test
    fun `title matches arriving after the results are scrolled into view above the results`() {
        val results = (1..9).map { title(it, "Result $it") }
        val state = MutableStateFlow(SearchUiState(query = "murder mystery", results = results, hasSearched = true))

        composeRule.setContent {
            FamilyWatchListTheme {
                val s by state.collectAsState()
                SearchResultsGrid(s, onOpenTitle = { _, _ -> }, onToggleWatchlist = {}, onLoadMore = {})
            }
        }
        shown("Result 1")

        state.value = state.value.copy(titleMatches = listOf(title(100, "Murder Mystery"), title(101, "Murder Mystery 2")))
        composeRule.waitForIdle()

        shown("Titles matching \u2018murder mystery\u2019")
        shown("Murder Mystery")
        shown("Murder Mystery 2")
    }
}
