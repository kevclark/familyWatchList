package org.seg7.familywatchlist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.seg7.familywatchlist.ui.theme.FamilyWatchListTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FamilyWatchListTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ScaffoldStatus(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(24.dp),
                    )
                }
            }
        }
    }
}

/**
 * M0 placeholder screen. Its only job is to prove the toolchain end-to-end:
 * Compose renders, Material 3 theming applies, and the TMDB token reached BuildConfig.
 * The token itself is never displayed — only whether it is present.
 */
@Composable
fun ScaffoldStatus(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Family Watchlist",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "M0 — toolchain scaffold",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = buildString {
                append("TMDB token: ")
                append(if (BuildConfig.TMDB_ACCESS_TOKEN.isNotBlank()) "configured" else "MISSING")
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "minSdk 26 · compileSdk 37 · Compose Material 3",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ScaffoldStatusPreview() {
    FamilyWatchListTheme {
        ScaffoldStatus()
    }
}
