package org.seg7.familywatchlist.ui.details

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.AddTask
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil3.compose.AsyncImage
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.RatingValue
import org.seg7.familywatchlist.ui.LocalAppContainer
import org.seg7.familywatchlist.ui.components.AvailabilityRow
import org.seg7.familywatchlist.ui.components.BottomScrim
import org.seg7.familywatchlist.ui.components.PosterArt
import org.seg7.familywatchlist.ui.components.TopScrim
import org.seg7.familywatchlist.ui.components.backdropUrl
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
 * PLAN.md §5 screen 4: backdrop hero, poster, year/runtime/cert, overview, cast chips,
 * availability badges + JustWatch attribution, ▶ Trailer, ＋ My List, Log watch, thumbs rating.
 *
 * Built to §5a: the backdrop runs full-bleed under the status bar with a scrim dissolving it
 * into the page, the back button floats over it with no app bar behind it, and the poster
 * overlaps the seam between hero and content the way a streaming service does.
 *
 * PLAN.md §5 also lists a "Because you liked …" reason line "when reached from a shortlist" —
 * not built here, because shortlists are M3's output and there is no route into this screen
 * that carries a reason yet.
 */
@Composable
fun TitleDetailScreen(
    tmdbId: Int,
    mediaType: MediaType,
    activeProfileId: Long,
    onBack: () -> Unit,
    onLogWatch: (Int, MediaType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalAppContainer.current
    val viewModel: TitleDetailViewModel = viewModel(
        key = "detail-$mediaType-$tmdbId",
        factory = viewModelFactory {
            initializer {
                TitleDetailViewModel(
                    container.titleRepository,
                    container.watchlistRepository,
                    container.ratingRepository,
                    tmdbId,
                    mediaType,
                    activeProfileId,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val title = state.title
    val snackbarHostState = remember { SnackbarHostState() }

    // PLAN.md §5a: a blocked ＋ My List tap (title not on a subscribed provider) surfaces as a
    // Snackbar rather than a silent no-op.
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is TitleDetailUiEvent.WatchlistBlocked -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Ink)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 36.dp),
        ) {
            item(key = "hero") {
                // Backdrop, scrim, and the poster/headline block layered in one Box. Overlaying
                // rather than offsetting a following item keeps the LazyColumn's measured
                // heights honest — a negative offset would leave dead space below the row.
                Box(modifier = Modifier.fillMaxWidth().background(InkRaised)) {
                    Box(modifier = Modifier.fillMaxWidth().aspectRatio(Dimens.BackdropAspect)) {
                        AsyncImage(
                            model = backdropUrl(title?.backdropPath),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        BottomScrim()
                    }
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(horizontal = Dimens.Gutter),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Box(
                            modifier = Modifier
                                .width(96.dp)
                                .aspectRatio(Dimens.PosterAspect)
                                .clip(MaterialTheme.shapes.small),
                        ) {
                            PosterArt(title = title?.title, posterPath = title?.posterPath)
                        }
                        Column(
                            modifier = Modifier.weight(1f).padding(bottom = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            Text(
                                text = title?.title.orEmpty(),
                                style = MaterialTheme.typography.headlineMedium,
                                color = Chalk,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = listOfNotNull(
                                    if (mediaType == MediaType.MOVIE) "FILM" else "SERIES",
                                    title?.year?.toString(),
                                    title?.runtimeMin?.let { "$it MIN" },
                                    title?.certification,
                                ).joinToString("  ·  "),
                                style = MaterialTheme.typography.labelSmall,
                                color = ChalkMuted,
                            )
                            if (state.genres.isNotEmpty()) {
                                Text(
                                    text = state.genres.joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ChalkFaint,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            item(key = "actions") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.Gutter, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { context.openTrailer(title?.trailerKey) },
                            enabled = title?.trailerKey != null,
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.small,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Accent,
                                contentColor = OnAccent,
                                disabledContainerColor = InkRaised,
                                disabledContentColor = ChalkFaint,
                            ),
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                            Text(
                                text = if (title?.trailerKey != null) "Trailer" else "No trailer",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                        OutlinedButton(
                            onClick = viewModel::toggleWatchlist,
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.small,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (state.isListed) Accent else InkHairline,
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (state.isListed) Accent else Chalk,
                            ),
                        ) {
                            Icon(
                                imageVector = if (state.isListed) Icons.Filled.Check else Icons.Filled.Add,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = if (state.isListed) "On list" else "My List",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = { onLogWatch(tmdbId, mediaType) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                        border = androidx.compose.foundation.BorderStroke(1.dp, InkHairline),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Chalk),
                    ) {
                        Icon(Icons.Outlined.AddTask, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(
                            text = "Log a watch",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                    ThumbsRow(current = state.myRating, onRate = viewModel::rate)
                }
            }

            if (!title?.overview.isNullOrBlank()) {
                item(key = "overview") {
                    Text(
                        text = title.overview.orEmpty(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = ChalkMuted,
                        modifier = Modifier.padding(horizontal = Dimens.Gutter, vertical = 4.dp),
                    )
                }
            }

            if (state.cast.isNotEmpty()) {
                item(key = "cast") {
                    DetailSection(title = "Cast") {
                        ChipFlow(items = state.cast.take(10))
                    }
                }
            }

            if (state.crew.isNotEmpty()) {
                item(key = "crew") {
                    DetailSection(
                        title = if (mediaType == MediaType.MOVIE) "Director" else "Created by",
                    ) {
                        ChipFlow(items = state.crew)
                    }
                }
            }

            item(key = "availability") {
                DetailSection(title = "Where to watch") {
                    AvailabilityRow(badges = state.availability)
                }
            }
        }

        // Floating back affordance over the hero — §5a: no solid app bar where imagery is.
        Box(modifier = Modifier.fillMaxWidth().height(96.dp)) { TopScrim() }
        Box(
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = Dimens.Gutter - 4.dp, top = 6.dp)
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0x800B0B0D))
                .clickableNoRipple(onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Chalk)
        }
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

/** PLAN.md §2/§5: thumbs up / neutral / down for the active profile. */
@Composable
private fun ThumbsRow(current: RatingValue?, onRate: (RatingValue) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "YOUR RATING",
            style = MaterialTheme.typography.labelSmall,
            color = ChalkFaint,
            modifier = Modifier.padding(end = 4.dp),
        )
        ThumbButton(Icons.Filled.ThumbUp, "Thumbs up", current == RatingValue.UP) { onRate(RatingValue.UP) }
        NeutralButton(current == RatingValue.NEUTRAL) { onRate(RatingValue.NEUTRAL) }
        ThumbButton(Icons.Filled.ThumbDown, "Thumbs down", current == RatingValue.DOWN) { onRate(RatingValue.DOWN) }
    }
}

@Composable
private fun ThumbButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(if (selected) Accent else InkRaised)
            .clickableNoRipple(onClick)
            .ratingSemantics(label, selected),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) OnAccent else ChalkMuted,
            modifier = Modifier.size(19.dp),
        )
    }
}

@Composable
private fun NeutralButton(selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(if (selected) Accent else InkRaised)
            .clickableNoRipple(onClick)
            .ratingSemantics("Neutral rating", selected),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "—",
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) OnAccent else ChalkMuted,
        )
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.Gutter, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleLarge, color = Chalk)
        content()
    }
}

/** Cast/crew chips. Wrapping rather than scrolling — the whole billed cast should be readable. */
@Composable
private fun ChipFlow(items: List<String>) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        items.forEach { name ->
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                color = ChalkMuted,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(InkRaised)
                    .padding(horizontal = 11.dp, vertical = 7.dp),
            )
        }
    }
}

/**
 * PLAN.md §1: "Trailers: TMDB /videos → YouTube key → Intent to YouTube app/browser. Zero-
 * dependency v1." The app intent is tried first so the YouTube app handles it when installed;
 * the https URL is the fallback, and the whole thing is a no-op when there's no key (the button
 * is disabled in that case anyway).
 */
private fun android.content.Context.openTrailer(youTubeKey: String?) {
    if (youTubeKey.isNullOrBlank()) return
    val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$youTubeKey"))
    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$youTubeKey"))
    try {
        startActivity(appIntent)
    } catch (_: ActivityNotFoundException) {
        runCatching { startActivity(webIntent) }
    }
}

private fun Modifier.ratingSemantics(label: String, isSelected: Boolean): Modifier =
    this.semantics {
        contentDescription = label
        selected = isSelected
    }
