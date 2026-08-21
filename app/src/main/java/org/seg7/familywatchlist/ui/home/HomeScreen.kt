package org.seg7.familywatchlist.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil3.compose.AsyncImage
import org.seg7.familywatchlist.R
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.ProfileEntity
import org.seg7.familywatchlist.data.local.entity.TitleEntity
import org.seg7.familywatchlist.ui.ActiveProfile
import org.seg7.familywatchlist.ui.LocalAppContainer
import org.seg7.familywatchlist.ui.avatar.AvatarBadge
import org.seg7.familywatchlist.ui.avatar.avatarKeyToOption
import org.seg7.familywatchlist.ui.components.BottomScrim
import org.seg7.familywatchlist.ui.components.PosterCard
import org.seg7.familywatchlist.ui.components.PosterCarousel
import org.seg7.familywatchlist.ui.components.SectionHeader
import org.seg7.familywatchlist.ui.components.TopScrim
import org.seg7.familywatchlist.ui.components.backdropUrl
import org.seg7.familywatchlist.ui.components.clickableNoRipple
import org.seg7.familywatchlist.ui.theme.Accent
import org.seg7.familywatchlist.ui.theme.Chalk
import org.seg7.familywatchlist.ui.theme.ChalkFaint
import org.seg7.familywatchlist.ui.theme.ChalkMuted
import org.seg7.familywatchlist.ui.theme.Dimens
import org.seg7.familywatchlist.ui.theme.Ink
import org.seg7.familywatchlist.ui.theme.InkRaised
import org.seg7.familywatchlist.ui.theme.OnAccent

/**
 * PLAN.md §5 screen 3 + §5a: **one continuous scrollable feed of stacked horizontal poster
 * carousels**, matching Netflix/Prime — not per-section tabs, and no Material app bar.
 *
 * The whole screen is a single [LazyColumn] whose first item is a full-bleed hero image running
 * under the status bar; the profile badge and refresh action float over it on a scrim rather
 * than sitting in a solid `TopAppBar` (§5a: "a transparent/scrim top bar over hero content").
 * There is no boxed card anywhere in this file — every surface with content on it is either
 * poster art or the [Ink] ground.
 */
