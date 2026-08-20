package org.seg7.familywatchlist.data.recommend

import org.seg7.familywatchlist.data.local.entity.AttrType
import org.seg7.familywatchlist.data.local.entity.MediaType

/**
 * PLAN.md §4: one dimension of an affinity vector — a single genre/keyword/cast/crew id. TMDB's
 * ids are only unique *within* a namespace (a genre id and a cast id can collide numerically), so
 * [attrType] is part of the key, not just [attrId].
 */
data class AttrKey(val attrType: AttrType, val attrId: Int)

/** Identifies one title (movie or TV series) — used as the affinity engine's document identity for dedup/exclusion. */
data class TitleKey(val tmdbId: Int, val mediaType: MediaType)
