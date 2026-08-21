package org.seg7.familywatchlist.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.seg7.familywatchlist.data.local.entity.MediaType
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
                placeholder = { Text("Films and series", color = ChalkFaint) },
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
            if (state.isSearching) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = Accent,
                    trackColor = InkRaised,
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            val results = state.visibleResults
            when {
                results.isNotEmpty() -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(
                        start = Dimens.Gutter,
                        end = Dimens.Gutter,
                        top = 14.dp,
                        bottom = 28.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.CardGap),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(results, key = { "${it.mediaType}-${it.tmdbId}" }) { title ->
                        PosterCard(
                            title = title.title,
                            posterPath = title.posterPath,
                            onClick = { onOpenTitle(title.tmdbId, title.mediaType) },
                            width = Dimens.PosterWidthLarge,
                            label = true,
                            year = title.year,
                            isListed = state.isListed(title),
                            onQuickAdd = { viewModel.toggleWatchlist(title) },
                        )
                    }
                }

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

@Composable
private fun CenteredMessage(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) { content() }
    }
}
