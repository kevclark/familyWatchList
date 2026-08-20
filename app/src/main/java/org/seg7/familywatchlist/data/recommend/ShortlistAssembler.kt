package org.seg7.familywatchlist.data.recommend

/** A scored candidate ready for shortlist assembly. [primaryGenreId] drives the diversity cap; null (no genre data) never counts against it. */
data class ScoredCandidate(
    val title: TitleKey,
    val score: Double,
    val quality: Double,
    val primaryGenreId: Int?,
)

/** One assembled shortlist slot — [isWildcard] distinguishes the "unexplored genre" slot(s) from the greedy-by-score core. */
data class ShortlistSlot(val candidate: ScoredCandidate, val isWildcard: Boolean)

/**
 * PLAN.md §4: "greedy by score with a diversity cap (max 2 per primary genre) + 1 wildcard slot
 * (high-quality title from an unexplored genre)." Sliders 1 (PLAN.md §4a) tune [ShortlistConfig]'s
 * wildcard count and diversity cap; s=0 reproduces the spec's 1-wildcard/cap-2 defaults exactly.
 */
object ShortlistAssembler {

    fun assemble(candidates: List<ScoredCandidate>, config: ShortlistConfig = ShortlistConfig.SPEC_DEFAULT): List<ShortlistSlot> {
        val sorted = candidates.sortedWith(compareByDescending<ScoredCandidate> { it.score }.thenBy { it.title.tmdbId })
        val coreTarget = (config.targetSize - config.wildcardCount).coerceAtLeast(0)

        // Greedy core pass, respecting the per-genre diversity cap.
        val core = mutableListOf<ScoredCandidate>()
        val perGenreCount = HashMap<Int, Int>()
        for (candidate in sorted) {
            if (core.size >= coreTarget) break
            val genre = candidate.primaryGenreId
            if (genre != null) {
                val count = perGenreCount.getOrDefault(genre, 0)
                if (count >= config.diversityCap) continue
                perGenreCount[genre] = count + 1
            }
            core += candidate
        }
        // Backfill: if the diversity cap left the core short of target (a small/undiverse pool),
        // fill remaining slots with the next-best candidates regardless of genre repetition —
        // "~8 titles" is the goal, an under-filled shortlist is a worse failure than a repeat genre.
        if (core.size < coreTarget) {
            val used = core.map { it.title }.toHashSet()
            for (candidate in sorted) {
                if (core.size >= coreTarget) break
                if (candidate.title in used) continue
                core += candidate
                used += candidate.title
            }
        }

        val usedTitles = core.map { it.title }.toHashSet()
        val usedGenres = core.mapNotNull { it.primaryGenreId }.toHashSet()

        // Wildcard: highest-quality candidate(s) from a genre not already represented in the core.
        val unexploredPool = sorted.filter { it.title !in usedTitles && (it.primaryGenreId == null || it.primaryGenreId !in usedGenres) }
            .sortedWith(compareByDescending<ScoredCandidate> { it.quality }.thenBy { it.title.tmdbId })
        val wildcards = unexploredPool.take(config.wildcardCount).toMutableList()

        // If there simply aren't enough unexplored-genre candidates, fall back to the next-best
        // remaining candidates so the wildcard slot(s) aren't silently left empty.
        if (wildcards.size < config.wildcardCount) {
            val chosen = (usedTitles + wildcards.map { it.title }).toHashSet()
            for (candidate in sorted) {
                if (wildcards.size >= config.wildcardCount) break
                if (candidate.title in chosen) continue
                wildcards += candidate
                chosen += candidate.title
            }
        }

        return core.map { ShortlistSlot(it, isWildcard = false) } + wildcards.map { ShortlistSlot(it, isWildcard = true) }
    }
}
