package org.seg7.familywatchlist.ui.logwatch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.ProfileEntity
import org.seg7.familywatchlist.data.local.entity.RatingValue
import org.seg7.familywatchlist.data.repository.ProfileRepository
import org.seg7.familywatchlist.data.repository.RatingRepository
import org.seg7.familywatchlist.data.repository.TitleRepository
import org.seg7.familywatchlist.data.repository.WatchEventRepository

/**
 * PLAN.md §5 screen 6: "date (default today), profile multi-select chips, optional per-profile
 * thumbs right in the sheet. One tap for the common case."
 *
 * "One tap for the common case" is the design constraint that shapes this whole class: the sheet
 * opens with today's date already set and the active profile already ticked, so the common
 * action — *I watched this, today* — is Save and nothing else. Everything beyond that (other
 * people, a different date, thumbs) is optional and additive.
 *
 * The same ViewModel backs editing an existing event from History (PLAN.md §5 screen 7's "tap
 * to edit/delete"): pass [editingEventId] and it loads that event's date and profile tags
 * instead of the defaults, and saves through
 * [WatchEventRepository.updateWatch] rather than logging a new one.
 *
 * ## Family auto-tag (PLAN.md §4b, M3j — supersedes M3d)
 * [initialSelectedProfileIds] is what actually decides the pre-ticked chips — the common
 * single-person case passes `setOf(activeProfile.id)` (unchanged from M2b), but logging a watch
 * while the *Family* profile is active passes every real member's [ProfileEntity.id] **plus
 * Family's own [org.seg7.familywatchlist.data.local.entity.FAMILY_PROFILE_SENTINEL_ID]**
 * ([org.seg7.familywatchlist.ui.logwatch.LogWatchSheet] computes which). There is deliberately no
 * separate "Family" code path below this point beyond that initial value: [selectedProfileIds] is
 * the exact same `Set<Long>` the manual multi-select chips already write through [toggleProfile]
 * and [save] — it happens to include the sentinel id when Family auto-tagged it, and [save] just
 * writes a [org.seg7.familywatchlist.data.local.entity.WatchEventProfileEntity] row per id in that
 * set, sentinel included, since neither that entity nor `RatingEntity` has a `@ForeignKey` to
 * `profiles`. As of M3j, logging a watch while Family is active is *not* equivalent to a manual
 * multi-select of just its members any more — it also tags Family itself, which a manual
 * multi-select of real people alone could never do (Family isn't a selectable chip).
 */
