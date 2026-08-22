package org.seg7.familywatchlist.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch
import org.seg7.familywatchlist.R
import org.seg7.familywatchlist.data.local.entity.FAMILY_PROFILE_SENTINEL_ID
import org.seg7.familywatchlist.data.remote.TmdbApi
import org.seg7.familywatchlist.data.repository.AccentColor
import org.seg7.familywatchlist.data.repository.BackupRepository
import org.seg7.familywatchlist.data.repository.RegionOption
import org.seg7.familywatchlist.data.repository.UserPreferencesRepository
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
import org.seg7.familywatchlist.work.RecommendationScheduler

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
fun SettingsScreen(activeProfileId: Long, onOpenTunePicks: () -> Unit, modifier: Modifier = Modifier) {
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
            // PLAN.md §5b M3i item 7: long-press → edit on the profile picker was otherwise the
            // only way to reach name/avatar/age-cap editing, and nobody would think to look for
            // it there. Reuses "Switch profile"'s exact navigation (clearActiveProfileId bounces
            // the app back to ProfilePickerScreen) rather than a second route/dialog — the
            // picker's own "Long-press a profile to edit or delete it" hint is already the entry
            // point this needs; this row just gets a person there and says how once they arrive.
            SettingsRow(
                title = "Manage profiles",
                subtitle = "Long-press a profile there to edit its name, avatar or age rating",
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
            // PLAN.md §4b (M3j/M3k): Family is now a fully independent profile with its own
            // storable slider settings and its own independently-scored shortlist (no longer the
            // fixed-weight blend the M3d comment this replaces used to describe), so "Tune my
            // picks" applies to it exactly like any real profile — no disabled branch needed.
            SettingsRow(
                title = "Tune my picks",
                subtitle = "Adjust discovery, recency, and personal-match sliders",
                onClick = onOpenTunePicks,
            )
        }

        Text(
            text = "NOTIFICATIONS",
            style = MaterialTheme.typography.labelSmall,
            color = ChalkFaint,
            modifier = Modifier.padding(start = Dimens.Gutter, top = 28.dp, bottom = 10.dp),
        )
        NotificationSettingsSection(modifier = Modifier.padding(horizontal = Dimens.Gutter))

        Text(
            text = "SCHEDULE",
            style = MaterialTheme.typography.labelSmall,
            color = ChalkFaint,
            modifier = Modifier.padding(start = Dimens.Gutter, top = 28.dp, bottom = 10.dp),
        )
        ScheduleSettingsSection(modifier = Modifier.padding(horizontal = Dimens.Gutter))

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
            text = "BACKUP & RESTORE",
            style = MaterialTheme.typography.labelSmall,
            color = ChalkFaint,
            modifier = Modifier.padding(start = Dimens.Gutter, top = 28.dp, bottom = 10.dp),
        )
        BackupRestoreSection(modifier = Modifier.padding(horizontal = Dimens.Gutter))

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
 * PLAN.md §4 "Per-profile notification control" (M3e). The master toggle is always visible; the
 * per-profile list underneath it (every individual, plus the Family profile if one exists) only
 * renders while the master is on — PLAN.md §4's own framing: "no point showing per-profile
 * toggles for a feature that's globally off." Turning the master off doesn't touch any
 * per-profile row's stored value — they're just not shown, and take effect again the moment the
 * master is switched back on.
 */
@Composable
private fun NotificationSettingsSection(modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val masterEnabled by container.userPreferencesRepository.notificationsEnabled
        .collectAsStateWithLifecycle(initialValue = true)
    val profiles by container.profileRepository.observeAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val familyProfile by container.familyProfileRepository.observe()
        .collectAsStateWithLifecycle(initialValue = null)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        NotificationToggleRow(
            title = "Weekly shortlist notifications",
            subtitle = "Master switch — also needs notification permission to actually fire",
            checked = masterEnabled,
            onCheckedChange = { enabled -> scope.launch { container.userPreferencesRepository.setNotificationsEnabled(enabled) } },
        )
        if (masterEnabled) {
            profiles.forEach { profile ->
                NotificationProfileToggleRow(profileId = profile.id, name = profile.name)
            }
            familyProfile?.let { family ->
                NotificationProfileToggleRow(profileId = FAMILY_PROFILE_SENTINEL_ID, name = family.profile.name)
            }
        }
    }
}

