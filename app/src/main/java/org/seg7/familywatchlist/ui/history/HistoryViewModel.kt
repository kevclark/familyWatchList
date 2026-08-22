package org.seg7.familywatchlist.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.seg7.familywatchlist.data.local.dao.WatchEventItem
import org.seg7.familywatchlist.data.local.entity.FAMILY_PROFILE_SENTINEL_ID
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.ProfileEntity
import org.seg7.familywatchlist.data.local.entity.RatingValue
import org.seg7.familywatchlist.data.repository.FamilyProfileRepository
import org.seg7.familywatchlist.data.repository.ProfileRepository
import org.seg7.familywatchlist.data.repository.RatingRepository
import org.seg7.familywatchlist.data.repository.WatchEventRepository

/**
 * One history row: the event, its title, who was tagged on it, and (PLAN.md §5b M3i item 2)
 * each tagged profile's *current* rating of the title, if any — keyed by profileId, using the
 * exact same [RatingValue] the log-watch sheet's `RatingDot` already renders (no algorithm/schema
 * change, purely a read of the existing per-(profile, title) rating). A rating is of the title,
 * not of this specific watch (see [RatingEntity][org.seg7.familywatchlist.data.local.entity
 * .RatingEntity]'s kdoc), so re-watching and re-rating the same title updates every one of its
 * history rows at once — that's correct, not a bug: it's still "how does this profile currently
 * feel about this title."
 */
data class HistoryRow(
    val event: WatchEventItem,
    val watchedBy: List<ProfileEntity>,
    val ratings: Map<Long, RatingValue> = emptyMap(),
)

data class HistoryUiState(
    val rows: List<HistoryRow> = emptyList(),
    val profiles: List<ProfileEntity> = emptyList(),
    /** null = "Everyone" (PLAN.md §5 screen 7's "filter by profile"). */
    val filterProfileId: Long? = null,
    val isEmpty: Boolean = false,
)

/**
 * PLAN.md §5 screen 7: reverse-chronological watch history, filterable by profile, with
 * edit/delete on each event.
 *
 * Ordering comes from the DAO (`ORDER BY watchedAt DESC, id DESC`) rather than being re-sorted
 * here — the id tiebreak matters, because several titles logged on the same date must still come
 * back newest-logged-first rather than in arbitrary order.
 *
 * Filtering is applied in memory over the tag map. The rows already need that map to render
 * "who watched" chips, so filtering it costs nothing extra and — unlike a parameterised query —
 * doesn't re-hit the database every time the chip row changes.
 *
 * PLAN.md §4b (M3j): the Family profile is now a selectable filter option too, alongside every
 * real profile — it can genuinely own logged events as of this milestone (dual-tagged onto every
 * watch logged while Family is active, see `LogWatchSheet`'s auto-tag). [FamilyProfileRepository]
 * is consulted only to build a synthetic [ProfileEntity] (id = [FAMILY_PROFILE_SENTINEL_ID]) to
 * append to the chip row — `family_profile` isn't a real row in the `profiles` table, so there's
 * nothing [ProfileRepository.observeAll] would ever surface for it on its own. Deliberately not
 * folded into `watchedBy`'s per-row avatar chips below: those already show every real curated
 * member (dual-tagging keeps that convenience), so a redundant "Family" avatar there would just
 * be clutter, not new information — this option only changes what the *filter* can select.
 */
class HistoryViewModel(
    private val watchEventRepository: WatchEventRepository,
    profileRepository: ProfileRepository,
    ratingRepository: RatingRepository,
    familyProfileRepository: FamilyProfileRepository,
) : ViewModel() {

    private val _filterProfileId = MutableStateFlow<Long?>(null)

    private val familyFilterOption = familyProfileRepository.observe().map { family ->
        family?.let {
            ProfileEntity(
                id = FAMILY_PROFILE_SENTINEL_ID,
                name = it.profile.name,
                avatarKey = it.profile.avatarKey,
                ageRatingCap = null,
                createdAt = it.profile.createdAt,
            )
        }
    }

    val uiState: StateFlow<HistoryUiState> = combine(
        watchEventRepository.observeAllItems(),
        watchEventRepository.observeTagsByEvent(),
        profileRepository.observeAll(),
        ratingRepository.observeAll(),
        combine(_filterProfileId, familyFilterOption) { filterProfileId, family -> filterProfileId to family },
    ) { events, tagsByEvent, realProfiles, ratings, (filterProfileId, family) ->
        // PLAN.md §4b (M3j): the filter chip row offers Family alongside every real profile —
        // watchedBy resolution below deliberately keeps using realProfiles-only, never this
        // extended list, so Family's own dual-tag never adds a redundant "Family" avatar next to
        // every real member's on a row (see this class's kdoc).
        val filterableProfiles = if (family != null) realProfiles + family else realProfiles
        val profilesById = realProfiles.associateBy { it.id }
        // PLAN.md §5b M3i item 2: every rating in the DB, regrouped by the (tmdbId, mediaType)
        // key each history row already carries via its event — so "this row's title" resolves
        // straight to "who rated it what" without a per-row DB round trip.
        val ratingsByTitle: Map<Pair<Int, MediaType>, Map<Long, RatingValue>> =
            ratings.groupBy { it.tmdbId to it.mediaType }
                .mapValues { (_, rows) -> rows.associate { it.profileId to it.value } }
        val rows = events
            .filter { event ->
                filterProfileId == null || filterProfileId in tagsByEvent[event.id].orEmpty()
            }
            .map { event ->
                HistoryRow(
                    event = event,
                    watchedBy = tagsByEvent[event.id].orEmpty().mapNotNull { profilesById[it] },
                    ratings = ratingsByTitle[event.tmdbId to event.mediaType].orEmpty(),
                )
            }
        HistoryUiState(
            rows = rows,
            profiles = filterableProfiles,
            filterProfileId = filterProfileId,
            // "Nothing logged yet" and "nothing matches this filter" are different messages,
            // so emptiness is reported against the *unfiltered* list.
            isEmpty = events.isEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun setFilter(profileId: Long?) {
        _filterProfileId.value = profileId
    }

    fun deleteEvent(eventId: Long) {
        viewModelScope.launch { watchEventRepository.deleteWatch(eventId) }
    }
}