class LogWatchViewModel(
    private val watchEventRepository: WatchEventRepository,
    private val ratingRepository: RatingRepository,
    private val titleRepository: TitleRepository,
    profileRepository: ProfileRepository,
    private val tmdbId: Int,
    private val mediaType: MediaType,
    initialSelectedProfileIds: Set<Long>,
    private val today: LocalDate,
    private val editingEventId: Long? = null,
) : ViewModel() {

    private val _form = MutableStateFlow(
        LogWatchForm(watchedAt = today, selectedProfileIds = initialSelectedProfileIds),
    )

    val uiState: StateFlow<LogWatchUiState> = combine(
        profileRepository.observeAll(),
        _form,
        titleRepository.observeTitle(tmdbId, mediaType),
    ) { profiles, form, title ->
        LogWatchUiState(
            profiles = profiles,
            titleName = title?.title,
            posterPath = title?.posterPath,
            today = today,
            watchedAt = form.watchedAt,
            selectedProfileIds = form.selectedProfileIds,
            ratings = form.ratings,
            isEditing = editingEventId != null,
            isSaving = form.isSaving,
            saved = form.saved,
            validationError = form.validationError,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LogWatchUiState())

    init {
        if (editingEventId != null) loadExistingEvent(editingEventId)
    }

    private fun loadExistingEvent(eventId: Long) {
        viewModelScope.launch {
            val event = watchEventRepository.getEvent(eventId) ?: return@launch
            val taggedProfiles = watchEventRepository.getProfileIds(eventId)
            // Only the tagged profiles' existing thumbs are pre-filled — a rating by someone who
            // wasn't on this watch belongs to a different viewing and isn't this sheet's to edit.
            val existingRatings = ratingRepository.getForTitle(tmdbId, mediaType)
                .filterKeys { it in taggedProfiles }
            _form.value = _form.value.copy(
                watchedAt = event.watchedAt,
                selectedProfileIds = taggedProfiles.toSet(),
                ratings = existingRatings,
            )
        }
    }

    fun setDate(date: LocalDate) {
        _form.value = _form.value.copy(watchedAt = date, validationError = null)
    }

    fun toggleProfile(profileId: Long) {
        val current = _form.value
        val next = if (profileId in current.selectedProfileIds) {
            current.selectedProfileIds - profileId
        } else {
            current.selectedProfileIds + profileId
        }
        // Untagging someone also drops the thumbs you'd set for them — a rating with nobody
        // attached to the watch would be written for a profile that didn't watch it.
        _form.value = current.copy(
            selectedProfileIds = next,
            ratings = current.ratings.filterKeys { it in next },
            validationError = null,
        )
    }

    /** Per-profile thumbs, optional. Tapping the same value again clears it. */
    fun setRating(profileId: Long, value: RatingValue) {
        val current = _form.value
        if (profileId !in current.selectedProfileIds) return
        val next = if (current.ratings[profileId] == value) {
            current.ratings - profileId
        } else {
            current.ratings + (profileId to value)
        }
        _form.value = current.copy(ratings = next)
    }

    /**
     * Validation is deliberately thin — two rules, both of which describe an event that can't
     * have happened rather than a style preference:
     *  - at least one profile must be tagged (PLAN.md §2's WatchEventProfile is the multi-tag
     *    join; an event with no tags contributes to nobody's affinity vector in §4 and would
     *    never show up in History's per-profile filter — it'd be invisible data)
     *  - the date can't be in the future
     */
    fun save(onSaved: () -> Unit = {}) {
        val form = _form.value
        val error = validate(form.selectedProfileIds, form.watchedAt, today)
        if (error != null) {
            _form.value = form.copy(validationError = error)
            return
        }

        viewModelScope.launch {
            _form.value = _form.value.copy(isSaving = true, validationError = null)
            val profileIds = form.selectedProfileIds.toList()

            if (editingEventId != null) {
                watchEventRepository.updateWatch(editingEventId, form.watchedAt, null, profileIds)
            } else {
                // Auto-flips a matching ACTIVE watchlist entry to WATCHED (PLAN.md §2) — that
                // lives in the repository so every logging route gets it, not just this sheet.
                watchEventRepository.logWatch(tmdbId, mediaType, form.watchedAt, null, profileIds)
            }

            form.ratings.forEach { (profileId, value) ->
                ratingRepository.rate(profileId, tmdbId, mediaType, value)
            }

            _form.value = _form.value.copy(isSaving = false, saved = true)
            onSaved()
        }
    }

    private data class LogWatchForm(
        val watchedAt: LocalDate,
        val selectedProfileIds: Set<Long>,
        val ratings: Map<Long, RatingValue> = emptyMap(),
        val isSaving: Boolean = false,
        val saved: Boolean = false,
        val validationError: String? = null,
    )

    companion object {
        /** Pure so the rules are unit-testable without a ViewModel — same pattern as `resolveStartState`. */
        fun validate(profileIds: Set<Long>, watchedAt: LocalDate, today: LocalDate): String? = when {
            profileIds.isEmpty() -> "Pick at least one person who watched this"
            watchedAt.isAfter(today) -> "You can't log a watch in the future"
            else -> null
        }
    }
}

data class LogWatchUiState(
    val profiles: List<ProfileEntity> = emptyList(),
    val titleName: String? = null,
    val posterPath: String? = null,
    /** The sheet's notion of "today" (its constructor's [LogWatchViewModel] `today` param) — the "Today"/"Yesterday" label compares [watchedAt] against this, not the wall clock, so tests can pin it. */
    val today: LocalDate = LocalDate.now(),
    val watchedAt: LocalDate = LocalDate.now(),
    val selectedProfileIds: Set<Long> = emptySet(),
    val ratings: Map<Long, RatingValue> = emptyMap(),
    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val validationError: String? = null,
)
