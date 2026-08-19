package org.seg7.familywatchlist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.seg7.familywatchlist.data.repository.AccentColor
import org.seg7.familywatchlist.ui.AppRoot
import org.seg7.familywatchlist.ui.LocalAppContainer
import org.seg7.familywatchlist.ui.theme.FamilyWatchListTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as FamilyWatchListApp).container
        setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                // PLAN.md §5a "Post-M2b decisions": accent is a live preference, default
                // OBSIDIAN. Collected here — the app's real composition root — and handed to
                // FamilyWatchListTheme so picking a new colour in Settings recomposes everything
                // immediately.
                val accentColor by container.userPreferencesRepository.accentColor
                    .collectAsStateWithLifecycle(initialValue = AccentColor.OBSIDIAN)
                FamilyWatchListTheme(accentColor = accentColor) {
                    AppRoot(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}
