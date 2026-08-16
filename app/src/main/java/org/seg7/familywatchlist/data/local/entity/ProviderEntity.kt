package org.seg7.familywatchlist.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * PLAN.md §2: Provider — seeded from TMDB's GB provider list; [subscribed] is toggled in
 * Settings (M4). Onboarding (M2) defaults a starter set on; that's app-layer logic, not schema.
 */
@Entity(tableName = "providers")
data class ProviderEntity(
    @PrimaryKey val providerId: Int,
    val name: String,
    val logoPath: String?,
    val subscribed: Boolean,
    val displayPriority: Int,
)
