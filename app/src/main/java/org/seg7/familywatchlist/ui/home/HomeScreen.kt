package org.seg7.familywatchlist.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.seg7.familywatchlist.data.local.entity.ProfileEntity
import org.seg7.familywatchlist.ui.avatar.AvatarBadge
import org.seg7.familywatchlist.ui.avatar.avatarKeyToOption

private data class HomeRow(val title: String, val emptyStateText: String)

/**
 * PLAN.md §5 screen 3: the four Home carousels, as empty-state placeholders — content browsing
 * (search/details/watchlist, M2b) and the recommender (M3) don't exist yet, so this pass is
 * about the shell (edge-to-edge, dark-first, active-profile greeting) rather than real rows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(activeProfile: ProfileEntity, modifier: Modifier = Modifier) {
    val rows = remember(activeProfile.id) {
        listOf(
            HomeRow("My List", "Nothing on your list yet — search is coming next."),
            HomeRow("For ${activeProfile.name}", "Watch a few things and we'll start picking for you."),
            HomeRow("Family night", "Pick who's watching to get a shared shortlist."),
            HomeRow("Popular on your services", "Set up your streaming services to see what's popular."),
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Family Watchlist") },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 16.dp),
                    ) {
                        AvatarBadge(option = avatarKeyToOption(activeProfile.avatarKey), size = 32.dp)
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            items(rows) { row -> HomeRowSection(row) }
        }
    }
}

@Composable
private fun HomeRowSection(row: HomeRow) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = row.title, style = MaterialTheme.typography.titleMedium)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Text(
                text = row.emptyStateText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp),
            )
        }
    }
}
