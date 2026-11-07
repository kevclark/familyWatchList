package org.seg7.familywatchlist.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.seg7.familywatchlist.R
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.TitleEntity
import org.seg7.familywatchlist.ui.LocalAppContainer
import org.seg7.familywatchlist.ui.components.FilterPill
import org.seg7.familywatchlist.ui.components.PosterCard
import org.seg7.familywatchlist.ui.components.clickableNoRipple
import org.seg7.familywatchlist.ui.theme.Accent
import org.seg7.familywatchlist.ui.theme.Chalk
import org.seg7.familywatchlist.ui.theme.ChalkFaint
import org.seg7.familywatchlist.ui.theme.ChalkMuted
import org.seg7.familywatchlist.ui.theme.Dimens
import org.seg7.familywatchlist.ui.theme.Ink
import org.seg7.familywatchlist.ui.theme.InkHairline
import org.seg7.familywatchlist.ui.theme.InkRaised
import org.seg7.familywatchlist.ui.theme.OnAccent

/**
 * PLAN.md §5 screen 5: `/search/multi` with movie/TV filter chips, a poster grid, and quick
 * add-to-list on each card.
 *
 * No `TopAppBar` — the search field *is* the header (§5a "minimal chrome"), and results are a
 * 3-column poster grid so the screen is mostly art even mid-typing.
 */
