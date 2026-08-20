package org.seg7.familywatchlist.ui.tune

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlin.math.roundToInt
import org.seg7.familywatchlist.data.recommend.suggestionCountRange
import org.seg7.familywatchlist.ui.LocalAppContainer
import org.seg7.familywatchlist.ui.components.clickableNoRipple
import org.seg7.familywatchlist.ui.theme.Accent
import org.seg7.familywatchlist.ui.theme.Chalk
import org.seg7.familywatchlist.ui.theme.ChalkFaint
import org.seg7.familywatchlist.ui.theme.ChalkMuted
import org.seg7.familywatchlist.ui.theme.Dimens
import org.seg7.familywatchlist.ui.theme.Ink
import org.seg7.familywatchlist.ui.theme.InkHairline
import org.seg7.familywatchlist.ui.theme.InkRaised

/**
 * PLAN.md §4a: the four tunable taste sliders, each `s ∈ [-1, +1]`, default 0 (dead centre —
 * "the algorithm as already designed"), plus the "Suggestion count" control (slider 5) — a plain
 * integer, not a taste axis, see [SuggestionCountSlider]'s kdoc. Reachable from Settings (see
 * that screen's "Tune my picks" row); PLAN.md's "profile picker or Settings" is satisfied via
 * Settings, which is already scoped to the active profile — no need to duplicate the entry point
 * on the profile picker itself, which has no natural per-profile detail screen of its own to add
 * a row to.
 *
 * Changing a slider recomputes the active profile's shortlist after a short debounce
 * ([TunePicksViewModel.RECOMPUTE_DEBOUNCE_MS]) — see that class's kdoc. The family-blend slider
 * (slider 4) only renders once [TunePicksViewModel.familyBlendVisible] is true (PLAN.md §4a's
 * 2+-profiles UI-home decision).
 */
@Composable
fun TunePicksScreen(activeProfileId: Long, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val viewModel: TunePicksViewModel = viewModel(
        key = "tune-picks-$activeProfileId",
        factory = viewModelFactory {
            initializer {
                TunePicksViewModel(
                    activeProfileId,
                    container.profileSlidersRepository,
                    container.recommendationRepository,
                    container.userPreferencesRepository,
                )
            }
        },
    )

    val sliders by viewModel.sliders.collectAsStateWithLifecycle()
    val suggestionCount by viewModel.suggestionCount.collectAsStateWithLifecycle()
    val eligibleCandidateCount by viewModel.eligibleCandidateCount.collectAsStateWithLifecycle()
    val familyBlend by viewModel.familyBlend.collectAsStateWithLifecycle()
    val familyBlendVisible by viewModel.familyBlendVisible.collectAsStateWithLifecycle()

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
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Chalk,
                    modifier = Modifier.size(22.dp).clickableNoRipple(onBack),
                )
                Text(text = "Tune my picks", style = MaterialTheme.typography.displaySmall, color = Chalk)
            }
            TextButton(onClick = viewModel::resetToDefaults) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, tint = Accent, modifier = Modifier.size(16.dp))
                    Text("Reset", color = Accent, style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        Text(
            text = "These shape For You and Family Night — changes apply to your picks in a moment.",
            style = MaterialTheme.typography.bodySmall,
            color = ChalkMuted,
            modifier = Modifier.padding(start = Dimens.Gutter, end = Dimens.Gutter, bottom = 20.dp),
        )

        Column(
            modifier = Modifier.padding(horizontal = Dimens.Gutter),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            TuneSlider(
                title = "Discovery",
                leftLabel = "Sticks to my taste",
                rightLabel = "Surprise me",
                value = sliders.discovery,
                onValueChange = viewModel::onDiscoveryChange,
            )
            TuneSlider(
                title = "Recency",
                leftLabel = "All-time favourites",
                rightLabel = "Recent favourites",
                // PLAN.md §4a slider 2 is labelled "Recent (+1) <-> All-time (-1)" — the slider's
                // visual left-to-right order matches every other slider here (negative on the
                // left) by negating the displayed position only; the stored/scored value is
                // untouched.
                value = -sliders.recency,
                onValueChange = { viewModel.onRecencyChange(-it) },
            )
            TuneSlider(
                title = "Personal match vs. popular",
                leftLabel = "Personal match",
                rightLabel = "Popular & well-reviewed",
                value = sliders.personalMatch,
                onValueChange = viewModel::onPersonalMatchChange,
            )
            SuggestionCountSlider(
                value = suggestionCount,
                range = suggestionCountRange(eligibleCandidateCount),
                onValueChange = viewModel::onSuggestionCountChange,
            )
            if (familyBlendVisible) {
                TuneSlider(
                    title = "Family Night blend",
                    leftLabel = "Everyone's happy",
                    rightLabel = "Average taste wins",
                    value = familyBlend,
                    onValueChange = viewModel::onFamilyBlendChange,
                    footnote = "Applies to Family Night for everyone, not just you — it's a shared setting.",
                )
            }
        }
    }
}

