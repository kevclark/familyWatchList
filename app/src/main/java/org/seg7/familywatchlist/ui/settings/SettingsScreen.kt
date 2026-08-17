package org.seg7.familywatchlist.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.seg7.familywatchlist.R
import org.seg7.familywatchlist.ui.LocalAppContainer
import org.seg7.familywatchlist.ui.components.clickableNoRipple
import org.seg7.familywatchlist.ui.theme.Chalk
import org.seg7.familywatchlist.ui.theme.ChalkFaint
import org.seg7.familywatchlist.ui.theme.ChalkMuted
import org.seg7.familywatchlist.ui.theme.Dimens
import org.seg7.familywatchlist.ui.theme.Ink
import org.seg7.familywatchlist.ui.theme.InkRaised

/**
 * PLAN.md §5 screen 8. Services toggles, JSON backup/restore and full profile management are
 * still M4; two rows are wired for real because PLAN.md calls for them outside M4:
 *  - "Switch profile" clears the active-profile flag, which bounces the app to the picker.
 *  - "Services & attribution setup" now sets [org.seg7.familywatchlist.data.repository
 *    .UserPreferencesRepository.servicesSetupRequested] instead of un-setting
 *    `onboardingComplete` (PLAN.md §5a known defect #2) — so the user lands on the services
 *    step with a close button, not at the top of a flow they can't escape.
 *
 * The TMDB/JustWatch notices are here verbatim per §3's "Settings → About and on onboarding".
 */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Ink)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.displaySmall,
            color = Chalk,
            modifier = Modifier.statusBarsPadding().padding(horizontal = Dimens.Gutter, vertical = 12.dp),
        )

        Column(
            modifier = Modifier.padding(horizontal = Dimens.Gutter),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SettingsRow(
                title = "Switch profile",
                subtitle = "Go back to the profile picker",
                onClick = { scope.launch { container.userPreferencesRepository.clearActiveProfileId() } },
            )
            SettingsRow(
                title = "Streaming services",
                subtitle = "Change which services you subscribe to",
                onClick = { scope.launch { container.userPreferencesRepository.setServicesSetupRequested(true) } },
            )
        }

        Text(
            text = "ABOUT",
            style = MaterialTheme.typography.labelSmall,
            color = ChalkFaint,
            modifier = Modifier.padding(start = Dimens.Gutter, top = 28.dp, bottom = 10.dp),
        )
        Column(
            modifier = Modifier.padding(horizontal = Dimens.Gutter, vertical = 0.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
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
                modifier = Modifier.padding(bottom = 32.dp),
            )
        }
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(InkRaised)
            .clickableNoRipple(onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = Chalk)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = ChalkMuted)
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = ChalkFaint,
            modifier = Modifier.size(20.dp),
        )
    }
}
