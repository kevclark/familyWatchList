package org.seg7.familywatchlist.ui.home

import androidx.compose.runtime.Composable
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.TitleEntity
import org.seg7.familywatchlist.ui.components.PosterCard
import org.seg7.familywatchlist.ui.components.PosterCarousel

/**
 * PLAN.md §5i: "New films" / "New series", below the popular rows. [PosterCarousel] renders nothing for
 * an empty list, so an empty row hides itself (no header, no placeholder). Films that are only available
 * to rent/buy carry the existing RENT/BUY tag — the one place browsing shows them (Kev's sign-off).
 */
@Composable
internal fun NewFilmsRow(
    state: HomeUiState,
    onOpenTitle: (Int, MediaType) -> Unit,
    onToggleWatchlist: (TitleEntity) -> Unit,
    onLongPressDismiss: (TitleEntity) -> Unit,
) {
    PosterCarousel(
        title = "New films",
        items = state.newMovies,
        key = { "new-movie-${it.tmdbId}" },
    ) { title ->
        PosterCard(
            title = title.title,
            posterPath = title.posterPath,
            onClick = { onOpenTitle(title.tmdbId, MediaType.MOVIE) },
            onLongPress = { onLongPressDismiss(title) },
            onQuickAdd = { onToggleWatchlist(title) },
            isListed = (title.tmdbId to title.mediaType) in state.listedKeys,
            paidOnly = title.tmdbId in state.newMoviePaidOnlyIds,
        )
    }
}

@Composable
internal fun NewSeriesRow(
    state: HomeUiState,
    onOpenTitle: (Int, MediaType) -> Unit,
    onToggleWatchlist: (TitleEntity) -> Unit,
    onLongPressDismiss: (TitleEntity) -> Unit,
) {
    PosterCarousel(
        title = "New series",
        items = state.newTv,
        key = { "new-tv-${it.tmdbId}" },
    ) { title ->
        PosterCard(
            title = title.title,
            posterPath = title.posterPath,
            onClick = { onOpenTitle(title.tmdbId, MediaType.TV) },
            onLongPress = { onLongPressDismiss(title) },
            onQuickAdd = { onToggleWatchlist(title) },
            isListed = (title.tmdbId to title.mediaType) in state.listedKeys,
        )
    }
}
