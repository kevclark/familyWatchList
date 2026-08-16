package org.seg7.familywatchlist.ui.nav

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import org.seg7.familywatchlist.data.local.entity.ProfileEntity
import org.seg7.familywatchlist.ui.history.HistoryScreen
import org.seg7.familywatchlist.ui.home.HomeScreen
import org.seg7.familywatchlist.ui.search.SearchScreen
import org.seg7.familywatchlist.ui.settings.SettingsScreen

private sealed class BottomTab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    data object Home : BottomTab("home", "Home", Icons.Filled.Home)
    data object Search : BottomTab("search", "Search", Icons.Filled.Search)
    data object History : BottomTab("history", "History", Icons.Filled.History)
    data object Settings : BottomTab("settings", "Settings", Icons.Filled.Settings)
}

private val BOTTOM_TABS = listOf(BottomTab.Home, BottomTab.Search, BottomTab.History, BottomTab.Settings)

/**
 * PLAN.md §5: the four-tab shell (Home/Search/History/Settings). Search/History/Settings are
 * empty scaffolds this pass (M2b fills Search & History; M4 fills Settings) — only Home needs
 * real structure per the M2a brief. A conventional [androidx.navigation.compose.NavHost] is the
 * right tool here (as opposed to [org.seg7.familywatchlist.ui.AppRoot]'s manual `when`): these
 * four destinations are genuine sibling tabs with their own back-stacks, which is exactly what
 * nav-compose is for.
 */
@Composable
fun MainScaffold(activeProfile: ProfileEntity, modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = backStackEntry?.destination
            NavigationBar {
                BOTTOM_TABS.forEach { tab ->
                    val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = BottomTab.Home.route,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            composable(BottomTab.Home.route) { HomeScreen(activeProfile = activeProfile) }
            composable(BottomTab.Search.route) { SearchScreen() }
            composable(BottomTab.History.route) { HistoryScreen() }
            composable(BottomTab.Settings.route) { SettingsScreen() }
        }
    }
}
