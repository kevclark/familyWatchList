package org.seg7.familywatchlist.data.recommend

/**
 * PLAN.md §5e (M16): pure paging over the rest of a ranked candidate pool for the "Show 30 more"
 * end card. [rest] is already in rank order and already excludes what the row first showed;
 * [consumed] is how far into it earlier pages have read. Nothing here touches Room or the network,
 * and nothing it returns is ever persisted.
 */
data class ExtraPicksPager(val rest: List<TitleKey>, val consumed: Int = 0) {

    /** What is still pageable once [excluded] (dismissed, or already on screen) is skipped. */
    fun remaining(excluded: Set<TitleKey> = emptySet()): List<TitleKey> =
        rest.drop(consumed).filterNot { it in excluded }

    /** The next page in rank order (up to [pageSize], skipping [excluded]) and the pager advanced past it. */
    fun next(excluded: Set<TitleKey> = emptySet(), pageSize: Int = ExtraPicks.PAGE_SIZE): Pair<List<TitleKey>, ExtraPicksPager> {
        val page = ArrayList<TitleKey>(pageSize)
        var index = consumed
        while (index < rest.size && page.size < pageSize) {
            val key = rest[index]
            index += 1
            if (key !in excluded) page += key
        }
        return page to copy(consumed = index)
    }

    /** Back to the first page, e.g. when a refresh lands and the appended titles are dropped. */
    fun rewound(): ExtraPicksPager = copy(consumed = 0)
}

object ExtraPicks {
    const val PAGE_SIZE: Int = 30
}

/** The "Show 30 more" end card's copy inputs: the range the next tap would add, of [total] (PLAN.md §5e). */
data class MoreCardState(val from: Int, val to: Int, val total: Int, val loading: Boolean) {
    val rangeLabel: String get() = "$from–$to of $total"

    companion object {
        /** Null (no card) when nothing remains to page through. */
        fun of(visibleCount: Int, remaining: Int, loading: Boolean, pageSize: Int = ExtraPicks.PAGE_SIZE): MoreCardState? =
            if (remaining <= 0) null
            else MoreCardState(visibleCount + 1, visibleCount + minOf(pageSize, remaining), visibleCount + remaining, loading)
    }
}

/** PLAN.md §5e part 4: which profile the running refresh is on, for the Home banner. */
data class RefreshProgress(val profileName: String, val index: Int, val total: Int) {
    val bannerText: String get() = "Refreshing $profileName's picks ($index of $total)…"
}
