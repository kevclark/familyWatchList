package org.seg7.familywatchlist.ui.settings

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.seg7.familywatchlist.BuildConfig
import org.seg7.familywatchlist.BuildStats
import org.seg7.familywatchlist.R
import org.seg7.familywatchlist.ui.components.clickableNoRipple
import org.seg7.familywatchlist.ui.theme.Accent
import org.seg7.familywatchlist.ui.theme.Chalk
import org.seg7.familywatchlist.ui.theme.ChalkFaint
import org.seg7.familywatchlist.ui.theme.ChalkMuted
import org.seg7.familywatchlist.ui.theme.Dimens
import org.seg7.familywatchlist.ui.theme.Ink
import org.seg7.familywatchlist.ui.theme.InkRaised

/**
 * PLAN.md §3 "TMDB logo + the exact notice ... in Settings → About" — M7 (PROGRESS.md "In-app
 * 'About this build' AI-transparency screen") moves this content off Settings' own scroll into
 * its own navigable destination, same pattern as [org.seg7.familywatchlist.ui.tune.TunePicksScreen]
 * (a full screen with its own back button, reached via a single row in Settings) rather than
 * another inline section — still satisfied, just relocated.
 *
 * Below the relocated attribution content sits a "Built with Claude" section: this app was built
 * heavily with AI, and Kev wants that visible rather than hidden. The figures are static, baked
 * into [BuildStats] from the project's own Claude Code session transcripts — not computable at
 * runtime from anything in Room/TMDB, and not a live counter. They're a point-in-time snapshot
 * (see [BuildStats.SNAPSHOT_DATE], shown directly in the UI below) that will go stale as
 * development continues, same as a version number does until the next bump.
 */
@Composable
fun AboutScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Ink)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding(),
    ) {
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
            Text(text = "About", style = MaterialTheme.typography.displaySmall, color = Chalk)
        }

        Text(
            text = "ATTRIBUTION",
            style = MaterialTheme.typography.labelSmall,
            color = ChalkFaint,
            modifier = Modifier.padding(start = Dimens.Gutter, top = 16.dp, bottom = 10.dp),
        )
        Column(
            modifier = Modifier.padding(horizontal = Dimens.Gutter),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // PLAN.md §3: "TMDB logo + the exact notice ... in Settings → About and on
            // onboarding" — same asset/wording as `ui/onboarding/AttributionStep.kt`.
            Image(
                painter = painterResource(R.drawable.ic_tmdb_logo),
                contentDescription = "The Movie Database (TMDB)",
            )
            Text(
                text = stringResource(R.string.tmdb_attribution),
                style = MaterialTheme.typography.bodySmall,
                color = ChalkFaint,
            )
            Text(
                text = stringResource(R.string.justwatch_attribution),
                style = MaterialTheme.typography.bodySmall,
                color = ChalkFaint,
            )
            Text(
                text = "Streaming availability is best-effort, especially for UK catch-up " +
                    "services — always double-check on the service itself.",
                style = MaterialTheme.typography.bodySmall,
                color = ChalkFaint,
            )
            // versionName is Android's own build-time field (build.gradle.kts), already
            // SemVer-shaped ("0.1.0-alpha.1") — no separate app-level version concept.
            Text(
                text = "Version ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = ChalkFaint,
            )
        }

        Text(
            text = "BUILT WITH CLAUDE",
            style = MaterialTheme.typography.labelSmall,
            color = ChalkFaint,
            modifier = Modifier.padding(start = Dimens.Gutter, top = 28.dp, bottom = 10.dp),
        )
        Column(
            modifier = Modifier.padding(horizontal = Dimens.Gutter, vertical = 0.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "This app was built almost entirely by Claude, an AI coding agent, working " +
                    "from a written spec under Kev's direction and review. These numbers describe " +
                    "that development process, not the app's own runtime data.",
                style = MaterialTheme.typography.bodySmall,
                color = ChalkMuted,
            )

            BuiltWithClaudeSummaryCard()

            BuildStats.MODEL_BREAKDOWN.forEach { model ->
                ModelStatsCard(model)
            }

            Text(
                text = "Snapshot as of ${BuildStats.SNAPSHOT_DATE} — these figures don't update live " +
                    "as development continues.",
                style = MaterialTheme.typography.labelSmall,
                color = ChalkFaint,
                modifier = Modifier.padding(bottom = 32.dp, top = 4.dp),
            )
        }
    }
}

@Composable
private fun BuiltWithClaudeSummaryCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(InkRaised)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "${BuildStats.TOTAL_PROMPTS} prompts · ${formatHours(BuildStats.TOTAL_HOURS)} of build time",
            style = MaterialTheme.typography.titleSmall,
            color = Chalk,
        )
        Text(
            text = "${BuildStats.TOTAL_AGENT_TASKS} background agent dispatches across 3 Claude models",
            style = MaterialTheme.typography.bodySmall,
            color = ChalkMuted,
        )
    }
}

@Composable
private fun ModelStatsCard(model: BuildStats.ModelStats) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(InkRaised)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = model.modelName, style = MaterialTheme.typography.titleSmall, color = Chalk)
            Text(
                text = "${model.taskCount} task${if (model.taskCount == 1) "" else "s"} · ${formatHours(model.hours)}",
                style = MaterialTheme.typography.labelSmall,
                color = Accent,
            )
        }
        val tokensAndCalls = formatTokensAndCalls(model.tokens, model.toolCalls)
        if (tokensAndCalls != null) {
            Text(text = tokensAndCalls, style = MaterialTheme.typography.labelSmall, color = ChalkFaint)
        }
        Text(text = model.usedFor, style = MaterialTheme.typography.bodySmall, color = ChalkMuted)
    }
}

/** e.g. 2.8 -> "2.8h", and the Haiku 4.5 sub-tenth-hour figure -> "36min" rather than a misleading "0.0h". */
private fun formatHours(hours: Double): String {
    if (hours < 0.1) {
        val minutes = (hours * 60).let { if (it < 1) "<1" else "%.0f".format(it) }
        return "${minutes}min"
    }
    return "%.1fh".format(hours)
}

private fun formatTokensAndCalls(tokens: Long?, toolCalls: Int?): String? {
    val parts = mutableListOf<String>()
    tokens?.let { parts += "${formatTokenCount(it)} tokens" }
    toolCalls?.let { parts += "$it tool calls" }
    return if (parts.isEmpty()) null else parts.joinToString(" · ")
}

private fun formatTokenCount(tokens: Long): String = when {
    tokens >= 1_000_000L -> "%.2fM".format(tokens / 1_000_000.0)
    tokens >= 1_000L -> "%.0fk".format(tokens / 1_000.0)
    else -> tokens.toString()
}
