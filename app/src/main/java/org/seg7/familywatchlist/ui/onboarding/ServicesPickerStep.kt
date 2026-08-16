package org.seg7.familywatchlist.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.seg7.familywatchlist.data.local.entity.ProviderEntity

/**
 * PLAN.md §5 screen 1 + §2: subscribed-services picker, seeded from the Provider table with
 * the GB defaults pre-ticked (Netflix, Disney+, Amazon Prime Video, BBC iPlayer, Channel 4,
 * ITVX — [ProviderRepository.applyOnboardingDefaults]). The list is confirm/editable here.
 */
@Composable
fun ServicesPickerStep(
    providers: List<ProviderEntity>,
    loadState: ServicesLoadState,
    onToggle: (providerId: Int, subscribed: Boolean) -> Unit,
    onConfirm: () -> Unit,
    onRetry: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(24.dp)) {
        Text(
            text = "Which streaming services do you have?",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "We've pre-ticked the usual suspects — turn any off, or on, that don't match your household.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )

        when (loadState) {
            ServicesLoadState.Loading -> Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }

            is ServicesLoadState.Error -> Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(vertical = 16.dp),
            ) {
                Text(
                    text = "Couldn't load streaming services right now (${loadState.message}). You can set these up later in Settings.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onRetry) { Text("Retry") }
                    TextButton(onClick = onSkip) { Text("Skip for now") }
                }
            }

            ServicesLoadState.Loaded -> LazyColumn(modifier = Modifier.weight(1f)) {
                items(providers, key = { it.providerId }) { provider ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(text = provider.name, style = MaterialTheme.typography.bodyLarge)
                        Switch(
                            checked = provider.subscribed,
                            onCheckedChange = { onToggle(provider.providerId, it) },
                        )
                    }
                    HorizontalDivider()
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = onBack) { Text("Back") }
            Button(onClick = onConfirm, enabled = loadState !is ServicesLoadState.Loading) {
                Text("Continue")
            }
        }
    }
}
