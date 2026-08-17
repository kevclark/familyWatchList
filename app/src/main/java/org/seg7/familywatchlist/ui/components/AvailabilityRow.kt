package org.seg7.familywatchlist.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.seg7.familywatchlist.R
import org.seg7.familywatchlist.data.local.dao.AvailabilityBadge
import org.seg7.familywatchlist.data.local.entity.ProviderKind
import org.seg7.familywatchlist.ui.theme.Accent
import org.seg7.familywatchlist.ui.theme.Chalk
import org.seg7.familywatchlist.ui.theme.ChalkFaint
import org.seg7.familywatchlist.ui.theme.ChalkMuted
import org.seg7.familywatchlist.ui.theme.InkHairline
import org.seg7.familywatchlist.ui.theme.InkRaised

/**
 * Availability badges + the JustWatch credit, as one inseparable component.
 *
 * PLAN.md §3 requires "'Streaming data by JustWatch' text wherever availability badges render",
 * and CLAUDE.md restates it as a hard quality bar. Bundling the credit into the same composable
 * as the badges is the cheapest way to make that structurally true: there is no way to render
 * availability in this app without the credit coming along, so a future screen can't forget it.
 *
 * Services the family is subscribed to are tinted with the accent and float to the front (the
 * DAO sorts them there) — "you can watch this tonight" is the question this row exists to
 * answer. Everything else is a muted outline.
 */
@Composable
fun AvailabilityRow(
    badges: List<AvailabilityBadge>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 0.dp),
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (badges.isEmpty()) {
            Text(
                text = "Not on any UK streaming service we can see right now.",
                style = MaterialTheme.typography.bodyMedium,
                color = ChalkMuted,
                modifier = Modifier.padding(contentPadding),
            )
        } else {
            LazyRow(
                contentPadding = contentPadding,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(badges, key = { "${it.providerId}-${it.kind}" }) { badge -> ProviderBadge(badge) }
            }
        }
        // Required attribution — never render the badges above without this line.
        Text(
            text = stringResource(R.string.justwatch_attribution),
            style = MaterialTheme.typography.labelSmall,
            color = ChalkFaint,
            modifier = Modifier.padding(contentPadding),
        )
    }
}

@Composable
private fun ProviderBadge(badge: AvailabilityBadge, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(7.dp))
            .background(if (badge.subscribed) Accent.copy(alpha = 0.14f) else InkRaised)
            .border(
                width = 1.dp,
                color = if (badge.subscribed) Accent.copy(alpha = 0.55f) else InkHairline,
                shape = RoundedCornerShape(7.dp),
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        val logo = providerLogoUrl(badge.logoPath)
        if (logo != null) {
            AsyncImage(
                model = logo,
                contentDescription = null,
                modifier = Modifier.size(20.dp).clip(RoundedCornerShape(4.dp)),
            )
        }
        Column {
            Text(
                text = badge.name,
                style = MaterialTheme.typography.labelLarge,
                color = if (badge.subscribed) Chalk else ChalkMuted,
            )
            if (badge.kind == ProviderKind.FREE) {
                Text(text = "FREE", style = MaterialTheme.typography.labelSmall, color = ChalkFaint)
            }
        }
    }
}
