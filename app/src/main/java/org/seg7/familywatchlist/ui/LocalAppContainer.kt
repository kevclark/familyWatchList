package org.seg7.familywatchlist.ui

import androidx.compose.runtime.staticCompositionLocalOf
import org.seg7.familywatchlist.di.AppContainer

/**
 * Hands the single manual-DI [AppContainer] (PLAN.md §1) down the Compose tree so screens can
 * build their ViewModels without threading a `container` parameter through every composable.
 * Provided once, at the root, in `MainActivity`.
 */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("LocalAppContainer not provided — wrap the composition root in CompositionLocalProvider")
}