@Composable
private fun NotificationProfileToggleRow(profileId: Long, name: String) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val enabled by container.notificationPreferencesRepository.observe(profileId)
        .collectAsStateWithLifecycle(initialValue = true)

    NotificationToggleRow(
        title = name,
        subtitle = "Notify when $name's picks are ready",
        checked = enabled,
        onCheckedChange = { newValue ->
            scope.launch { container.notificationPreferencesRepository.setEnabled(profileId, newValue) }
        },
    )
}

@Composable
private fun NotificationToggleRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(InkRaised)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = Chalk)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = ChalkMuted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(),
        )
    }
}

/**
 * PLAN.md §4 "Configurable schedule" (M3f). Two simple dropdown rows — day-of-week and
 * hour-of-day, no minute granularity per the plan's explicit "don't over-build a full
 * time-of-day picker" call. Picking either one both persists the preference
 * ([UserPreferencesRepository.setRefreshSchedule]) *and* triggers
 * [RecommendationScheduler.rescheduleForSettingsChange] — the critical-correctness half of M3f:
 * writing the preference alone would leave the previously-scheduled `KEEP`-enqueued WorkManager
 * job running untouched (see that function's kdoc). Both happen from the same `launch` block so
 * the stored preference and the actually-scheduled job never drift apart.
 */
