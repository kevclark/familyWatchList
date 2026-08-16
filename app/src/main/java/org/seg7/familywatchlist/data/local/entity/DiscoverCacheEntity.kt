package org.seg7.familywatchlist.data.local.entity

import androidx.room.Entity

/**
 * Not one of the named tables in PLAN.md §2 — this is the storage PLAN.md §3 implies but
 * doesn't name explicitly ("discover/candidate pages cached 24h per query hash"). It stores
 * which titles a given discover/recommendations query returned, in order; the titles
 * themselves are upserted into [TitleEntity] as normal so display data stays in one place.
 * [queryHash] is a stable hash of the request params (endpoint + all query params).
 */
@Entity(
    tableName = "discover_cache",
    primaryKeys = ["queryHash", "tmdbId", "mediaType"],
)
data class DiscoverCacheEntity(
    val queryHash: String,
    val tmdbId: Int,
    val mediaType: MediaType,
    val ord: Int,
    val fetchedAt: Long,
)
