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
import androidx.compose.foundation.lazy.LazyColumn
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
import org.seg7.familywatchlist.ui.LocalAppContainer
import org.seg7.familywatchlist.ui.avatar.AvatarBadge
import org.seg7.familywatchlist.ui.avatar.avatarKeyToOption
import org.seg7.familywatchlist.ui.components.BottomScrim
import org.seg7.familywatchlist.ui.components.PosterCard
import org.seg7.familywatchlist.ui.components.PosterCarousel
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
    activeProfile: ProfileEntity,
    onOpenTitle: (Int, MediaType) -> Unit,
    onOpenMyList: () -> Unit,
    onOpenSearch: () -> Unit,
    onSwitchProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalAppContainer.current
    val viewModel: HomeViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                HomeViewModel(
                    container.discoverRepository,
                    container.providerRepository,
                    container.watchlistRepository,
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
                HomeHero(
                    hero = state.hero,
                    isLoading = state.isLoading,
                    errorMessage = state.errorMessage,
                    onOpenTitle = onOpenTitle,
                    onRetry = viewModel::refresh,
                    onOpenSearch = onOpenSearch,
                )
            }

            if (state.myList.isNotEmpty()) {
                item(key = "my-list") {
                    PosterCarousel(
                        title = "My List",
                        items = state.myList,
                        key = { "${it.mediaType}-${it.tmdbId}" },
                        onSeeAll = onOpenMyList,
                    ) { item ->
                        PosterCard(
                            title = item.title,
                            posterPath = item.posterPath,
                            onClick = { onOpenTitle(item.tmdbId, item.mediaType) },
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

            item(key = "footer") { HomeFooter(activeProfile.name) }
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
 * Closes the feed. Says plainly that personalised rows are still to come — a one-line note
 * rather than PLAN.md §5's *For {profile}* / *Family night* rows rendered as empty boxes, which
 * is what M2a did and what Kev called "a wall of text boxes".
 */
@Composable
private fun HomeFooter(profileName: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.Gutter, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Picks for $profileName and family night arrive once there's enough watch history to learn from.",
            style = MaterialTheme.typography.bodySmall,
            color = ChalkFaint,
        )
        Text(
            text = stringResource(R.string.justwatch_attribution),
            style = MaterialTheme.typography.labelSmall,
            color = ChalkFaint,
        )
    }
}