@Composable
private fun ScheduleSettingsSection(modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val appContext = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val day by container.userPreferencesRepository.refreshDayOfWeek
        .collectAsStateWithLifecycle(initialValue = UserPreferencesRepository.DEFAULT_REFRESH_DAY_OF_WEEK)
    val hour by container.userPreferencesRepository.refreshHour
        .collectAsStateWithLifecycle(initialValue = UserPreferencesRepository.DEFAULT_REFRESH_HOUR)

    fun apply(newDay: DayOfWeek, newHour: Int) {
        scope.launch {
            container.userPreferencesRepository.setRefreshSchedule(newDay, newHour)
            RecommendationScheduler.rescheduleForSettingsChange(appContext, newDay, newHour)
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ScheduleDayRow(selected = day, onSelect = { apply(it, hour) })
        ScheduleHourRow(selected = hour, onSelect = { apply(day, it) })
    }
}

@Composable
private fun ScheduleDayRow(selected: DayOfWeek, onSelect: (DayOfWeek) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(InkRaised)
                .clickableNoRipple { expanded = true }
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(text = "Refresh day", style = MaterialTheme.typography.titleSmall, color = Chalk)
                Text(
                    text = selected.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                    style = MaterialTheme.typography.bodySmall,
                    color = ChalkMuted,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = ChalkFaint,
                modifier = Modifier.size(20.dp),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DayOfWeek.entries.forEach { candidate ->
                DropdownMenuItem(
                    text = { Text(candidate.getDisplayName(TextStyle.FULL, Locale.getDefault())) },
                    onClick = {
                        onSelect(candidate)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ScheduleHourRow(selected: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(InkRaised)
                .clickableNoRipple { expanded = true }
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(text = "Refresh time", style = MaterialTheme.typography.titleSmall, color = Chalk)
                Text(
                    text = "%02d:00".format(selected),
                    style = MaterialTheme.typography.bodySmall,
                    color = ChalkMuted,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = ChalkFaint,
                modifier = Modifier.size(20.dp),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            (0..23).forEach { candidate ->
                DropdownMenuItem(
                    text = { Text("%02d:00".format(candidate)) },
                    onClick = {
                        onSelect(candidate)
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * PLAN.md §5 screen 8 "JSON backup/restore" (M4b) + §2's "Backup/restore: Settings →
 * export/import a single JSON of Profiles, WatchEvents, Ratings, WatchlistEntries,
 * Provider.subscribed ... via Storage Access Framework."
 *
 * Both actions go through Android's document picker (`ACTION_CREATE_DOCUMENT` /
 * `ACTION_OPEN_DOCUMENT`, wired here via [ActivityResultContracts.CreateDocument] /
 * [ActivityResultContracts.OpenDocument]) rather than any broad storage permission — the user
 * picks exactly where the file goes/comes from each time, nothing else on the device is touched.
 * The actual read/write and JSON shape live in
 * [org.seg7.familywatchlist.data.repository.BackupRepository]; this composable only owns the
 * SAF launchers and a one-line status message (success/error) shown until the next action.
 */
@Composable
private fun BackupRestoreSection(modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val appContext = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var isBusy by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        isBusy = true
        scope.launch {
            runCatching { container.backupRepository.exportTo(appContext, uri) }
                .onSuccess { status = "Backup saved." }
                .onFailure { status = "Couldn't save the backup: ${it.message ?: "unknown error"}" }
            isBusy = false
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        isBusy = true
        scope.launch {
            when (val outcome = container.backupRepository.importFrom(appContext, uri)) {
                is BackupRepository.RestoreOutcome.Success -> status = "Backup restored."
                is BackupRepository.RestoreOutcome.Error -> status = outcome.message
            }
            isBusy = false
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SettingsRow(
            title = "Export backup",
            subtitle = "Save profiles, watch history, ratings and your list as a JSON file",
            enabled = !isBusy,
            onClick = {
                status = null
                exportLauncher.launch(defaultBackupFileName())
            },
        )
        SettingsRow(
            title = "Restore from backup",
            subtitle = "Replaces current profiles, history, ratings and your list from a JSON file",
            enabled = !isBusy,
            onClick = {
                status = null
                restoreLauncher.launch(arrayOf("application/json"))
            },
        )
        if (isBusy) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(color = Chalk, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text("Working…", style = MaterialTheme.typography.bodySmall, color = ChalkMuted)
            }
        }
        status?.let {
            Text(text = it, style = MaterialTheme.typography.bodySmall, color = ChalkMuted, modifier = Modifier.padding(top = 2.dp))
        }
        Text(
            text = "The TMDB streaming cache isn't included — it refetches automatically after a restore.",
            style = MaterialTheme.typography.labelSmall,
            color = ChalkFaint,
        )
    }
}

/** e.g. "family-watchlist-backup-2026-08-22.json" — descriptive default, still fully editable in the SAF picker. */
private fun defaultBackupFileName(): String {
    val date = java.time.LocalDate.now().toString()
    return "family-watchlist-backup-$date.json"
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
                    // PLAN.md §5b M3i item 6: the current region is checkmarked once you find it,
                    // but nothing used to surface *where* it is in an otherwise unsorted,
                    // unscrolled full list. Sorting it to the top (over auto-scrolling to it)
                    // was the simpler of the two equally-valid fixes the plan allows, and reads
                    // more like "your current choice, right here" than a list that jumps under
                    // you the moment the sheet opens — the rest of the list stays in its original
                    // (still unfiltered/unsorted-by-name) order, `sortedBy` being stable.
                    val filtered = regions.orEmpty()
                        .filter {
                            query.isBlank() || it.englishName.contains(query.trim(), ignoreCase = true) ||
                                it.code.contains(query.trim(), ignoreCase = true)
                        }
                        .sortedBy { if (it.code == currentRegion) 0 else 1 }
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
private fun SettingsRow(title: String, subtitle: String, onClick: () -> Unit, enabled: Boolean = true) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(InkRaised)
            .then(if (enabled) Modifier.clickableNoRipple(onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = if (enabled) Chalk else ChalkFaint)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = ChalkMuted)
        }
        if (enabled) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = ChalkFaint,
                modifier = Modifier.size(20.dp),
            )
        }
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
