package org.seg7.familywatchlist.data.local.entity

/** PLAN.md §2: Title.mediaType. Stored as TEXT in Room via [org.seg7.familywatchlist.data.local.Converters]. */
enum class MediaType { MOVIE, TV }

/** PLAN.md §2: TitleAttribute.attrType — one normalized table serves display + the recommender. */
enum class AttrType { GENRE, CAST, CREW, KEYWORD }

/** PLAN.md §2: Rating.value — thumbs up/neutral/down, latest write wins per person. */
enum class RatingValue { UP, NEUTRAL, DOWN }

/** PLAN.md §2: WatchlistEntry.state. */
enum class WatchlistState { ACTIVE, WATCHED, REMOVED }

/** PLAN.md §2: ProviderAvailability.kind — TMDB watch/providers monetization type. */
enum class ProviderKind { FLATRATE, FREE }

/** PLAN.md §2: ShortlistEntry.state. */
enum class ShortlistState { SUGGESTED, DISMISSED, WATCHED }
