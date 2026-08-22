package org.seg7.familywatchlist.data.local.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * PLAN.md §2/§3: ProviderAvailability — GB region only. [fetchedAt] drives the 7-day TTL.
 *
 * M6 (PLAN.md §5 "Paid (rent/buy) titles" addendum, DB v7 -> v8, [org.seg7.familywatchlist.data
 * .local.AppDatabase.MIGRATION_7_8]): [kind] joined the primary key. The same provider can
 * legitimately offer a title both included with a subscription (FLATRATE) *and* separately as a
 * rental/purchase (RENT/BUY) — Amazon Video routinely does — and the old `(tmdbId, mediaType,
 * providerId)` key couldn't represent both rows for the same provider: whichever
 * [org.seg7.familywatchlist.data.repository.TmdbMappers.toAvailability] happened to build last
 * silently clobbered the other one via `Upsert`. Adding [kind] to the key is what makes it
 * possible to persist "FLATRATE here, RENT there" as two distinct rows for one provider.
 */
@Entity(
    tableName = "provider_availability",
    primaryKeys = ["tmdbId", "mediaType", "providerId", "kind"],
    indices = [Index(value = ["tmdbId", "mediaType"])],
)
data class ProviderAvailabilityEntity(
    val tmdbId: Int,
    val mediaType: MediaType,
    val providerId: Int,
    val kind: ProviderKind,
    val fetchedAt: Long,
)
