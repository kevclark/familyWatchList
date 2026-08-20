package org.seg7.familywatchlist.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.seg7.familywatchlist.R
import org.seg7.familywatchlist.data.remote.TmdbApi
import org.seg7.familywatchlist.data.repository.AccentColor
import org.seg7.familywatchlist.data.repository.RegionOption
import org.seg7.familywatchlist.di.AppContainer
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
import org.seg7.familywatchlist.ui.theme.displayName
import org.seg7.familywatchlist.ui.theme.toColor

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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onOpenTunePicks: () -> Unit, modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val activeAccent by container.userPreferencesRepository.accentColor
        .collectAsStateWithLifecycle(initialValue = AccentColor.OBSIDIAN)
    val activeRegion by container.userPreferencesRepository.region
        .collectAsStateWithLifecycle(initialValue = TmdbApi.REGION_GB)
    val regionServicesMismatch by container.userPreferencesRepository.regionServicesMismatch
        .collectAsStateWithLifecycle(initialValue = false)
    var showRegionPicker by remember { mutableStateOf(false) }

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
            text = "RECOMMENDATIONS",
            style = MaterialTheme.typography.labelSmall,
            color = ChalkFaint,
            modifier = Modifier.padding(start = Dimens.Gutter, top = 28.dp, bottom = 10.dp),
        )
        Column(
            modifier = Modifier.padding(horizontal = Dimens.Gutter),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SettingsRow(
                title = "Tune my picks",
                subtitle = "Adjust discovery, recency, and personal-match sliders",
                onClick = onOpenTunePicks,
            )
        }

        Text(
            text = "REGION",
            style = MaterialTheme.typography.labelSmall,
            color = ChalkFaint,
            modifier = Modifier.padding(start = Dimens.Gutter, top = 28.dp, bottom = 10.dp),
        )
        Column(
            modifier = Modifier.padding(horizontal = Dimens.Gutter),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SettingsRow(
                title = "Region",
                subtitle = "TMDB doesn't detect this automatically — $activeRegion right now. " +
                    "Change it if you're travelling.",
                onClick = { showRegionPicker = true },
            )
            if (regionServicesMismatch) {
                RegionMismatchNotice(
                    // Cleared once they've actually been through the services picker again —
                    // see OnboardingViewModel.dismiss()/onServicesConfirmed(), both of which
                    // this row's flow ends at — not the instant this button is tapped.
                    onReviewServices = {
                        scope.launch { container.userPreferencesRepository.setServicesSetupRequested(true) }
                    },
                )
            }
        }

        Text(
            text = "APPEARANCE",
            style = MaterialTheme.typography.labelSmall,
            color = ChalkFaint,
            modifier = Modifier.padding(start = Dimens.Gutter, top = 28.dp, bottom = 10.dp),
        )
        AccentColorRow(
            active = activeAccent,
            onSelect = { accent -> scope.launch { container.userPreferencesRepository.setAccentColor(accent) } },
            modifier = Modifier.padding(horizontal = Dimens.Gutter),
        )

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

    if (showRegionPicker) {
        RegionPickerSheet(
            container = container,
            currentRegion = activeRegion,
            onDismiss = { showRegionPicker = false },
            onSelect = { region ->
                scope.launch {
                    container.userPreferencesRepository.setRegion(region.code)
                    // PLAN.md §7 M2f: a region change can otherwise leave up to 7 days of the
                    // old region's provider_availability rows looking "fresh" and mislabeled as
                    // the new region's — see TitleRepository.invalidateAllProviderData's kdoc.
                    // Discover pages don't need the same treatment: region is now part of their
                    // cache key (DiscoverRepository.queryHash), so an old-region page just sits
                    // unreached under its own hash rather than serving wrong data.
                    container.titleRepository.invalidateAllProviderData()
                }
                showRegionPicker = false
            },
        )
    }
}

/**
 * PLAN.md §7 M2f's open design question, resolved: switching region doesn't error and the
 * subscribed-provider list isn't auto-cleared (that would be surprising and lossy — the user
 * might switch back), but it may well now be wrong (BBC iPlayer/Channel 4/ITVX don't exist
 * outside the UK). A dismissible-by-fixing inline notice, not a blocking modal — Kev can ignore
 * it and keep using degraded results if he wants; the point is that the mismatch is visible
 * rather than a silent empty "Popular on your services" row with no explanation.
 */
