package org.seg7.familywatchlist.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.seg7.familywatchlist.R
import org.seg7.familywatchlist.ui.LocalAppContainer

/**
 * Empty-scaffold Settings (PLAN.md §5 screen 8; services toggles, JSON backup/restore, and full
 * profile management are M4). Two things are wired for real in this pass because PLAN.md calls
 * for them directly:
 *  - onboarding is "reachable again from Settings later" (§5 screen 1) — the row below flips
 *    the DataStore flag straight back to false, and [org.seg7.familywatchlist.ui.AppRoot]'s
 *    reactive state does the rest.
 *  - the TMDB/JustWatch attribution notices belong "in Settings → About and on onboarding"
 *    (§3) — shown verbatim here, not just on first run.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text("Settings") }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            ListItem(
                headlineContent = { Text("Switch profile") },
                supportingContent = { Text("Return to the profile picker") },
                modifier = Modifier.fillMaxWidth().clickable {
                    scope.launch { container.userPreferencesRepository.clearActiveProfileId() }
                },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Services & attribution setup") },
                supportingContent = { Text("Re-run the streaming services picker") },
                modifier = Modifier.fillMaxWidth().clickable {
                    scope.launch { container.userPreferencesRepository.setOnboardingComplete(false) }
                },
            )
            HorizontalDivider()

            Text(
                text = "About",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp),
            )
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(text = stringResource(R.string.tmdb_attribution), style = MaterialTheme.typography.bodyMedium)
                    Text(text = stringResource(R.string.justwatch_attribution), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
