package org.seg7.familywatchlist.data.repository

import kotlinx.coroutines.flow.Flow
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

    suspend fun get(profileId: Long, tmdbId: Int, mediaType: MediaType): RatingEntity? =
        ratingDao.get(profileId, tmdbId, mediaType)

    suspend fun rate(profileId: Long, tmdbId: Int, mediaType: MediaType, value: RatingValue) {
        ratingDao.upsert(RatingEntity(profileId, tmdbId, mediaType, value, clock.nowMillis()))
    }
}
