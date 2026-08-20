package org.seg7.familywatchlist.data.recommend

import org.junit.Assert.assertEquals
import org.junit.Test
import org.seg7.familywatchlist.data.local.entity.AttrType
import org.seg7.familywatchlist.data.local.entity.MediaType

private const val EPS = 1e-9

private fun genre(id: Int) = AttrKey(AttrType.GENRE, id)
private fun cast(id: Int) = AttrKey(AttrType.CAST, id)
private fun keyword(id: Int) = AttrKey(AttrType.KEYWORD, id)

/** PLAN.md §4's scoring formula, each term verified against hand-computed fixtures. */
class ScorerTest {

    @Test
    fun `attrTypeWeight matches PLAN md §4 exactly`() {
        assertEquals(1.2, Scorer.attrTypeWeight(AttrType.KEYWORD), EPS)
        assertEquals(1.0, Scorer.attrTypeWeight(AttrType.GENRE), EPS)
        assertEquals(1.0, Scorer.attrTypeWeight(AttrType.CREW), EPS)
        assertEquals(0.8, Scorer.attrTypeWeight(AttrType.CAST), EPS)
    }

    @Test
    fun `affinityMatch averages within a type then weights by type, summing across types`() {
        val profile = mapOf(genre(1) to 0.6, genre(2) to 0.2, cast(10) to 0.9, cast(11) to 0.3, keyword(50) to 0.5)
        val candidateAttrs = listOf(genre(1), genre(2), cast(10), cast(11))

        // GENRE avg = (0.6+0.2)/2 = 0.4, weight 1.0 -> 0.4
        // CAST avg = (0.9+0.3)/2 = 0.6, weight 0.8 -> 0.48
        val expected = 0.4 + 0.48
        assertEquals(expected, Scorer.affinityMatch(profile, candidateAttrs), 1e-9)
    }

    @Test
    fun `affinityMatch scores a candidate attribute the profile has no opinion on as zero`() {
        val profile = mapOf(genre(1) to 0.6)
        val candidateAttrs = listOf(genre(1), keyword(99))
        // GENRE: 0.6 * 1.0 = 0.6 ; KEYWORD: 0 * 1.2 = 0
        assertEquals(0.6, Scorer.affinityMatch(profile, candidateAttrs), 1e-9)
    }

    @Test
    fun `affinityMatch of an empty candidate is zero`() {
        assertEquals(0.0, Scorer.affinityMatch(mapOf(genre(1) to 0.6), emptyList()), EPS)
    }

    @Test
    fun `tmdbQuality requires the 20-vote floor`() {
        assertEquals(0.0, Scorer.tmdbQuality(voteAverage = 9.0, voteCount = 19), EPS)
        assertEquals(0.75, Scorer.tmdbQuality(voteAverage = 7.5, voteCount = 20), EPS)
        assertEquals(0.0, Scorer.tmdbQuality(voteAverage = null, voteCount = 500), EPS)
        assertEquals(0.0, Scorer.tmdbQuality(voteAverage = 8.0, voteCount = null), EPS)
    }

    @Test
    fun `tmdbQuality clamps into the 0 to 1 range`() {
        assertEquals(1.0, Scorer.tmdbQuality(voteAverage = 12.0, voteCount = 100), EPS)
    }

    @Test
    fun `freshness decays with a 3-year half-life and handles a missing release year`() {
        assertEquals(1.0, Scorer.freshness(releaseYear = 2026, todayYear = 2026), EPS)
        assertEquals(0.5, Scorer.freshness(releaseYear = 2023, todayYear = 2026), 1e-6)
        assertEquals(0.0, Scorer.freshness(releaseYear = null, todayYear = 2026), EPS)
    }

    @Test
    fun `freshness never scores a future release above 1_0`() {
        assertEquals(1.0, Scorer.freshness(releaseYear = 2030, todayYear = 2026), EPS)
    }

    @Test
    fun `score combines the three terms at the PLAN md §4 fixed weights`() {
        val profile = mapOf(genre(1) to 0.5)
        val candidate = ScoringCandidate(
            title = TitleKey(1, MediaType.MOVIE),
            attributes = listOf(genre(1)),
            voteAverage = 8.0,
            voteCount = 100,
            releaseYear = 2026,
        )
        // affinity = 0.5*1.0 = 0.5 ; quality = 0.8 ; freshness = 1.0
        // score = 0.70*0.5 + 0.15*0.8 + 0.15*1.0 = 0.35 + 0.12 + 0.15 = 0.62
        assertEquals(0.62, Scorer.score(profile, candidate, todayYear = 2026), 1e-9)
    }

    @Test
    fun `score respects custom weights (slider-adjusted) instead of the spec default`() {
        val profile = mapOf(genre(1) to 0.5)
        val candidate = ScoringCandidate(TitleKey(1, MediaType.MOVIE), listOf(genre(1)), 8.0, 100, 2026)
        val weights = ScoringWeights(affinity = 0.85, quality = 0.05, freshness = 0.15)
        // 0.85*0.5 + 0.05*0.8 + 0.15*1.0 = 0.425 + 0.04 + 0.15 = 0.615
        assertEquals(0.615, Scorer.score(profile, candidate, todayYear = 2026, weights = weights), 1e-9)
    }
}
