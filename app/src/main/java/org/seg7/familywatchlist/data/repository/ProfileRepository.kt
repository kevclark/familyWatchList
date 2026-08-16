package org.seg7.familywatchlist.data.repository

import kotlinx.coroutines.flow.Flow
import org.seg7.familywatchlist.common.AppClock
import org.seg7.familywatchlist.data.local.dao.ProfileDao
import org.seg7.familywatchlist.data.local.entity.ProfileEntity

/** PLAN.md §2: "Hard cap: 10 profiles (enforced in repository)." */
class ProfileRepository(
    private val profileDao: ProfileDao,
    private val clock: AppClock,
) {
    fun observeAll(): Flow<List<ProfileEntity>> = profileDao.observeAll()

    suspend fun getById(id: Long): ProfileEntity? = profileDao.getById(id)

    suspend fun addProfile(name: String, avatarKey: String, ageRatingCap: String?): Result<Long> {
        if (profileDao.count() >= MAX_PROFILES) {
            return Result.failure(MaxProfilesReachedException())
        }
        val id = profileDao.insert(
            ProfileEntity(name = name, avatarKey = avatarKey, ageRatingCap = ageRatingCap, createdAt = clock.nowMillis())
        )
        return Result.success(id)
    }

    suspend fun update(profile: ProfileEntity) = profileDao.update(profile)

    suspend fun delete(profile: ProfileEntity) = profileDao.delete(profile)

    companion object {
        const val MAX_PROFILES = 10
    }
}

class MaxProfilesReachedException :
    IllegalStateException("Cannot add another profile — the ${ProfileRepository.MAX_PROFILES}-profile cap is already reached")
