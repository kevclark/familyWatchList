package org.seg7.familywatchlist.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * PLAN.md §2: Profile. Hard cap of 10 profiles — enforced in the repository layer, not here
 * (Room has no portable "row count <= N" constraint).
 */
@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val avatarKey: String,
    /** UK certification cap, e.g. "12". Null = no cap. */
    val ageRatingCap: String?,
    val createdAt: Long,
)
