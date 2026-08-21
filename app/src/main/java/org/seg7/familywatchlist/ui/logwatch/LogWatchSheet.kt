package org.seg7.familywatchlist.ui.logwatch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.RatingValue
import org.seg7.familywatchlist.ui.ActiveProfile
import org.seg7.familywatchlist.ui.LocalAppContainer
import org.seg7.familywatchlist.ui.avatar.AvatarBadge
import org.seg7.familywatchlist.ui.avatar.avatarKeyToOption
import org.seg7.familywatchlist.ui.components.FilterPill
import org.seg7.familywatchlist.ui.components.PosterArt
import org.seg7.familywatchlist.ui.components.clickableNoRipple
import org.seg7.familywatchlist.ui.theme.Accent
import org.seg7.familywatchlist.ui.theme.Chalk
import org.seg7.familywatchlist.ui.theme.ChalkFaint
import org.seg7.familywatchlist.ui.theme.ChalkMuted
import org.seg7.familywatchlist.ui.theme.Crimson
import org.seg7.familywatchlist.ui.theme.Dimens
import org.seg7.familywatchlist.ui.theme.Ink
import org.seg7.familywatchlist.ui.theme.InkRaised
import org.seg7.familywatchlist.ui.theme.OnAccent

/** Test tags for the log-watch Compose UI test (PLAN.md §7: the one UI-tested flow). */
object LogWatchTags {
    const val SHEET = "log_watch_sheet"
    const val SAVE = "log_watch_save"
    const val ERROR = "log_watch_error"
    fun profileChip(profileId: Long) = "log_watch_profile_$profileId"
}

/**
 * PLAN.md §4 (M3d)'s "auto-tag every member" shortcut, extracted as a pure function (same
 * pattern as [org.seg7.familywatchlist.ui.resolveStartState]/[LogWatchViewModel.validate]) so
 * it's directly unit-testable without Compose/Robolectric. A real person pre-ticks just
 * themself; the Family profile pre-ticks every one of its real, curated members — never its own
 * sentinel [org.seg7.familywatchlist.data.local.entity.FAMILY_PROFILE_SENTINEL_ID], which is
 * never a selectable chip in the first place (the chip list is sourced from real profiles only).
 */
internal fun initialLogWatchSelection(activeProfile: ActiveProfile): Set<Long> = when (activeProfile) {
    is ActiveProfile.Family -> activeProfile.memberProfileIds.toSet()
    is ActiveProfile.Individual -> setOf(activeProfile.id)
}

