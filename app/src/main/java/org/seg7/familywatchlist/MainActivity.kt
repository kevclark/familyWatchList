package org.seg7.familywatchlist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
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
                FamilyWatchListTheme {
                    AppRoot(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}
