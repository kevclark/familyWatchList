package org.seg7.familywatchlist.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.seg7.familywatchlist.data.local.entity.FamilyProfileEntity
import org.seg7.familywatchlist.data.local.entity.FamilyProfileMemberEntity

@Dao
interface FamilyProfileDao {
    @Query("SELECT * FROM family_profile WHERE id = :id")
    fun observe(id: Long = FamilyProfileEntity.SINGLETON_ID): Flow<FamilyProfileEntity?>

    @Query("SELECT * FROM family_profile WHERE id = :id")
    suspend fun get(id: Long = FamilyProfileEntity.SINGLETON_ID): FamilyProfileEntity?

    @Upsert
    suspend fun upsert(entity: FamilyProfileEntity)

    @Query("SELECT memberProfileId FROM family_profile_members")
    fun observeMemberIds(): Flow<List<Long>>

    @Query("SELECT memberProfileId FROM family_profile_members")
    suspend fun getMemberIds(): List<Long>

    @Insert
    suspend fun insertMembers(members: List<FamilyProfileMemberEntity>)

    @Query("DELETE FROM family_profile_members")
    suspend fun deleteAllMembers()

    /**
     * Replaces the entire membership set atomically — [org.seg7.familywatchlist.data.repository
     * .FamilyProfileRepository.save]'s "editable membership" write. A plain delete-then-insert
     * rather than a diff: the membership list is small (at most [org.seg7.familywatchlist.data
     * .repository.ProfileRepository.MAX_PROFILES]) and this is only ever called from an explicit
     * user save action, never a hot path.
     */
    @Transaction
    suspend fun replaceMembers(memberProfileIds: List<Long>) {
        deleteAllMembers()
        insertMembers(memberProfileIds.map { FamilyProfileMemberEntity(it) })
    }
}