@Composable
fun HomeScreen(
    activeProfile: ActiveProfile,
    onOpenTitle: (Int, MediaType) -> Unit,
    onOpenMyList: () -> Unit,
    onOpenSearch: () -> Unit,
    onSwitchProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalAppContainer.current
    val viewModel: HomeViewModel = viewModel(
        key = "home-${activeProfile.id}",
        factory = viewModelFactory {
            initializer {
                HomeViewModel(
                    container.discoverRepository,
                    container.providerRepository,
                    container.watchlistRepository,
                    container.userPreferencesRepository,
                    container.recommendationRepository,
                    container.titleRepository,
                    container.profileRepository,
                    container.familyProfileRepository,
                    activeProfile,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val servicesSuffix = if (state.hasSubscribedServices) " on your services" else ""

    Box(modifier = modifier.fillMaxSize().background(Ink)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(Dimens.RowGap),
        ) {
            item(key = "hero") {
                // PLAN.md §4 "Cold-start Home treatment" (Kev, 2026-08-21, M3g): a cold-start
                // profile's hero is never a title, backdrop or "More info" button implying a
                // specific recommendation — a plain getting-started panel with a CTA into Search.
                if (state.isColdStartForYou) {
                    ColdStartHero(onOpenSearch = onOpenSearch)
                } else {
                    HomeHero(
                        hero = state.hero,
                        isLoading = state.isLoading,
                        errorMessage = state.errorMessage,
                        onOpenTitle = onOpenTitle,
                        onRetry = viewModel::refresh,
                        onOpenSearch = onOpenSearch,
                    )
                }
            }

            if (state.myList.isNotEmpty()) {
                item(key = "my-list") {
                    PosterCarousel(
                        title = "My List",
                        items = state.myList,
                        key = { "${it.item.mediaType}-${it.item.tmdbId}" },
                        onSeeAll = onOpenMyList,
                    ) { row ->
                        // PLAN.md §5a M2g: an item that's lost availability renders dimmed with a
                        // direct remove action, right here — no detour through details. Available
                        // items are untouched (no dim, no extra control) per the same spec.
                        PosterCard(
                            title = row.item.title,
                            posterPath = row.item.posterPath,
                            onClick = { onOpenTitle(row.item.tmdbId, row.item.mediaType) },
                            dimmed = !row.isAvailable,
                            onRemoveUnavailable = {
                                viewModel.removeFromWatchlist(row.item.tmdbId, row.item.mediaType)
                            },
                        )
                    }
                }
            }

            item(key = "for-you") {
                ForYouRow(state = state, onOpenTitle = onOpenTitle, onOpenSearch = onOpenSearch)
            }

            // PLAN.md §5 screen 3 / §4a slider 4: the who's-watching chip row + blended Family
            // Night carousel — only relevant at all with 2+ profiles on the account (same gating
            // the family-blend slider already established), and the results row itself only once
            // 2+ chips are actually selected.
            if (state.familyNightChipsVisible) {
                item(key = "family-night-chips") {
                    FamilyNightChipRow(
                        profiles = state.familyNightProfiles,
                        selectedIds = state.familyNightSelectedIds,
                        onToggle = viewModel::toggleFamilyNightProfile,
                    )
                }
            }
            if (state.familyNightSelectedIds.size >= 2 && state.familyNightTitles.isNotEmpty()) {
                item(key = "family-night-row") {
                    PosterCarousel(
                        title = "Family Night",
                        items = state.familyNightTitles,
                        key = { "family-${it.mediaType}-${it.tmdbId}" },
                    ) { title ->
                        PosterCard(
                            title = title.title,
                            posterPath = title.posterPath,
                            onClick = { onOpenTitle(title.tmdbId, title.mediaType) },
                        )
                    }
                }
            }

            item(key = "popular-movies") {
                PosterCarousel(
                    title = "Popular films$servicesSuffix",
                    items = state.popularMovies,
                    key = { "movie-${it.tmdbId}" },
                ) { title ->
                    PosterCard(
                        title = title.title,
                        posterPath = title.posterPath,
                        onClick = { onOpenTitle(title.tmdbId, MediaType.MOVIE) },
                    )
                }
            }

            item(key = "popular-tv") {
                PosterCarousel(
                    title = "Popular series$servicesSuffix",
                    items = state.popularTv,
                    key = { "tv-${it.tmdbId}" },
                ) { title ->
                    PosterCard(
                        title = title.title,
                        posterPath = title.posterPath,
                        onClick = { onOpenTitle(title.tmdbId, MediaType.TV) },
                    )
                }
            }

            item(key = "footer") { HomeFooter() }
        }

        // Floating chrome over the hero: scrim first, then the controls on top of it.
        Box(modifier = Modifier.fillMaxWidth().height(96.dp)) { TopScrim() }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = Dimens.Gutter, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "FAMILY WATCHLIST",
                style = MaterialTheme.typography.titleMedium,
                color = Accent,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = "Refresh",
                    tint = Chalk,
                    modifier = Modifier
                        .size(22.dp)
                        .clickableNoRipple(viewModel::refresh),
                )
                AvatarBadge(
                    option = avatarKeyToOption(activeProfile.avatarKey),
                    size = 30.dp,
                    name = activeProfile.name,
                    modifier = Modifier.clickableNoRipple(onSwitchProfile),
                )
            }
        }
    }
}

/**
 * The full-bleed hero. Backdrop art, a bottom scrim dissolving it into the page, and the title
 * set over it in display type — the single biggest lever on "does this look like a streamer".
 */
