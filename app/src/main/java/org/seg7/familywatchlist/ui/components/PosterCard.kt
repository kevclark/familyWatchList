package org.seg7.familywatchlist.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.seg7.familywatchlist.ui.theme.Accent
import org.seg7.familywatchlist.ui.theme.Chalk
import org.seg7.familywatchlist.ui.theme.ChalkFaint
import org.seg7.familywatchlist.ui.theme.Crimson
import org.seg7.familywatchlist.ui.theme.Dimens
import org.seg7.familywatchlist.ui.theme.InkRaised
import org.seg7.familywatchlist.ui.theme.OnAccent

/**
 * The single poster primitive the whole app uses — Home carousels, search grid, My List.
 *
 * PLAN.md §5a: "posters are 2:3 thumbnails … no Material card borders/shadows/boxed backgrounds
 * around them", plus "subtle scale-on-press for poster cards". So this is deliberately *not* a
 * Material `Card`: it's the image, clipped, with nothing behind or around it. The only chrome
 * is the optional quick-add affordance, which floats on the art itself.
 *
 * [label] is off by default. In a carousel the poster art carries the title (that's how Netflix
 * and Prime do it, and it's what keeps §5a's imagery-to-text ratio right); the search grid turns
 * it on, because there you're scanning for a specific name.
 */
@Composable
fun PosterCard(
    title: String?,
    posterPath: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: androidx.compose.ui.unit.Dp = Dimens.PosterWidth,
    label: Boolean = false,
    year: Int? = null,
    onQuickAdd: (() -> Unit)? = null,
    isListed: Boolean = false,
    /**
     * PLAN.md §5a's M2g refinement: true for a watchlist entry that's since lost GB availability
     * on every subscribed provider (Home's "My List" carousel and the full My List screen are the
     * only two callers that ever pass this — search results are always gated available, so this
     * stays false there by construction). Dims the poster art and swaps the caption line for an
     * explanation, but never touches [onClick] — tapping through to details still works exactly
     * as before.
     */
    dimmed: Boolean = false,
    /**
     * A dedicated "clean this up" affordance shown only when [dimmed] is true — distinct from
     * [onQuickAdd]'s ✓/＋ toggle (which means "in my list" / "not in my list") because an
     * unavailable-but-listed item is neither of those; it needs a plain "take this off the list"
     * action, styled destructively (PLAN.md §5a M2g: "an explicit remove/clean-up action …
     * without requiring a detour through the details screen first"). Ignored when [onQuickAdd]
     * is also supplied, since no caller currently needs both on the same card.
     */
    onRemoveUnavailable: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.55f),
        label = "poster-press",
    )

    Column(
        modifier = modifier.width(width),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(Dimens.PosterAspect)
                .scale(scale)
                .clip(MaterialTheme.shapes.small)
                .clickableNoRipple(interactionSource, onClick)
                .semantics {
                    contentDescription = if (dimmed) {
                        "${title.orEmpty()} — no longer available on your services"
                    } else {
                        title.orEmpty()
                    }
                },
        ) {
            PosterArt(
                title = title,
                posterPath = posterPath,
                modifier = if (dimmed) Modifier.alpha(DimmedAlpha) else Modifier,
            )

            when {
                onQuickAdd != null -> QuickAddButton(
                    isListed = isListed,
                    title = title,
                    onClick = onQuickAdd,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp),
                )

                dimmed && onRemoveUnavailable != null -> RemoveUnavailableButton(
                    title = title,
                    onClick = onRemoveUnavailable,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp),
                )
            }
        }

        if (label) {
            Text(
                text = title.orEmpty(),
                style = MaterialTheme.typography.titleSmall,
                color = if (dimmed) ChalkFaint else Chalk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            when {
                // Dimmed overrides the year line — "why is this greyed out" matters more here
                // than the release year, and there's only room for one caption line.
                dimmed -> Text(
                    text = "Not on your services",
                    style = MaterialTheme.typography.bodySmall,
                    color = ChalkFaint,
                )
                year != null -> Text(
                    text = year.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = ChalkFaint,
                )
            }
        }
    }
}

/** PLAN.md §5a M2g: the alpha applied to a watchlist item's poster art once it's lost availability. */
private const val DimmedAlpha = 0.4f

/**
 * The art itself, with the fallback for the (common, on a fresh install) case of a title TMDB
 * has no poster for. A flat tile with the title set in it beats a broken-image glyph and keeps
 * the carousel's rhythm intact.
 */
@Composable
fun PosterArt(title: String?, posterPath: String?, modifier: Modifier = Modifier) {
    val url = posterUrl(posterPath)
    if (url == null) {
        Box(
            modifier = modifier.fillMaxSize().background(InkRaised),
            contentAlignment = Alignment.Center,
        ) {
            if (title.isNullOrBlank()) {
                Icon(Icons.Filled.Movie, contentDescription = null, tint = ChalkFaint)
            } else {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = ChalkFaint,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(10.dp),
                )
            }
        }
    } else {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            // A near-black placeholder means an unloaded poster reads as an empty slot rather
            // than a bright grey flash against the Ink ground while Coil crossfades it in.
            modifier = modifier.fillMaxSize().background(InkRaised),
        )
    }
}

/** ＋/✓ quick add-to-list, per PLAN.md §5 screen 5 ("quick add-to-list on each card"). */
@Composable
private fun QuickAddButton(
    isListed: Boolean,
    title: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(if (isListed) Accent else Color(0xCC0B0B0D))
            .clickableNoRipple(interactionSource, onClick)
            .semantics {
                contentDescription =
                    if (isListed) "Remove ${title.orEmpty()} from your list" else "Add ${title.orEmpty()} to your list"
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isListed) Icons.Filled.Check else Icons.Filled.Add,
            contentDescription = null,
            tint = if (isListed) OnAccent else Chalk,
            modifier = Modifier.size(16.dp),
        )
    }
}

/**
 * PLAN.md §5a M2g: "an explicit remove/clean-up action" for a watchlist item that's lost
 * availability, reachable straight off the card rather than requiring a trip through details.
 * Styled with [Crimson] (this app's existing destructive-action colour — see [History]'s delete
 * icon) rather than [Accent], so it reads as "take this off the list" and not as another
 * ✓/＋ toggle.
 */
@Composable
private fun RemoveUnavailableButton(
    title: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(Color(0xCC0B0B0D))
            .clickableNoRipple(interactionSource, onClick)
            .semantics {
                contentDescription = "Remove ${title.orEmpty()} from your list — no longer available"
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = null,
            tint = Crimson,
            modifier = Modifier.size(16.dp),
        )
    }
}
