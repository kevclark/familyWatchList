package org.seg7.familywatchlist.data.local.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * PLAN.md §2/§3: ProviderAvailability — GB region only. [fetchedAt] drives the 7-day TTL.
 */
@Entity(
    tableName = "provider_availability",
    primaryKeys = ["tmdbId", "mediaType", "providerId"],
    indices = [Index(value = ["tmdbId", "mediaType"])],
)
data class ProviderAvailabilityEntity(
    val tmdbId: Int,
    val mediaType: MediaType,
    val providerId: Int,
    val kind: ProviderKind,
    val fetchedAt: Long,
)