/**
 * PLAN.md §5 screen 6. Opens as a modal sheet over whatever screen asked for it (details or
 * history), so logging never costs a navigation.
 *
 * PLAN.md §4 (M3d): [activeProfile] drives which chips start pre-ticked — a real person
 * pre-ticks just themself (unchanged since M2b); the Family profile pre-ticks every one of its
 * real members (see [ActiveProfile.Family.memberProfileIds]), the "auto-tag" shortcut. Never the
 * Family profile's own sentinel id — that's never a selectable chip at all, since the chip list
 * is sourced from [org.seg7.familywatchlist.data.repository.ProfileRepository.observeAll] (real
 * people only), matching PLAN.md §4's "nothing is ever logged directly against it".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogWatchSheet(
    tmdbId: Int,
    mediaType: MediaType,
    activeProfile: ActiveProfile,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    editingEventId: Long? = null,
) {
    val container = LocalAppContainer.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val initialSelectedProfileIds = initialLogWatchSelection(activeProfile)
    val viewModel: LogWatchViewModel = viewModel(
        key = "logwatch-$mediaType-$tmdbId-$editingEventId",
        factory = viewModelFactory {
            initializer {
                LogWatchViewModel(
                    watchEventRepository = container.watchEventRepository,
                    ratingRepository = container.ratingRepository,
                    titleRepository = container.titleRepository,
                    profileRepository = container.profileRepository,
                    tmdbId = tmdbId,
                    mediaType = mediaType,
                    initialSelectedProfileIds = initialSelectedProfileIds,
                    today = LocalDate.now(),
                    editingEventId = editingEventId,
                )
            }
        },
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = InkRaised,
        contentColor = Chalk,
        dragHandle = null,
        modifier = modifier.testTag(LogWatchTags.SHEET),
    ) {
        LogWatchSheetContent(viewModel = viewModel, onSaved = onDismiss, onCancel = onDismiss)
    }
}

/**
 * Split out from [LogWatchSheet] so the Compose UI test can drive the form directly without
 * standing up a `ModalBottomSheet` (whose window/animation machinery is what makes sheet tests
 * flaky under Robolectric). The form is the part with the behaviour worth testing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogWatchSheetContent(
    viewModel: LogWatchViewModel,
    onSaved: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showDatePicker by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = Dimens.Gutter, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // Header: poster + title, so it's unambiguous what's being logged.
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(46.dp)
                    .aspectRatio(Dimens.PosterAspect)
                    .clip(MaterialTheme.shapes.extraSmall),
            ) {
                PosterArt(title = state.titleName, posterPath = state.posterPath)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (state.isEditing) "EDIT WATCH" else "LOG A WATCH",
                    style = MaterialTheme.typography.labelSmall,
                    color = Accent,
                )
                Text(
                    text = state.titleName.orEmpty(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Chalk,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Date — defaults to today so the common case needs no interaction at all.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("WHEN", style = MaterialTheme.typography.labelSmall, color = ChalkFaint)
            FilterPill(
                label = state.watchedAt.displayLabel(state.today),
                selected = true,
                onClick = { showDatePicker = true },
            )
        }

        // Who watched — PLAN.md §2's multi-tag: family night is one event with N profiles.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("WHO WATCHED", style = MaterialTheme.typography.labelSmall, color = ChalkFaint)
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.profiles.forEach { profile ->
                    val selected = profile.id in state.selectedProfileIds
                    FilterPill(
                        label = profile.name,
                        selected = selected,
                        onClick = { viewModel.toggleProfile(profile.id) },
                        modifier = Modifier.testTag(LogWatchTags.profileChip(profile.id)),
                        leading = {
                            AvatarBadge(
                                option = avatarKeyToOption(profile.avatarKey),
                                size = 20.dp,
                                name = profile.name,
                            )
                        },
                    )
                }
            }
        }

        // Optional per-profile thumbs, shown only for the people actually tagged.
        val tagged = state.profiles.filter { it.id in state.selectedProfileIds }
        if (tagged.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "HOW WAS IT? (OPTIONAL)",
                    style = MaterialTheme.typography.labelSmall,
                    color = ChalkFaint,
                )
                tagged.forEach { profile ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = profile.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = ChalkMuted,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RatingDot(
                                selected = state.ratings[profile.id] == RatingValue.UP,
                                label = "${profile.name} thumbs up",
                            ) { viewModel.setRating(profile.id, RatingValue.UP) }
                            RatingDot(
                                selected = state.ratings[profile.id] == RatingValue.NEUTRAL,
                                label = "${profile.name} neutral",
                                neutral = true,
                            ) { viewModel.setRating(profile.id, RatingValue.NEUTRAL) }
                            RatingDot(
                                selected = state.ratings[profile.id] == RatingValue.DOWN,
                                label = "${profile.name} thumbs down",
                                down = true,
                            ) { viewModel.setRating(profile.id, RatingValue.DOWN) }
                        }
                    }
                }
            }
        }

        if (state.validationError != null) {
            Text(
                text = state.validationError!!,
                style = MaterialTheme.typography.bodyMedium,
                color = Crimson,
                modifier = Modifier.testTag(LogWatchTags.ERROR),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text("Cancel", color = ChalkMuted, style = MaterialTheme.typography.labelLarge)
            }
            Button(
                onClick = { viewModel.save(onSaved) },
                enabled = !state.isSaving,
                modifier = Modifier.weight(2f).testTag(LogWatchTags.SAVE),
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent),
            ) {
                Text(
                    text = if (state.isEditing) "Save changes" else "Log it",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.watchedAt
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            colors = androidx.compose.material3.DatePickerDefaults.colors(containerColor = InkRaised),
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        viewModel.setDate(
                            Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate(),
                        )
                    }
                    showDatePicker = false
                }) { Text("Set", color = Accent) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel", color = ChalkMuted) }
            },
        ) {
            DatePicker(
                state = pickerState,
                colors = androidx.compose.material3.DatePickerDefaults.colors(
                    containerColor = InkRaised,
                    selectedDayContainerColor = Accent,
                    selectedDayContentColor = OnAccent,
                    todayDateBorderColor = Accent,
                ),
            )
        }
    }
}

@Composable
private fun RatingDot(
    selected: Boolean,
    label: String,
    neutral: Boolean = false,
    down: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(if (selected) Accent else Ink)
            .clickableNoRipple(onClick)
            .semanticsFor(label, selected),
        contentAlignment = Alignment.Center,
    ) {
        val tint = if (selected) OnAccent else ChalkMuted
        when {
            neutral -> Text("—", style = MaterialTheme.typography.titleMedium, color = tint)
            down -> Icon(Icons.Filled.ThumbDown, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
            else -> Icon(Icons.Filled.ThumbUp, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
        }
    }
}

private fun Modifier.semanticsFor(label: String, isSelected: Boolean): Modifier =
    this.semantics {
        contentDescription = label
        selected = isSelected
    }

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM yyyy")

/**
 * "Today"/"Yesterday" read faster than a date, and those are by far the two common cases.
 *
 * Compares against the sheet's own [today] (the [LogWatchViewModel] constructor param it was
 * opened with) rather than the wall clock: this was previously `LocalDate.now()` internally,
 * which is a real-clock dependency wearing a pure-function disguise — harmless in the app itself
 * (the sheet's `today` always *is* the real "today" there), but it made this label silently drift
 * out of sync with a test's deliberately-pinned `today` the moment a run crossed a real midnight,
 * which is exactly what broke `LogWatchFlowUiTest` mid-session here.
 */
private fun LocalDate.displayLabel(today: LocalDate): String = when (this) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> format(DATE_FORMAT)
}
