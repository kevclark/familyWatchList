package org.seg7.familywatchlist.data.local.dao

import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.ProviderKind
import java.time.LocalDate

/**
 * Flat read-models for the M2b screens. These are join results, not entities — nothing here
 * touches the schema (PLAN.md §2 stays as-is), they just spare each ViewModel from hand-joining
 * three Flows to render one poster with a name under it.
 *
 * Deliberately flat rather than Room `@Relation` graphs: every one of these feeds a `LazyRow`
 * or `LazyColumn` item that needs exactly these columns, and flat projections keep the
 * generated queries to a single round-trip.
 */

/** A watchlist entry joined to its cached title — PLAN.md §5's "My List" row and screen. */
data class WatchlistItem(
    val tmdbId: Int,
    val mediaType: MediaType,
    val title: String?,
    val posterPath: String?,
    val year: Int?,
    val addedByProfileId: Long,
    val addedAt: Long,
)

/** A watch event joined to its cached title — PLAN.md §5 screen 7 (History). */
data class WatchEventItem(
    val id: Long,
    val tmdbId: Int,
    val mediaType: MediaType,
    val watchedAt: LocalDate,
    val note: String?,
    val title: String?,
    val posterPath: String?,
    val year: Int?,
)

/**
 * Availability for one title joined to the provider catalog so badges can show real service
 * names, not numeric TMDB ids. Rendered with the "Streaming data by JustWatch" credit
 * (PLAN.md §3 attribution) wherever it appears.
 */
data class AvailabilityBadge(
    val providerId: Int,
    val name: String,
    val logoPath: String?,
    val kind: ProviderKind,
    val subscribed: Boolean,
)
