package org.seg7.familywatchlist.data.recommend

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.RatingValue

private fun genre(id: Int) = AttrKey(org.seg7.familywatchlist.data.local.entity.AttrType.GENRE, id)
private fun keyword(id: Int) = AttrKey(org.seg7.familywatchlist.data.local.entity.AttrType.KEYWORD, id)
private fun cast(id: Int) = AttrKey(org.seg7.familywatchlist.data.local.entity.AttrType.CAST, id)

/**
 * PLAN.md §4a's explicit, non-optional acceptance bar: "fixture tests must prove s=0 on all
 * sliders reproduces bit-for-bit the same shortlist a build with no sliders at all would have
 * produced." This runs the *entire* pipeline (affinity vector -> scoring -> shortlist assembly,
 * and separately the family blend) twice against the same fixture data — once wired directly to
 * [RecommenderSpec]'s fixed constants (as if the slider feature didn't exist), once wired through
 * [SliderSettings.DEFAULT] / [FamilyBlendSlider.DEFAULT] (all four sliders at s=0) — and asserts
 * the outputs are identical down to the double.
 */
class SliderAcceptanceBarTest {

    private val today = LocalDate.of(2026, 8, 20)

    private val watches = listOf(
        RatedWatch(TitleKey(1, MediaType.MOVIE), listOf(genre(35), genre(28), cast(1)), today.minusDays(10), RatingValue.UP),
        RatedWatch(TitleKey(2, MediaType.MOVIE), listOf(genre(35), keyword(9)), today.minusDays(200), RatingValue.DOWN),
        RatedWatch(TitleKey(3, MediaType.TV), listOf(genre(18), cast(2)), today.minusDays(30), null),
        RatedWatch(TitleKey(4, MediaType.MOVIE), listOf(genre(28), keyword(9), cast(1)), today.minusDays(400), RatingValue.UP),
        RatedWatch(TitleKey(5, MediaType.TV), listOf(genre(18)), today.minusDays(5), RatingValue.UP),
    )
    private val watchlist = listOf(
        WatchlistSignal(TitleKey(6, MediaType.MOVIE), listOf(genre(35), keyword(12)), today.minusDays(2)),
    )

    private val candidates = listOf(
        ScoringCandidate(TitleKey(101, MediaType.MOVIE), listOf(genre(35), genre(28), cast(1)), 8.1, 500, 2026),
        ScoringCandidate(TitleKey(102, MediaType.MOVIE), listOf(genre(18), keyword(9)), 7.4, 300, 2024),
        ScoringCandidate(TitleKey(103, MediaType.TV), listOf(genre(28), cast(2)), 6.9, 40, 2020),
        ScoringCandidate(TitleKey(104, MediaType.MOVIE), listOf(genre(35)), 9.0, 15, 2026), // below the 20-vote floor
        ScoringCandidate(TitleKey(105, MediaType.TV), listOf(genre(99), keyword(12)), 8.8, 900, 2025), // unexplored genre
        ScoringCandidate(TitleKey(106, MediaType.MOVIE), listOf(genre(35), genre(18)), 7.0, 60, 2023),
        ScoringCandidate(TitleKey(107, MediaType.MOVIE), listOf(cast(1), keyword(9)), 6.5, 25, 2022),
        ScoringCandidate(TitleKey(108, MediaType.TV), listOf(genre(28), genre(35)), 8.3, 200, 2019),
        ScoringCandidate(TitleKey(109, MediaType.MOVIE), listOf(genre(18), cast(2)), 7.9, 80, 2021),
        ScoringCandidate(TitleKey(110, MediaType.TV), listOf(genre(99)), 9.5, 1000, 2025), // unexplored genre, highest quality
    )
    private val primaryGenreByTitle: Map<TitleKey, Int?> = candidates.associate { it.title to it.attributes.firstOrNull { a -> a.attrType == org.seg7.familywatchlist.data.local.entity.AttrType.GENRE }?.attrId }

    @Test
    fun `SliderSettings DEFAULT reproduces the no-slider shortlist bit-for-bit`() {
        val noSliderShortlist = runPipeline(
            halfLifeDays = RecommenderSpec.HALF_LIFE_DAYS,
            weights = ScoringWeights.SPEC_DEFAULT,
            config = ShortlistConfig.SPEC_DEFAULT,
        )
        val defaultSliderShortlist = runPipeline(
            halfLifeDays = SliderSettings.DEFAULT.halfLifeDays,
            weights = SliderSettings.DEFAULT.toScoringWeights(),
            config = SliderSettings.DEFAULT.toShortlistConfig(),
        )

        assertEquals(noSliderShortlist, defaultSliderShortlist)
        // Belt-and-braces: also confirm the derived config values themselves are identical, not
        // just that they happen to produce the same shortlist by coincidence.
        assertEquals(RecommenderSpec.HALF_LIFE_DAYS, SliderSettings.DEFAULT.halfLifeDays, 0.0)
        assertEquals(ScoringWeights.SPEC_DEFAULT, SliderSettings.DEFAULT.toScoringWeights())
        assertEquals(ShortlistConfig.SPEC_DEFAULT, SliderSettings.DEFAULT.toShortlistConfig())
    }

    @Test
    fun `FamilyBlendSlider DEFAULT reproduces the no-slider family blend bit-for-bit`() {
        val vectorA = AffinityEngine.buildAffinityVector(watches, watchlist, today)
        val vectorB = AffinityEngine.buildAffinityVector(
            listOf(watches[2], watches[4]), // a second, differently-tasted profile
            emptyList(),
            today,
        )

        val noSliderBlend = FamilyBlend.blendVectors(listOf(vectorA, vectorB), FamilyBlendWeights.SPEC_DEFAULT)
        val defaultSliderBlend = FamilyBlend.blendVectors(listOf(vectorA, vectorB), FamilyBlendSlider.DEFAULT.toFamilyBlendWeights())

        assertEquals(noSliderBlend, defaultSliderBlend)
        assertEquals(FamilyBlendWeights.SPEC_DEFAULT, FamilyBlendSlider.DEFAULT.toFamilyBlendWeights())
    }

    private fun runPipeline(halfLifeDays: Double, weights: ScoringWeights, config: ShortlistConfig): List<ShortlistSlot> {
        val vector = AffinityEngine.buildAffinityVector(watches, watchlist, today, halfLifeDays)
        val scored = candidates.map { candidate ->
            ScoredCandidate(
                title = candidate.title,
                score = Scorer.score(vector, candidate, todayYear = today.year, weights = weights),
                quality = Scorer.tmdbQuality(candidate.voteAverage, candidate.voteCount),
                primaryGenreId = primaryGenreByTitle[candidate.title],
            )
        }
        return ShortlistAssembler.assemble(scored, config)
    }
}
