package org.seg7.familywatchlist.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.seg7.familywatchlist.ui.theme.Accent
import org.seg7.familywatchlist.ui.theme.Chalk
import org.seg7.familywatchlist.ui.theme.Dimens

/**
 * A section header for Home's stacked carousels. Bold condensed type (PLAN.md §5a typography),
 * with an optional tappable "see all" chevron.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    onSeeAll: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.Gutter, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = title, style = MaterialTheme.typography.titleLarge, color = Chalk)
        if (onSeeAll != null) {
            Row(
                modifier = Modifier
                    .clickableNoRipple(onSeeAll)
                    .padding(start = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "All", style = MaterialTheme.typography.labelMedium, color = Accent)
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "See all $title",
                    tint = Accent,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * One horizontal poster row in Home's single continuous feed (PLAN.md §5a Home structure).
 *
 * Edge-to-edge by construction: the `LazyRow` itself is full-bleed and only its *content* is
 * inset by the gutter, so posters scroll out under the screen edge instead of stopping short at
 * a margin. Inter-card spacing is [Dimens.CardGap] — §5a's "tight inter-card spacing".
 */
@Composable
fun <T> PosterCarousel(
    title: String,
    items: List<T>,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    onSeeAll: (() -> Unit)? = null,
    itemContent: @Composable (T) -> Unit,
) {
    if (items.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(title = title, onSeeAll = onSeeAll)
        LazyRow(
            contentPadding = PaddingValues(horizontal = Dimens.Gutter),
            horizontalArrangement = Arrangement.spacedBy(Dimens.CardGap),
        ) {
            items(items.size, key = { key(items[it]) }) { index -> itemContent(items[index]) }
        }
    }
}
