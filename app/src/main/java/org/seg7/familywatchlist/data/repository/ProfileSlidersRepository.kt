package org.seg7.familywatchlist.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.seg7.familywatchlist.data.local.dao.ProfileSlidersDao
import org.seg7.familywatchlist.data.local.entity.ProfileSlidersEntity
import org.seg7.familywatchlist.data.recommend.SliderSettings

/**
 * PLAN.md §4a: per-profile "Tune my picks" slider storage. A profile with no row yet reads as
 * [SliderSettings.DEFAULT] (all sliders at 0, the fixed spec) — nothing needs pre-seeding at
 * profile creation, and a profile that's never opened "Tune my picks" behaves identically to
 * this feature not existing at all.
 */
class ProfileSlidersRepository(private val dao: ProfileSlidersDao) {

    fun observe(profileId: Long): Flow<SliderSettings> =
        dao.observe(profileId).map { it?.toSliderSettings() ?: SliderSettings.DEFAULT }

    suspend fun get(profileId: Long): SliderSettings = dao.get(profileId)?.toSliderSettings() ?: SliderSettings.DEFAULT

    suspend fun set(profileId: Long, settings: SliderSettings) {
        dao.upsert(
            ProfileSlidersEntity(
                profileId = profileId,
                discovery = settings.discovery,
                recency = settings.recency,
                personalMatch = settings.personalMatch,
            )
        )
    }

    private fun ProfileSlidersEntity.toSliderSettings() =
        SliderSettings(discovery = discovery, recency = recency, personalMatch = personalMatch)
}