@Composable
private fun HomeHero(
    hero: TitleEntity?,
    isLoading: Boolean,
    errorMessage: String?,
    onOpenTitle: (Int, MediaType) -> Unit,
    onRetry: () -> Unit,
    onOpenSearch: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(Dimens.HeroAspect)
            .background(InkRaised),
        contentAlignment = Alignment.BottomStart,
    ) {
        if (hero != null) {
            val art = backdropUrl(hero.backdropPath) ?: org.seg7.familywatchlist.ui.components.posterUrl(hero.posterPath)
            AsyncImage(
                model = art,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clickableNoRipple { onOpenTitle(hero.tmdbId, hero.mediaType) },
            )
            BottomScrim()
            Column(
                modifier = Modifier.padding(horizontal = Dimens.Gutter, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = hero.title.uppercase(),
                    style = MaterialTheme.typography.displayMedium,
                    color = Chalk,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(
                        if (hero.mediaType == MediaType.MOVIE) "Film" else "Series",
                        hero.year?.toString(),
                        hero.voteAverage?.takeIf { it > 0 }?.let { "★ ${"%.1f".format(it)}" },
                    ).joinToString("  ·  "),
                    style = MaterialTheme.typography.labelSmall,
                    color = ChalkMuted,
                )
                Button(
                    onClick = { onOpenTitle(hero.tmdbId, hero.mediaType) },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text("More info", style = MaterialTheme.typography.labelLarge)
                }
            }
        } else {
            HomeHeroEmpty(
                isLoading = isLoading,
                errorMessage = errorMessage,
                onRetry = onRetry,
                onOpenSearch = onOpenSearch,
            )
        }
    }
}

/**
 * PLAN.md §4 "Cold-start Home treatment" (Kev, 2026-08-21, M3g): replaces the old
 * `discover.movies.firstOrNull()` fallback for a cold-start profile's hero. That fallback showed
 * a real title with no visual distinction from a genuine personalised pick (only a small row
 * label underneath told them apart) — a popularity pick masquerading as "your pick". This is
 * deliberately title-shaped-nothing: no backdrop image, no "More info" button, just explanatory
 * copy and a CTA into Search. Same structural shell as [HomeHeroEmpty] (centered column over
 * [InkRaised], same aspect ratio) so the transition between the two hero states — cold start vs.
 * genuinely-nothing-to-show — reads as one consistent "empty hero" visual language, but the copy
 * and the condition that triggers each are different: this one is keyed off
 * [HomeUiState.isColdStartForYou] and shows regardless of whether Popular/discover data exists,
 * where [HomeHeroEmpty] only shows for a warm profile with nothing at all cached yet.
 */
@Composable
private fun ColdStartHero(onOpenSearch: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(Dimens.HeroAspect)
            .background(InkRaised),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(Dimens.Gutter * 2),
        ) {
            Text(
                text = "Let's find your picks",
                style = MaterialTheme.typography.displaySmall,
                color = Chalk,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Log a few things you've watched or add to your list, and we'll start finding what's next.",
                style = MaterialTheme.typography.bodyMedium,
                color = ChalkMuted,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = onOpenSearch,
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent),
                shape = MaterialTheme.shapes.small,
            ) { Text("Search titles") }
        }
    }
}

@Composable
private fun HomeHeroEmpty(
    isLoading: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
    onOpenSearch: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(Dimens.Gutter * 2),
        ) {
            when {
                isLoading -> CircularProgressIndicator(color = Accent)
                errorMessage != null -> {
                    Text(
                        text = "Couldn't load what's popular right now.",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Chalk,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = ChalkFaint,
                        textAlign = TextAlign.Center,
                    )
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent),
                        shape = MaterialTheme.shapes.small,
                    ) { Text("Try again") }
                }

                else -> {
                    Text(
                        text = "Nothing here yet",
                        style = MaterialTheme.typography.displaySmall,
                        color = Chalk,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "Search for something you've watched — or want to.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ChalkMuted,
                        textAlign = TextAlign.Center,
                    )
                    Button(
                        onClick = onOpenSearch,
                        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent),
                        shape = MaterialTheme.shapes.small,
                    ) { Text("Search titles") }
                }
            }
        }
    }
}

