package org.seg7.familywatchlist.ui.activity

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.seg7.familywatchlist.data.local.entity.RefreshOutcome
import org.seg7.familywatchlist.data.recommend.ProfileRunSummary
import org.seg7.familywatchlist.data.repository.RefreshRun
import org.seg7.familywatchlist.ui.LocalAppContainer
import org.seg7.familywatchlist.ui.components.clickableNoRipple
import org.seg7.familywatchlist.ui.theme.Chalk
import org.seg7.familywatchlist.ui.theme.ChalkFaint
import org.seg7.familywatchlist.ui.theme.ChalkMuted
import org.seg7.familywatchlist.ui.theme.Crimson
import org.seg7.familywatchlist.ui.theme.Dimens
import org.seg7.familywatchlist.ui.theme.Ink
import org.seg7.familywatchlist.ui.theme.InkHairline
import org.seg7.familywatchlist.ui.theme.InkRaised

/**
 * PLAN.md §5d part 3 (M15): a log of recent refresh runs -- when, why it ran, how it ended, what it
 * found per profile, and whether the notification was posted or suppressed (and why). Reached from
 * Settings and by tapping Home's refresh banner. Same restrained language as the rest of the app:
 * raised-ink rows on near-black, hairline dividers, no colour except [Crimson] for a failure.
 */
@Composable
fun ActivityScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val viewModel: ActivityViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                ActivityViewModel(container.refreshLogRepository, container.userPreferencesRepository, container.clock)
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize().background(Ink).navigationBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Dimens.Gutter, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Chalk,
                modifier = Modifier.size(22.dp).clickableNoRipple(onBack),
            )
            Text(text = "Activity", style = MaterialTheme.typography.displaySmall, color = Chalk)
        }
        Text(
            text = state.header,
            style = MaterialTheme.typography.bodyMedium,
            color = ChalkMuted,
            modifier = Modifier.padding(horizontal = Dimens.Gutter).padding(bottom = 14.dp),
        )
        HorizontalDivider(thickness = 1.dp, color = InkHairline)

        if (state.loaded && state.runs.isEmpty()) {
            Text(
                text = "No refreshes recorded yet. The next one will appear here, including whether it sent a notification.",
                style = MaterialTheme.typography.bodyMedium,
                color = ChalkFaint,
                modifier = Modifier.padding(Dimens.Gutter),
            )
        } else {
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Dimens.Gutter, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.runs, key = { it.id }) { run -> RunRow(run, state.zone) }
            }
        }
    }
}

@Composable
private fun RunRow(run: RefreshRun, zone: java.time.ZoneId) {
    var expanded by rememberSaveable(run.id) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(InkRaised)
            .clickableNoRipple { expanded = !expanded }
            .animateContentSize()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = ActivityFormat.runTime(run.startedAt, zone),
                    style = MaterialTheme.typography.titleSmall,
                    color = Chalk,
                )
                Text(
                    text = "${ActivityFormat.trigger(run.trigger)} · ${ActivityFormat.outcome(run.outcome)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (run.outcome == RefreshOutcome.FAILED) Crimson else ChalkFaint,
                )
            }
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = ChalkFaint,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(text = ActivityFormat.headline(run), style = MaterialTheme.typography.bodyMedium, color = ChalkMuted)

        if (expanded) {
            run.profiles.forEach { p -> ProfileDetail(p) }
            if (run.outcome != RefreshOutcome.FAILED) run.reason?.let { Detail(it) }
            run.notificationStatus?.let { Detail("Notification — $it") }
        }
    }
}

@Composable
private fun ProfileDetail(p: ProfileRunSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 4.dp)) {
        Text(
            text = ActivityFormat.profileLine(p),
            style = MaterialTheme.typography.bodySmall,
            color = if (p.status == ProfileRunSummary.STATUS_FAILED) Crimson else Chalk,
        )
        ActivityFormat.titleList(p.newCount, p.newTitles)?.let { Detail("New: $it") }
        ActivityFormat.titleList(p.droppedCount, p.droppedTitles)?.let { Detail("Dropped off: $it") }
    }
}

@Composable
private fun Detail(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = ChalkMuted)
}
