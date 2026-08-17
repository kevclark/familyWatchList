package org.seg7.familywatchlist.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.seg7.familywatchlist.ui.components.FilterPill

/** PLAN.md §5 screen 2: "optional per-profile age-rating cap field (UK certs U/PG/12/15/18)." */
val UK_CERTIFICATES = listOf("U", "PG", "12", "15", "18")

@Composable
fun AgeRatingCapSelector(
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterPill(label = "No cap", selected = selected == null, onClick = { onSelect(null) })
        }
        items(UK_CERTIFICATES) { cert ->
            FilterPill(label = cert, selected = selected == cert, onClick = { onSelect(cert) })
        }
    }
}