@Composable
private fun TuneSlider(
    title: String,
    leftLabel: String,
    rightLabel: String,
    value: Double,
    onValueChange: (Float) -> Unit,
    footnote: String? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(InkRaised)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleSmall, color = Chalk)
        Slider(
            value = value.toFloat(),
            onValueChange = onValueChange,
            valueRange = -1f..1f,
            colors = SliderDefaults.colors(
                thumbColor = Accent,
                activeTrackColor = Accent,
                inactiveTrackColor = InkHairline,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = leftLabel, style = MaterialTheme.typography.labelSmall, color = ChalkFaint)
            Text(text = rightLabel, style = MaterialTheme.typography.labelSmall, color = ChalkFaint)
        }
        if (footnote != null) {
            Text(
                text = footnote,
                style = MaterialTheme.typography.labelSmall,
                color = ChalkFaint,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * PLAN.md §4a slider 5 ("Suggestion count", design corrected 2026-08-20) — a plain integer
 * *request*, not a signed `s ∈ [-1, +1]` taste value like the four sliders above, so it
 * deliberately doesn't reuse [TuneSlider]'s signed/labelled-endpoints styling. Same card chrome
 * for visual consistency with the rest of this screen, but a normal linear range slider showing
 * the actual number ("Suggestions: 30").
 *
 * [range] is [org.seg7.familywatchlist.data.recommend.suggestionCountRange] applied to the
 * profile's real, last-known eligible-candidate count — not a fixed guessed ceiling — so the
 * endpoint labels show the *actual current* min/max rather than static text. `null` means there
 * are currently zero eligible candidates for this profile: the control renders disabled with a
 * short explanatory message instead of an inverted/broken range.
 */
@Composable
private fun SuggestionCountSlider(value: Int, range: IntRange?, onValueChange: (Int) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(InkRaised)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (range == null) {
            Text(text = "Suggestions", style = MaterialTheme.typography.titleSmall, color = Chalk)
            Text(
                text = "No eligible titles right now — check back after your services or watch history change.",
                style = MaterialTheme.typography.labelSmall,
                color = ChalkFaint,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else if (range.first == range.last) {
            // A degenerate single-value range (eligible count below the RecommenderSpec.SUGGESTION_COUNT_MIN
            // floor, e.g. 1-3 eligible titles) — a Slider with an equal start/end valueRange has
            // nothing meaningful to drag, so show the fixed count as text instead of a live control.
            Text(text = "Suggestions: ${range.first}", style = MaterialTheme.typography.titleSmall, color = Chalk)
            Text(
                text = "Only ${range.first} eligible title${if (range.first == 1) "" else "s"} right now — that's the most we can suggest.",
                style = MaterialTheme.typography.labelSmall,
                color = ChalkFaint,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else {
            Text(text = "Suggestions: $value", style = MaterialTheme.typography.titleSmall, color = Chalk)
            Slider(
                value = value.coerceIn(range).toFloat(),
                onValueChange = { onValueChange(it.roundToInt()) },
                valueRange = range.first.toFloat()..range.last.toFloat(),
                steps = (range.last - range.first - 1).coerceAtLeast(0),
                colors = SliderDefaults.colors(
                    thumbColor = Accent,
                    activeTrackColor = Accent,
                    inactiveTrackColor = InkHairline,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "${range.first} titles", style = MaterialTheme.typography.labelSmall, color = ChalkFaint)
                Text(text = "${range.last} titles", style = MaterialTheme.typography.labelSmall, color = ChalkFaint)
            }
        }
    }
}