@Composable
private fun RegionMismatchNotice(onReviewServices: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(InkRaised)
            .border(width = 1.dp, color = Crimson.copy(alpha = 0.35f), shape = MaterialTheme.shapes.medium)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Your streaming services were set up for a different region and may not be " +
                "available here — some rows might look sparse or empty until you update them.",
            style = MaterialTheme.typography.bodySmall,
            color = Chalk,
        )
        TextButton(onClick = onReviewServices) {
            Text("Review services", color = Crimson, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * PLAN.md §7 M2f: sourced from [org.seg7.familywatchlist.data.repository.RegionCatalogRepository]
 * (TMDB's own `/watch/providers/regions`) rather than a hand-maintained country list — same
 * substring-filter pattern as the onboarding services picker (PLAN.md §5a known defect fix).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RegionPickerSheet(
    container: AppContainer,
    currentRegion: String,
    onDismiss: () -> Unit,
    onSelect: (RegionOption) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    var regions by remember { mutableStateOf<List<RegionOption>?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching { container.regionCatalogRepository.getRegions() }
            .onSuccess { regions = it }
            .onFailure { loadError = it.message ?: "Couldn't load regions" }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = InkRaised,
        contentColor = Chalk,
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Dimens.Gutter)) {
            Text(
                text = "Region",
                style = MaterialTheme.typography.titleMedium,
                color = Chalk,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            when {
                loadError != null -> Text(
                    text = loadError ?: "Couldn't load regions",
                    style = MaterialTheme.typography.bodySmall,
                    color = Crimson,
                    modifier = Modifier.padding(bottom = 24.dp),
                )
                regions == null -> Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = Chalk) }
                else -> {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search regions") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Chalk,
                            unfocusedTextColor = Chalk,
                            focusedBorderColor = Chalk,
                            unfocusedBorderColor = InkHairline,
                            cursorColor = Chalk,
                        ),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    )
                    val filtered = regions.orEmpty().filter {
                        query.isBlank() || it.englishName.contains(query.trim(), ignoreCase = true) ||
                            it.code.contains(query.trim(), ignoreCase = true)
                    }
                    LazyColumn(modifier = Modifier.fillMaxWidth().height(420.dp)) {
                        items(filtered, key = { it.code }) { region ->
                            RegionRow(region = region, selected = region.code == currentRegion, onClick = { onSelect(region) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RegionRow(region: RegionOption, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickableNoRipple(onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = region.englishName, style = MaterialTheme.typography.bodyMedium, color = Chalk)
            Text(text = region.code, style = MaterialTheme.typography.labelSmall, color = ChalkFaint)
        }
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = "Current region", tint = Chalk, modifier = Modifier.size(20.dp))
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

/**
 * PLAN.md §5a "Post-M2b decisions": the four accent candidates as tappable swatches, checkmark
 * on the active one. Picking one calls [onSelect], which persists via
 * [org.seg7.familywatchlist.data.repository.UserPreferencesRepository.setAccentColor] — the
 * whole app recolours live off the back of that (see `ui/theme/Theme.kt`), so there's no local
 * "applying…" state to manage here.
 */
@Composable
private fun AccentColorRow(
    active: AccentColor,
    onSelect: (AccentColor) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(InkRaised)
            .padding(horizontal = 14.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        AccentColor.entries.forEach { candidate ->
            AccentSwatch(
                candidate = candidate,
                selected = candidate == active,
                onClick = { onSelect(candidate) },
            )
        }
    }
}

@Composable
private fun AccentSwatch(candidate: AccentColor, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clickableNoRipple(onClick)
            .semantics {
                this.selected = selected
                contentDescription = "${candidate.displayName} accent colour"
            },
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(candidate.toColor())
                .border(
                    width = if (selected) 2.dp else 0.dp,
                    color = if (selected) Chalk else Color.Transparent,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = Chalk,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Text(
            text = candidate.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) Chalk else ChalkMuted,
        )
    }
}
