package org.seg7.familywatchlist.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.seg7.familywatchlist.R

/**
 * PLAN.md §3 Attribution + §8 risk note: the exact required TMDB notice, shown verbatim (from
 * `strings.xml`, so it can't drift from what's used elsewhere in the app), plus the JustWatch
 * credit. First screen of onboarding — nothing else is shown until this is acknowledged.
 */
@Composable
fun AttributionStep(
    onAcknowledge: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = "Welcome to Family Watchlist",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = "Before we get started, a couple of things this app relies on:",
            style = MaterialTheme.typography.bodyLarge,
        )
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Movie & TV data",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.tmdb_attribution),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Streaming availability",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.justwatch_attribution),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Availability is best-effort — always double check on the service itself.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Button(onClick = onAcknowledge, modifier = Modifier) {
            Text("Got it, let's go")
        }
    }
}
