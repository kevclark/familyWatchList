package org.seg7.familywatchlist.data.recommend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.seg7.familywatchlist.data.local.entity.MediaType

private fun candidate(id: Int, score: Double, quality: Double = score, genre: Int? = null) =
    ScoredCandidate(TitleKey(id, MediaType.MOVIE), score, quality, genre)

/** PLAN.md §4's shortlist assembly: greedy-by-score, diversity cap, wildcard slot(s). */
class ShortlistAssemblerTest {

    @Test
    fun `assembles ~8 with a 2-per-genre cap and 1 wildcard from an unexplored genre, at a small target size`() {
        // Deliberately uses an explicit small targetSize rather than ShortlistConfig.SPEC_DEFAULT
        // (which is production's real 30) — this test's fixture only has 12 candidates, and its
        // whole point is hand-verifying the diversity-cap/wildcard mechanics against a small,
        // fully-worked-out pool. diversityCap and wildcardCount below still match SPEC_DEFAULT's
        // real values (2 and 1) — only targetSize is intentionally decoupled, since asking for 30
        // from a 12-candidate pool would trigger the backfill path this test isn't about.
        val candidates = listOf(
            candidate(1, 10.0, genre = 1),
            candidate(2, 9.0, genre = 1),
            candidate(3, 8.0, genre = 1), // 3rd G1 -> capped out
            candidate(4, 7.0, genre = 2),
            candidate(5, 6.0, genre = 1), // capped out
            candidate(6, 5.0, genre = 2),
            candidate(7, 4.0, genre = 3),
            candidate(8, 3.0, genre = 3),
            candidate(9, 2.0, genre = 4),
            candidate(10, 1.0, genre = 4), // core already full by the time this is reached
            candidate(11, 0.5, quality = 0.9, genre = 5), // unexplored genre, lower quality
            candidate(12, 0.4, quality = 0.95, genre = 5), // unexplored genre, highest quality -> wildcard
        )
        val config = ShortlistConfig(
            wildcardCount = RecommenderSpec.WILDCARD_COUNT,
            diversityCap = RecommenderSpec.DIVERSITY_CAP,
            targetSize = 8,
        )

        val result = ShortlistAssembler.assemble(candidates, config)

        assertEquals(8, result.size)
        val core = result.filterNot { it.isWildcard }.map { it.candidate.title.tmdbId }.toSet()
        assertEquals(setOf(1, 2, 4, 6, 7, 8, 9), core)
        val wildcards = result.filter { it.isWildcard }.map { it.candidate.title.tmdbId }
        assertEquals(listOf(12), wildcards)
    }

    @Test
    fun `backfills past the diversity cap when the pool is too undiverse to hit target size`() {
        val candidates = listOf(candidate(1, 10.0, genre = 1), candidate(2, 9.0, genre = 1), candidate(3, 8.0, genre = 1))
        val config = ShortlistConfig(wildcardCount = 0, diversityCap = 1, targetSize = 3)

        val result = ShortlistAssembler.assemble(candidates, config)

        assertEquals(3, result.size)
        assertTrue(result.none { it.isWildcard })
        assertEquals(setOf(1, 2, 3), result.map { it.candidate.title.tmdbId }.toSet())
    }

    @Test
    fun `wildcard falls back to the next-best remaining candidate when no unexplored genre exists`() {
        // Every candidate shares the same genre, so once the core claims that genre there is
        // nothing left in an "unexplored" genre for the wildcard slots to draw from.
        val candidates = listOf(candidate(1, 10.0, genre = 1), candidate(2, 9.0, genre = 1), candidate(3, 8.0, genre = 1))
        val config = ShortlistConfig(wildcardCount = 2, diversityCap = 5, targetSize = 3)

        val result = ShortlistAssembler.assemble(candidates, config)

        assertEquals(3, result.size)
        val core = result.filterNot { it.isWildcard }.map { it.candidate.title.tmdbId }
        assertEquals(listOf(1), core)
        val wildcards = result.filter { it.isWildcard }.map { it.candidate.title.tmdbId }
        assertEquals(listOf(2, 3), wildcards)
    }

    @Test
    fun `an empty candidate pool assembles to an empty shortlist`() {
        assertEquals(emptyList<Any>(), ShortlistAssembler.assemble(emptyList()))
    }
}
