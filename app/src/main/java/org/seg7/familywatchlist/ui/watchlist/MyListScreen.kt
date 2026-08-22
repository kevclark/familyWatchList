package org.seg7.familywatchlist.ui.watchlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.seg7.familywatchlist.R
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.recommend.FamilyBlend
import org.seg7.familywatchlist.ui.LocalAppContainer
import org.seg7.familywatchlist.ui.avatar.AvatarBadge
import org.seg7.familywatchlist.ui.avatar.avatarKeyToOption
import org.seg7.familywatchlist.ui.components.FilterPill
import org.seg7.familywatchlist.ui.components.PosterCard
import org.seg7.familywatchlist.ui.components.clickableNoRipple
import org.seg7.familywatchlist.ui.theme.Chalk
import org.seg7.familywatchlist.ui.theme.ChalkFaint
import org.seg7.familywatchlist.ui.theme.ChalkMuted
import org.seg7.familywatchlist.ui.theme.Dimens
import org.seg7.familywatchlist.ui.theme.Ink

/**
 * PLAN.md §2/§5: the Want-to-Watch list is **one shared family list, tagged with who added each
 * title**, and "Home's 'My List' row filters to the active profile with a 'whole family' toggle."
 *
 * PLAN.md §4b (M3j): Home's carousel ([org.seg7.familywatchlist.ui.home.HomeViewModel.myList])
 * now filters to the active profile's own additions unconditionally, no toggle — symmetric for
 * Family and every real profile alike, now that Family can own watchlist entries too. This
 * screen keeps the "Everyone"/"Added by me" pills as a manual override over that same filter
 * (Kev didn't ask to remove them) and is still where the added-by tag lives, because a poster
 * thumbnail has nowhere to put "added by Sam" without becoming the text-heavy layout §5a rules
 * out.
 */
@Composable
fun MyListScreen(
    activeProfileId: Long,
    onBack: () -> Unit,
    onOpenTitle: (Int, MediaType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalAppContainer.current
    val viewModel: MyListViewModel = viewModel(
        key = "mylist-$activeProfileId",
        factory = viewModelFactory {
            initializer {
                MyListViewModel(
                    container.watchlistRepository,
                    container.profileRepository,
                    activeProfileId,
                    container.userPreferencesRepository,
                    container.recommendationRepository,
                    container.familyProfileRepository,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize().background(Ink)) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = Dimens.Gutter, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Chalk,
                modifier = Modifier
                    .size(24.dp)
                    .clickableNoRipple(onBack),
            )
            Text(text = "My List", style = MaterialTheme.typography.displaySmall, color = Chalk)
        }

        Row(
            modifier = Modifier.padding(horizontal = Dimens.Gutter, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterPill(
                // PLAN.md §5b M3i item 8: "Whole family" predates the literal Family profile and
                // now collides with it in name — pure rename, mineOnly behaviour unchanged.
                label = "Everyone",
                selected = !state.mineOnly,
                onClick = { viewModel.setMineOnly(false) },
            )
            FilterPill(
                label = "Added by me",
                selected = state.mineOnly,
                onClick = { viewModel.setMineOnly(true) },
            )
        }

        // PLAN.md §3 attribution: dimming above is availability-derived (`row.isAvailable`,
        // JustWatch-sourced via ProviderAvailability), so this screen counts as "wherever
        // availability badges render" even though it shows dimming rather than provider glyphs —
        // a single line here rather than per-card, same pattern as HomeScreen's `HomeFooter`,
        // placed above the (fillMaxSize, non-scrolling-past) grid so it's always visible rather
        // than requiring a scroll to the bottom of a `LazyVerticalGrid` that fills the screen.
        Text(
            text = stringResource(R.string.justwatch_attribution),
            style = MaterialTheme.typography.labelSmall,
            color = ChalkFaint,
            modifier = Modifier.padding(horizontal = Dimens.Gutter, vertical = 4.dp),
        )

        if (state.visibleItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = if (state.mineOnly) "You haven't added anything yet" else "The list is empty",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Chalk,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "Search for a film or series and tap ＋ to put it here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ChalkFaint,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(
                    start = Dimens.Gutter,
                    end = Dimens.Gutter,
                    top = 12.dp,
                    bottom = 28.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(Dimens.CardGap),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.visibleItems, key = { "${it.item.mediaType}-${it.item.tmdbId}" }) { row ->
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        // PLAN.md §5b M3i item 9: an item over the *viewing* profile's age cap
                        // renders dimmed too, reusing the same FamilyBlend.isOverCap check
                        // Home/Search/the recommender already use, against [state.ageRatingCap].
                        // Display-only — the ✓ quick-add badge below still removes it exactly as
                        // before, unaffected by whether the dim reason is availability or age.
                        val overCap = FamilyBlend.isOverCap(row.item.certification, state.ageRatingCap)
                        PosterCard(
                            title = row.item.title,
                            posterPath = row.item.posterPath,
                            onClick = { onOpenTitle(row.item.tmdbId, row.item.mediaType) },
                            width = Dimens.PosterWidthLarge,
                            label = true,
                            isListed = true,
                            // PLAN.md §5a M2g: unavailable items render dimmed. The existing ✓
                            // quick-add badge already removes directly from this screen for any
                            // item (available or not) — that's the pre-existing direct-remove
                            // path this pass reuses rather than duplicating.
                            dimmed = !row.isAvailable || overCap,
                            dimReason = if (!row.isAvailable) null else "Over your age rating cap",
                            onQuickAdd = { viewModel.remove(row.item.tmdbId, row.item.mediaType) },
                        )
                        // The added-by tag — the whole reason this screen exists alongside the row.
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            row.addedBy?.let { profile ->
                                AvatarBadge(
                                    option = avatarKeyToOption(profile.avatarKey),
                                    size = 15.dp,
                                    name = profile.name,
                                )
                            }
                            Text(
                                text = row.addedBy?.name?.let { "Added by $it" } ?: "Added",
                                style = MaterialTheme.typography.labelSmall,
                                color = ChalkMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
