package org.seg7.familywatchlist.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.seg7.familywatchlist.common.AppClock
import org.seg7.familywatchlist.data.local.dao.FamilyProfileDao
import org.seg7.familywatchlist.data.local.dao.ProfileDao
import org.seg7.familywatchlist.data.local.entity.FamilyProfileEntity
import org.seg7.familywatchlist.data.local.entity.ProfileEntity

/**
 * PLAN.md §4/§2: CRUD for the singleton [FamilyProfileEntity] and its curated membership.
 * "2+ members required to create one" is enforced here (mirrors [ProfileRepository]'s 10-profile
 * cap being enforced in the repository, not the schema — Room has no portable row-count/set-size
 * constraint), and [save] doubles as both create *and* edit: PLAN.md's brief is explicit that
 * membership stays "editable later," not create-only, and a singleton row has no meaningful
 * distinction between "the first save" and "a later edit" beyond whether a row already exists.
 */
class FamilyProfileRepository(
    private val familyProfileDao: FamilyProfileDao,
    private val profileDao: ProfileDao,
    private val clock: AppClock,
) {
    /**
     * Null when no Family profile has been created yet. [FamilyProfileWithMembers.members] is
     * resolved against the live profile list, so a member profile's own name/avatar edits (or
     * deletion — see [FamilyProfileMemberEntity]'s `CASCADE` kdoc) show up here automatically.
     */
    fun observe(): Flow<FamilyProfileWithMembers?> =
        combine(familyProfileDao.observe(), familyProfileDao.observeMemberIds(), profileDao.observeAll()) { family, memberIds, allProfiles ->
            family?.let {
                FamilyProfileWithMembers(it, allProfiles.filter { profile -> profile.id in memberIds })
            }
        }

    suspend fun get(): FamilyProfileWithMembers? {
        val family = familyProfileDao.get() ?: return null
        val memberIds = familyProfileDao.getMemberIds().toSet()
        val allProfiles = profileDao.observeAll()
        // A one-shot read has no live Flow to pull from for the profile list; DAO exposes only
        // observeAll for profiles, so resolve via getById per member instead — membership is
        // always small (see replaceMembers' kdoc), so N lookups here is not a concern.
        val members = memberIds.mapNotNull { profileDao.getById(it) }
        return FamilyProfileWithMembers(family, members)
    }

    suspend fun exists(): Boolean = familyProfileDao.get() != null

    /**
     * Creates or updates the singleton Family profile and fully replaces its membership set.
     * [memberProfileIds] must resolve to at least [MIN_MEMBERS] *distinct* ids or this fails
     * without writing anything — PLAN.md §2: "Minimum 2 members to create one — a 'family' of
     * one person isn't a blend." This floor is enforced on every save (not just the first),
     * matching PLAN.md §4's explicit call that membership stays editable, not create-only.
     */
    suspend fun save(name: String, avatarKey: String, memberProfileIds: List<Long>): Result<Unit> {
        val distinctMembers = memberProfileIds.distinct()
        if (distinctMembers.size < MIN_MEMBERS) {
            return Result.failure(FamilyProfileMembersException())
        }
        val trimmedName = name.trim().ifBlank { DEFAULT_NAME }
        val existing = familyProfileDao.get()
        familyProfileDao.upsert(
            FamilyProfileEntity(
                name = trimmedName,
                avatarKey = avatarKey,
                createdAt = existing?.createdAt ?: clock.nowMillis(),
            )
        )
        familyProfileDao.replaceMembers(distinctMembers)
        return Result.success(Unit)
    }

    companion object {
        const val MIN_MEMBERS = 2
        const val DEFAULT_NAME = "Family"
    }
}

/**
 * @property members resolved [ProfileEntity] rows for the current membership, in [ProfileEntity]
 * insertion order (i.e. [ProfileDao.observeAll]'s `createdAt ASC` — a stable, predictable order
 * for UI rendering rather than arbitrary join order).
 */
data class FamilyProfileWithMembers(
    val profile: FamilyProfileEntity,
    val members: List<ProfileEntity>,
) {
    val memberIds: List<Long> get() = members.map { it.id }

    /** PLAN.md §4's under-2-members edge case (Kev's build-agent judgment call, documented in the M3d report): whether this Family profile is currently a valid *active* selection. */
    val hasEnoughMembers: Boolean get() = members.size >= FamilyProfileRepository.MIN_MEMBERS
}

class FamilyProfileMembersException :
    IllegalArgumentException("A family profile needs at least ${FamilyProfileRepository.MIN_MEMBERS} distinct members")