/**
 * PLAN.md §5's *For {profile}* row — real as of M3, retiring the pre-M3 "coming soon" placeholder
 * (PLAN.md §5a "Post-M2b decisions"). Always visible, per that same decision — never omitted.
 *
 * Two states, both driven by [HomeViewModel]:
 *  - **Cold start** ([HomeUiState.isColdStartForYou], PLAN.md §4: "< 5 watch events -> popular
 *    on your services"): a combined movies+TV popularity carousel, labelled "Popular on your
 *    services" — the same underlying `/discover` data the type-specific Popular rows below
 *    render, just merged into one row in the prominent "For You" slot rather than duplicating
 *    §4's cold-start copy as a second, separate concept.
 *  - **Warm profile**: [HomeUiState.forYouTitles], the real scored shortlist, labelled "For You".
 *    Empty (shortlist not computed yet, or a genuinely empty result) falls back to the same
 *    "nothing here yet, go search" prompt the pre-M3 placeholder used, rather than an empty row.
 */
@Composable
private fun ForYouRow(state: HomeUiState, onOpenTitle: (Int, MediaType) -> Unit, onOpenSearch: () -> Unit) {
    if (state.isColdStartForYou) {
        val combined = (state.popularMovies + state.popularTv)
            .sortedByDescending { it.popularity ?: 0.0 }
            .take(10)
        if (combined.isEmpty()) return
        PosterCarousel(
            title = "For You — popular on your services",
            items = combined,
            key = { "for-you-cold-${it.mediaType}-${it.tmdbId}" },
        ) { title ->
            PosterCard(
                title = title.title,
                posterPath = title.posterPath,
                onClick = { onOpenTitle(title.tmdbId, title.mediaType) },
            )
        }
        return
    }

    if (state.forYouTitles.isEmpty()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SectionHeader(title = "For You")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.Gutter)
                    .clip(MaterialTheme.shapes.medium)
                    .background(InkRaised)
                    .padding(horizontal = 16.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Building your picks — check back in a moment, or pull to refresh.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ChalkMuted,
                )
                Button(
                    onClick = onOpenSearch,
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text("Find something to watch", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        return
    }

    PosterCarousel(
        title = "For You",
        items = state.forYouTitles,
        key = { "for-you-${it.mediaType}-${it.tmdbId}" },
    ) { title ->
        PosterCard(
            title = title.title,
            posterPath = title.posterPath,
            onClick = { onOpenTitle(title.tmdbId, title.mediaType) },
        )
    }
}

/**
 * PLAN.md §5 screen 3: "Family night (with who's-watching chips)". Reuses [AvatarBadge]'s
 * existing `selected` styling (an [Accent] border) — the same visual language the profile picker
 * already uses for "which one is active", so a selected chip here reads consistently rather than
 * inventing a second selection idiom.
 */
@Composable
private fun FamilyNightChipRow(
    profiles: List<ProfileEntity>,
    selectedIds: Set<Long>,
    onToggle: (Long) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(title = "Who's watching tonight?")
        LazyRow(
            contentPadding = PaddingValues(horizontal = Dimens.Gutter),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(profiles, key = { it.id }) { profile ->
                val selected = profile.id in selectedIds
                Column(
                    modifier = Modifier.width(64.dp).clickableNoRipple { onToggle(profile.id) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    AvatarBadge(
                        option = avatarKeyToOption(profile.avatarKey),
                        size = 48.dp,
                        selected = selected,
                        name = profile.name,
                    )
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) Accent else ChalkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Closes the feed with the JustWatch attribution required on every availability-adjacent screen. */
@Composable
private fun HomeFooter() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.Gutter, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(R.string.justwatch_attribution),
            style = MaterialTheme.typography.labelSmall,
            color = ChalkFaint,
        )
    }
}