@Composable
fun SearchScreen(
    activeProfileId: Long,
    onOpenTitle: (Int, MediaType) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalAppContainer.current
    val viewModel: SearchViewModel = viewModel(
        key = "search-$activeProfileId",
        factory = viewModelFactory {
            initializer {
                SearchViewModel(
                    container.searchRepository,
                    container.watchlistRepository,
                    container.providerRepository,
                    activeProfileId,
                    container.userPreferencesRepository,
                    container.recommendationRepository,
                    container.descriptiveSearchRepository,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    val snackbarHostState = remember { SnackbarHostState() }

    // PLAN.md §5a: a blocked quick-add (title not on a subscribed provider) surfaces as a
    // Snackbar rather than a silent no-op — same one-shot event pattern as ProfilePickerScreen.
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SearchUiEvent.WatchlistBlocked -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Ink)) {
      Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.statusBarsPadding().padding(horizontal = Dimens.Gutter),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Search",
                style = MaterialTheme.typography.displaySmall,
                color = Chalk,
                modifier = Modifier.padding(top = 12.dp),
            )
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Films and series, or describe: action comedies", color = ChalkFaint) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = ChalkMuted) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        Icon(
                            Icons.Filled.Clear,
                            contentDescription = "Clear search",
                            tint = ChalkMuted,
                            modifier = Modifier.clickableNoRipple { viewModel.onQueryChange("") },
                        )
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    viewModel.onSubmit()
                    keyboard?.hide()
                }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = InkRaised,
                    unfocusedContainerColor = InkRaised,
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = InkHairline,
                    cursorColor = Accent,
                    focusedTextColor = Chalk,
                    unfocusedTextColor = Chalk,
                ),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchFilter.entries.forEach { filter ->
                    FilterPill(
                        label = filter.label,
                        selected = state.filter == filter,
                        onClick = { viewModel.onFilterChange(filter) },
                    )
                }
            }
            // PLAN.md §5f item 4: age filter, defaulting to the profile's cap (Family: strictest).
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Age", style = MaterialTheme.typography.labelMedium, color = ChalkMuted)
                state.ageOptions.forEach { option ->
                    FilterPill(
                        label = option.label,
                        selected = state.ageOption == option,
                        onClick = { viewModel.onAgeOptionChange(option) },
                    )
                }
            }
            // PLAN.md §5f item 3: what the structured search understood, each removable.
            state.interpretation?.let { interpretation ->
                if (interpretation.chips.isNotEmpty()) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        interpretation.chips.forEach { chip ->
                            FilterPill(
                                label = "${chip.label}  \u00D7",
                                selected = false,
                                onClick = { viewModel.onRemoveChip(chip.key) },
                            )
                        }
                    }
                }
                if (interpretation.ignored.isNotEmpty()) {
                    Text(
                        text = "Ignored: ${interpretation.ignored.joinToString(", ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = ChalkFaint,
                    )
                }
            }
            if (state.showTitleSearchLink) {
                Text(
                    text = "Search titles for \u2018${state.query.trim()}\u2019 instead",
                    style = MaterialTheme.typography.labelMedium,
                    color = Accent,
                    modifier = Modifier.clickableNoRipple { viewModel.onSearchTitlesInstead() },
                )
            }
            if (state.isSearching) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = Accent,
                    trackColor = InkRaised,
                )
            }
            // PLAN.md §3 attribution + §5a "Search & watchlist availability gating": every result
            // this screen can possibly show is already filtered to "available on a subscribed GB
            // service", so the credit belongs here unconditionally, not only once results exist —
            // same "always visible, not buried at the bottom of a fillMaxSize grid" placement as
            // MyListScreen's equivalent line.
            Text(
                text = stringResource(R.string.justwatch_attribution),
                style = MaterialTheme.typography.labelSmall,
                color = ChalkFaint,
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            val results = state.visibleResults
            val matches = state.visibleTitleMatches
            when {
                results.isNotEmpty() || matches.isNotEmpty() -> SearchResultsGrid(
                    state = state,
                    onOpenTitle = onOpenTitle,
                    onToggleWatchlist = viewModel::toggleWatchlist,
                    onLoadMore = viewModel::loadMore,
                )

                // PLAN.md §5a's M2g refinement: with nothing subscribed, no query could ever
                // return a result (the availability gate has no provider to pass against) — say
                // so explicitly rather than showing a silent empty grid or the generic "find
                // something to watch" prompt, and offer a direct path to fix it. Checked ahead of
                // the searching/error/query states below since it's true regardless of what's
                // been typed.
                !state.hasSubscribedServices -> CenteredMessage {
                    Text(
                        text = "No services selected",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Chalk,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "Search only shows what's on a service you're subscribed to — " +
                            "choose some in Settings to see results.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ChalkMuted,
                        textAlign = TextAlign.Center,
                    )
                    Button(
                        onClick = onOpenSettings,
                        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent),
                        shape = MaterialTheme.shapes.small,
                    ) { Text("Choose your services") }
                }

                state.isSearching && state.results.isEmpty() ->
                    CenteredMessage { CircularProgressIndicator(color = Accent) }

                state.errorMessage != null -> CenteredMessage {
                    Text(
                        text = state.errorMessage!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ChalkMuted,
                        textAlign = TextAlign.Center,
                    )
                }

                // PLAN.md §5b M3i item 4: a distinct message for the "excluded by age rating"
                // case, checked ahead of the generic "not on your services" one below — both are
                // in principle possible for the same empty result, and the age-cap explanation is
                // the more specific/actionable of the two when it applies. See
                // SearchUiState.isPlausiblyHiddenByAgeRating's kdoc for exactly when this fires
                // (a heuristic, not a certainty — documented there).
                state.isPlausiblyHiddenByAgeRating -> CenteredMessage {
                    Text(
                        text = "Nothing at or under this profile's age rating matched “${state.query}”.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = ChalkMuted,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "Some results may be hidden by this profile's age rating setting.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ChalkFaint,
                        textAlign = TextAlign.Center,
                    )
                }

                state.hasSearched -> CenteredMessage {
                    Text(
                        // PLAN.md §5a: results are gated to what's on a subscribed service, so
                        // "no matches" here also covers "matched, but not available anywhere
                        // you're subscribed" — the wording says so rather than implying a typo.
                        text = "Nothing available on your services matched “${state.query}”.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = ChalkMuted,
                        textAlign = TextAlign.Center,
                    )
                }

                else -> CenteredMessage {
                    Text(
                        text = "Find something to watch",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Chalk,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "Tap ＋ on any result to put it on the family's list.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ChalkFaint,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
      }
      SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

/**
 * The result grid, stateless so it can be tested on its own. Title matches (PLAN.md §5f) usually
 * arrive *after* the structured results; items prepended to a lazy list are kept off-screen above
 * the viewport (the list holds the first visible item's position by key), so the grid scrolls
 * back to the top when the matches section first appears.
 */
@Composable
internal fun SearchResultsGrid(
    state: SearchUiState,
    onOpenTitle: (Int, MediaType) -> Unit,
    onToggleWatchlist: (TitleEntity) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val results = state.visibleResults
    val matches = state.visibleTitleMatches
    val gridState = rememberLazyGridState()
    val hasMatches = matches.isNotEmpty()
    LaunchedEffect(hasMatches) {
        if (hasMatches) gridState.scrollToItem(0)
    }
    LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(
                        start = Dimens.Gutter,
                        end = Dimens.Gutter,
                        top = 14.dp,
                        bottom = 28.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.CardGap),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    // PLAN.md §5b M3i item 1: the keyboard can otherwise cover the last row of
                    // results with no way to scroll past it short of dismissing it first.
                    modifier = modifier.fillMaxSize().imePadding(),
                ) {
                    if (matches.isNotEmpty()) {
                        item(key = "title-matches-header", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = "Titles matching \u2018${state.query.trim()}\u2019",
                                style = MaterialTheme.typography.labelLarge,
                                color = ChalkMuted,
                            )
                        }
                        items(matches, key = { "match-${it.mediaType}-${it.tmdbId}" }) { title ->
                            PosterCard(
                                title = title.title,
                                posterPath = title.posterPath,
                                onClick = { onOpenTitle(title.tmdbId, title.mediaType) },
                                width = Dimens.PosterWidthLarge,
                                label = true,
                                year = title.year,
                                isListed = state.isListed(title),
                                onQuickAdd = { onToggleWatchlist(title) },
                                paidOnly = state.isPaidOnly(title),
                            )
                        }
                        if (results.isNotEmpty()) {
                            item(key = "results-header", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                Text(
                                    text = "Results",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = ChalkMuted,
                                )
                            }
                        }
                    }
                    items(results, key = { "${it.mediaType}-${it.tmdbId}" }) { title ->
                      Column {
                        PosterCard(
                            title = title.title,
                            posterPath = title.posterPath,
                            onClick = { onOpenTitle(title.tmdbId, title.mediaType) },
                            width = Dimens.PosterWidthLarge,
                            label = true,
                            year = title.year,
                            isListed = state.isListed(title),
                            onQuickAdd = { onToggleWatchlist(title) },
                            paidOnly = state.isPaidOnly(title),
                        )
                        if (state.isUnrated(title)) {
                            Text(
                                text = "Unrated",
                                style = MaterialTheme.typography.labelSmall,
                                color = ChalkFaint,
                            )
                        }
                      }
                    }
                    if (state.canLoadMore) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                if (state.isLoadingMore) {
                                    CircularProgressIndicator(color = Accent)
                                } else {
                                    FilterPill(label = "Load more", selected = false, onClick = onLoadMore)
                                }
                            }
                        }
                    }
                }
}

@Composable
private fun CenteredMessage(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) { content() }
    }
}
