package org.seg7.familywatchlist.ui.nav

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.ProfileEntity
import org.seg7.familywatchlist.ui.LocalAppContainer
import org.seg7.familywatchlist.ui.components.clickableNoRipple
import org.seg7.familywatchlist.ui.details.TitleDetailScreen
import org.seg7.familywatchlist.ui.history.HistoryScreen
import org.seg7.familywatchlist.ui.home.HomeScreen
import org.seg7.familywatchlist.ui.logwatch.LogWatchSheet
import org.seg7.familywatchlist.ui.search.SearchScreen
import org.seg7.familywatchlist.ui.settings.SettingsScreen
import org.seg7.familywatchlist.ui.watchlist.MyListScreen
import org.seg7.familywatchlist.ui.theme.Accent
import org.seg7.familywatchlist.ui.theme.ChalkFaint
import org.seg7.familywatchlist.ui.theme.Ink
import org.seg7.familywatchlist.ui.theme.InkHairline
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

private sealed class BottomTab(val route: String, val label: String, val icon: ImageVector) {
    data object Home : BottomTab("home", "Home", Icons.Filled.Home)
    data object Search : BottomTab("search", "Search", Icons.Filled.Search)
    data object MyList : BottomTab("mylist", "My List", Icons.Filled.Bookmark)
    data object History : BottomTab("history", "History", Icons.Filled.History)
    data object Settings : BottomTab("settings", "Settings", Icons.Filled.Settings)
}

private val BOTTOM_TABS = listOf(
    BottomTab.Home,
    BottomTab.Search,
    BottomTab.MyList,
    BottomTab.History,
    BottomTab.Settings,
)

private const val ROUTE_TITLE = "title/{mediaType}/{tmdbId}"

private fun titleRoute(tmdbId: Int, mediaType: MediaType) = "title/${mediaType.name}/$tmdbId"

/**
 * The app shell (PLAN.md §5). Five tabs plus a full-screen title-details destination, and the
 * log-watch sheet layered over whatever is showing.
 *
 * §5a's "minimal chrome" is why this is a hand-rolled bottom bar rather than Material 3's
 * `NavigationBar`: that component paints a tonal-elevation surface behind itself and a pill
 * "indicator" behind the selected icon, both of which read as Material rather than as a
 * streaming app. This is the same behaviour — a hairline over near-black, accent on the
 * selected tab — with none of the tonal chrome.
 *
 * Details is a route rather than an overlay because it needs its own back-stack entry: back out
 * of a title and you should land where you came from, whether that was Home, Search, My List or
 * History. The log-watch **sheet** is deliberately *not* a route — it's transient UI over the
 * current screen, and giving it a back-stack entry would mean "back" dismissed it into a
 * half-navigated state.
 */
@Composable
fun MainScaffold(activeProfile: ProfileEntity, modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()

    // The log-watch sheet's target: which title, and (when editing) which existing event.
    var logWatchTarget by remember { mutableStateOf<LogWatchTarget?>(null) }

    val openTitle: (Int, MediaType) -> Unit = { tmdbId, mediaType ->
        navController.navigate(titleRoute(tmdbId, mediaType))
    }
    val switchTab: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Ink)) {
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route
        // Details is full-bleed hero art — a bottom bar sitting on top of it would cut the
        // image and undo §5a's edge-to-edge intent, so the bar hides on that route only.
        val showBottomBar = currentRoute != ROUTE_TITLE

        Column(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = BottomTab.Home.route,
                modifier = Modifier.fillMaxSize().weight(1f),
            ) {
                composable(BottomTab.Home.route) {
                    HomeScreen(
                        activeProfile = activeProfile,
                        onOpenTitle = openTitle,
                        onOpenMyList = { switchTab(BottomTab.MyList.route) },
                        onOpenSearch = { switchTab(BottomTab.Search.route) },
                        onSwitchProfile = {
                            scope.launch { container.userPreferencesRepository.clearActiveProfileId() }
                        },
                    )
                }
                composable(BottomTab.Search.route) {
                    SearchScreen(
                        activeProfileId = activeProfile.id,
                        onOpenTitle = openTitle,
                        onOpenSettings = { switchTab(BottomTab.Settings.route) },
                    )
                }
                composable(BottomTab.MyList.route) {
                    MyListScreen(
                        activeProfileId = activeProfile.id,
                        onBack = { switchTab(BottomTab.Home.route) },
                        onOpenTitle = openTitle,
                    )
                }
                composable(BottomTab.History.route) {
                    HistoryScreen(
                        activeProfileId = activeProfile.id,
                        onEditEvent = { eventId, tmdbId, mediaType ->
                            logWatchTarget = LogWatchTarget(tmdbId, mediaType, eventId)
                        },
                        onOpenTitle = openTitle,
                    )
                }
                composable(BottomTab.Settings.route) { SettingsScreen() }

                composable(
                    route = ROUTE_TITLE,
                    arguments = listOf(
                        navArgument("mediaType") { type = NavType.StringType },
                        navArgument("tmdbId") { type = NavType.IntType },
                    ),
                ) { entry ->
                    val tmdbId = entry.arguments?.getInt("tmdbId") ?: return@composable
                    val mediaType = entry.arguments?.getString("mediaType")
                        ?.let { runCatching { MediaType.valueOf(it) }.getOrNull() }
                        ?: return@composable
                    TitleDetailScreen(
                        tmdbId = tmdbId,
                        mediaType = mediaType,
                        activeProfileId = activeProfile.id,
                        onBack = { navController.popBackStack() },
                        onLogWatch = { id, type -> logWatchTarget = LogWatchTarget(id, type, null) },
                    )
                }
            }

            if (showBottomBar) {
                MinimalBottomBar(
                    currentRoute = currentRoute,
                    onSelect = switchTab,
                )
            }
        }
    }

    logWatchTarget?.let { target ->
        LogWatchSheet(
            tmdbId = target.tmdbId,
            mediaType = target.mediaType,
            activeProfileId = activeProfile.id,
            editingEventId = target.editingEventId,
            onDismiss = { logWatchTarget = null },
        )
    }
}

private data class LogWatchTarget(
    val tmdbId: Int,
    val mediaType: MediaType,
    val editingEventId: Long?,
)

@Composable
private fun MinimalBottomBar(currentRoute: String?, onSelect: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().background(Ink)) {
        HorizontalDivider(thickness = 1.dp, color = InkHairline)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(58.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BOTTOM_TABS.forEach { tab ->
                val selected = currentRoute == tab.route
                val alpha by animateFloatAsState(if (selected) 1f else 0.65f, label = "tab-alpha")
                Column(
                    modifier = Modifier
                        .clickableNoRipple { onSelect(tab.route) }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        tint = if (selected) Accent else ChalkFaint.copy(alpha = alpha),
                        modifier = Modifier.size(21.dp),
                    )
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) Accent else ChalkFaint,
                    )
                }
            }
        }
    }
}
