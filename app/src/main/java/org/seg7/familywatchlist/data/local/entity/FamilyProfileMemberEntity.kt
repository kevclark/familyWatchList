package org.seg7.familywatchlist.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * PLAN.md §2: FamilyProfileMember — the join table for [FamilyProfileEntity]'s curated
 * membership. No `familyProfileId` column: [FamilyProfileEntity] is a singleton (see its kdoc),
 * so every row here is, unambiguously, a member of *the* Family profile — a second FK column
 * pointing at a row that can only ever be [FamilyProfileEntity.SINGLETON_ID] would add nothing.
 *
 * `onDelete = CASCADE` is this codebase's first `@ForeignKey` — deliberately, per PLAN.md §2's
 * "cascades on member deletion (removes them from the family, doesn't delete the family profile
 * itself)": Room enables SQLite's `PRAGMA foreign_keys=ON` automatically the moment any entity
 * declares a `@ForeignKey`, so deleting a [ProfileEntity] row now cleans up its family membership
 * for free at the database layer — no repository-level "also delete their membership row" call to
 * remember (and no chance of forgetting it, unlike [WatchEventProfileEntity]'s deliberately
 * FK-less tags, which the repository layer *does* have to clean up by hand — see
 * [org.seg7.familywatchlist.data.repository.WatchEventRepository.deleteWatch]'s kdoc for why that
 * one was left without one).
 */
@Entity(
    tableName = "family_profile_members",
    primaryKeys = ["memberProfileId"],
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["memberProfileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["memberProfileId"])],
)
data class FamilyProfileMemberEntity(
    val memberProfileId: Long,
)
