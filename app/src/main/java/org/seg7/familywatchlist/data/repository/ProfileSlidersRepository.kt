package org.seg7.familywatchlist.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.seg7.familywatchlist.data.local.dao.ProfileSlidersDao
import org.seg7.familywatchlist.data.local.entity.ProfileSlidersEntity
import org.seg7.familywatchlist.data.recommend.RecommenderSpec
import org.seg7.familywatchlist.data.recommend.SliderSettings

/**
 * PLAN.md §4a: per-profile "Tune my picks" slider storage — the three taste sliders plus the
 * unrelated "Suggestion count" control (slider 5). A profile with no row yet reads as
 * [SliderSettings.DEFAULT] (all taste sliders at 0, the fixed spec) and
 * [RecommenderSpec.SHORTLIST_TARGET_SIZE] (30) for both suggestion-count fields below — nothing
 * needs pre-seeding at profile creation, and a profile that's never opened "Tune my picks" (or
 * never had a shortlist refresh) behaves identically to this feature not existing at all.
 *
 * [set], [setSuggestionCount], and [setEligibleCandidateCount] each read-then-write so that
 * updating one of the three independent concerns this table now holds never clobbers the other
 * two back to their defaults — all writes replace the full row (Room's `@Upsert` has no
 * partial-column update), so whichever fields aren't being changed must be carried forward from
 * the current value.
 */
class ProfileSlidersRepository(private val dao: ProfileSlidersDao) {

    fun observe(profileId: Long): Flow<SliderSettings> =
        dao.observe(profileId).map { it?.toSliderSettings() ?: SliderSettings.DEFAULT }

    suspend fun get(profileId: Long): SliderSettings = dao.get(profileId)?.toSliderSettings() ?: SliderSettings.DEFAULT

    suspend fun set(profileId: Long, settings: SliderSettings) {
        val current = dao.get(profileId)
        dao.upsert(
            settings.toEntity(
                profileId = profileId,
                suggestionCount = current?.suggestionCount ?: RecommenderSpec.SHORTLIST_TARGET_SIZE,
                eligibleCandidateCount = current?.eligibleCandidateCount ?: RecommenderSpec.SHORTLIST_TARGET_SIZE,
            )
        )
    }

    /**
     * PLAN.md §4a slider 5: this profile's *requested* personal shortlist size, default
     * [RecommenderSpec.SHORTLIST_TARGET_SIZE]. This is what the user asked for — it is never
     * silently overwritten just because a given week's real pool (see
     * [getEligibleCandidateCount]) happens to be smaller; the *effective* target actually used at
     * refresh time is `min(this, eligibleCandidateCount)`, computed by
     * [org.seg7.familywatchlist.data.repository.RecommendationRepository.refreshProfileShortlist]
     * itself, not stored here.
     */
    fun observeSuggestionCount(profileId: Long): Flow<Int> =
        dao.observe(profileId).map { it?.suggestionCount ?: RecommenderSpec.SHORTLIST_TARGET_SIZE }

    suspend fun getSuggestionCount(profileId: Long): Int =
        dao.get(profileId)?.suggestionCount ?: RecommenderSpec.SHORTLIST_TARGET_SIZE

    /**
     * @throws IllegalArgumentException if [count] isn't a sane positive request. There is
     * deliberately no upper-bound validation here any more (design corrected 2026-08-20, same
     * day, after an initial fixed-50 ceiling was reworked) — the real ceiling is
     * [getEligibleCandidateCount], which drifts over time and is enforced only as the *effective*
     * clamp at refresh time, not as a write-time rejection of the user's stored request.
     */
    suspend fun setSuggestionCount(profileId: Long, count: Int) {
        require(count >= 1) { "suggestion count must be a positive integer, was $count" }
        val current = dao.get(profileId)
        val sliders = current?.toSliderSettings() ?: SliderSettings.DEFAULT
        dao.upsert(
            sliders.toEntity(
                profileId = profileId,
                suggestionCount = count,
                eligibleCandidateCount = current?.eligibleCandidateCount ?: RecommenderSpec.SHORTLIST_TARGET_SIZE,
            )
        )
    }

    /**
     * PLAN.md §4a slider 5: the real, last-known eligible-candidate count for this profile's
     * personal shortlist — persisted by [org.seg7.familywatchlist.data.repository.RecommendationRepository.refreshProfileShortlist]
     * on every refresh (weekly job or slider-triggered recompute). "Tune my picks" reads this to
     * set the suggestion-count slider's max ([org.seg7.familywatchlist.data.recommend.suggestionCountRange])
     * without a live network fetch (offline-first) — a profile that has never had a refresh run
     * (e.g. still cold-start) reads as [RecommenderSpec.SHORTLIST_TARGET_SIZE], a sane placeholder
     * ceiling rather than a misleadingly small real number.
     */
    fun observeEligibleCandidateCount(profileId: Long): Flow<Int> =
        dao.observe(profileId).map { it?.eligibleCandidateCount ?: RecommenderSpec.SHORTLIST_TARGET_SIZE }

    suspend fun getEligibleCandidateCount(profileId: Long): Int =
        dao.get(profileId)?.eligibleCandidateCount ?: RecommenderSpec.SHORTLIST_TARGET_SIZE

    /** @throws IllegalArgumentException if [count] is negative — a genuinely empty pool is 0, never negative. */
    suspend fun setEligibleCandidateCount(profileId: Long, count: Int) {
        require(count >= 0) { "eligible candidate count cannot be negative, was $count" }
        val current = dao.get(profileId)
        val sliders = current?.toSliderSettings() ?: SliderSettings.DEFAULT
        dao.upsert(
            sliders.toEntity(
                profileId = profileId,
                suggestionCount = current?.suggestionCount ?: RecommenderSpec.SHORTLIST_TARGET_SIZE,
                eligibleCandidateCount = count,
            )
        )
    }

    private fun ProfileSlidersEntity.toSliderSettings() =
        SliderSettings(discovery = discovery, recency = recency, personalMatch = personalMatch)

    private fun SliderSettings.toEntity(profileId: Long, suggestionCount: Int, eligibleCandidateCount: Int) = ProfileSlidersEntity(
        profileId = profileId,
        discovery = discovery,
        recency = recency,
        personalMatch = personalMatch,
        suggestionCount = suggestionCount,
        eligibleCandidateCount = eligibleCandidateCount,
    )
}
