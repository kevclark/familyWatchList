package org.seg7.familywatchlist.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.seg7.familywatchlist.common.AppClock
import org.seg7.familywatchlist.data.local.dao.RatingDao
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.RatingEntity
import org.seg7.familywatchlist.data.local.entity.RatingValue

/** PLAN.md §2: thumbs up/neutral/down, per (profile, title) — latest write wins. */
class RatingRepository(
    private val ratingDao: RatingDao,
    private val clock: AppClock,
) {
    fun observeForProfile(profileId: Long): Flow<List<RatingEntity>> = ratingDao.observeForProfile(profileId)

    /** PLAN.md §5b M3i item 2: every rating in the database — History resolves each row's tagged profiles' thumbs off this rather than one `observeForTitle` per row. */
    fun observeAll(): Flow<List<RatingEntity>> = ratingDao.observeAll()

    suspend fun get(profileId: Long, tmdbId: Int, mediaType: MediaType): RatingEntity? =
        ratingDao.get(profileId, tmdbId, mediaType)

    /** Every profile's thumbs on one title — details screen, log-watch sheet, history editing. */
    fun observeForTitle(tmdbId: Int, mediaType: MediaType): Flow<Map<Long, RatingValue>> =
        ratingDao.observeForTitle(tmdbId, mediaType).map { rows -> rows.associate { it.profileId to it.value } }

    suspend fun getForTitle(tmdbId: Int, mediaType: MediaType): Map<Long, RatingValue> =
        ratingDao.getForTitle(tmdbId, mediaType).associate { it.profileId to it.value }

    suspend fun rate(profileId: Long, tmdbId: Int, mediaType: MediaType, value: RatingValue) {
        ratingDao.upsert(RatingEntity(profileId, tmdbId, mediaType, value, clock.nowMillis()))
    }

    /**
     * Tapping the already-selected thumb clears it. "No opinion recorded" and NEUTRAL are
     * genuinely different to PLAN.md §4's scorer — unrated and NEUTRAL happen to share a
     * ratingWeight of +0.4 today, but only rows that exist count as evidence the profile has
     * actually seen and judged the title.
     */
    suspend fun clear(profileId: Long, tmdbId: Int, mediaType: MediaType) =
        ratingDao.delete(profileId, tmdbId, mediaType)

    /** One-call thumb toggle for the UI: same value again clears, a different value replaces. */
    suspend fun setOrToggle(profileId: Long, tmdbId: Int, mediaType: MediaType, value: RatingValue) {
        if (ratingDao.get(profileId, tmdbId, mediaType)?.value == value) {
            clear(profileId, tmdbId, mediaType)
        } else {
            rate(profileId, tmdbId, mediaType, value)
        }
    }
}
