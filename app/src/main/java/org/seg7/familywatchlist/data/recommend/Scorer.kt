package org.seg7.familywatchlist.data.recommend

import kotlin.math.exp
import org.seg7.familywatchlist.data.local.entity.AttrType

/** A candidate title's attributes, quality signal, and release year — everything [Scorer] needs to score it. */
data class ScoringCandidate(
    val title: TitleKey,
    val attributes: List<AttrKey>,
    val voteAverage: Double?,
    val voteCount: Int?,
    val releaseYear: Int?,
)

/**
 * PLAN.md §4's scoring formula: `0.70 x affinityMatch + 0.15 x tmdbQuality + 0.15 x freshness`
 * (weights adjustable per-profile by sliders 1/3 — PLAN.md §4a; s=0 reproduces these exact
 * defaults, proven in [RecommenderSpecReproductionTest]/[ScorerTest]).
 */
object Scorer {

    /** PLAN.md §4: "keyword 1.2, genre 1.0, crew 1.0, cast 0.8". */
    fun attrTypeWeight(type: AttrType): Double = when (type) {
        AttrType.KEYWORD -> 1.2
        AttrType.GENRE -> 1.0
        AttrType.CREW -> 1.0
        AttrType.CAST -> 0.8
    }

    /**
     * PLAN.md §4: "dot product vs candidate attrs, per-type weights ..., normalised by attr
     * count per type." For each attribute type present on the candidate, average the profile's
     * affinity across that type's attributes (so a title with many cast credits doesn't get an
     * unfair boost purely from having more of them), weight by [attrTypeWeight], then sum across
     * types. A candidate attribute the profile's vector has no entry for contributes 0.
     */
    fun affinityMatch(profileVector: Map<AttrKey, Double>, candidateAttributes: List<AttrKey>): Double =
        candidateAttributes
            .groupBy { it.attrType }
            .entries
            .sumOf { (type, attrs) ->
                val average = attrs.sumOf { profileVector[it] ?: 0.0 } / attrs.size
                average * attrTypeWeight(type)
            }

    /**
     * PLAN.md §4: "voteAverage/10, min 20 votes." Below the vote-count floor (or with no vote
     * data at all — e.g. a not-yet-detail-fetched candidate) there isn't enough evidence to
     * credit the title, so quality contributes 0 rather than an unreliable average from a
     * handful of votes.
     */
    fun tmdbQuality(voteAverage: Double?, voteCount: Int?): Double {
        if (voteAverage == null || voteCount == null || voteCount < RecommenderSpec.MIN_VOTES_FOR_QUALITY) return 0.0
        return (voteAverage / 10.0).coerceIn(0.0, 1.0)
    }

    /**
     * PLAN.md §4: "newer release ... boost." Release-year granularity is what [org.seg7.familywatchlist.data.local.entity.TitleEntity]
     * persists (no exact release date), so freshness decays on an exponential year half-life —
     * [FRESHNESS_HALF_LIFE_YEARS] chosen so a brand-new release scores near 1.0 and a title from
     * ~3 years ago has decayed to roughly half.
     *
     * The plan also names a "recently-added-to-provider" boost; that requires tracking *when* a
     * title joined a provider's catalog, which nothing in the M1/M2 data model records today (the
     * closest analogue, [org.seg7.familywatchlist.data.local.entity.ProviderAvailabilityEntity.fetchedAt],
     * is "when we last checked", not "when it actually arrived on the service" — using it would
     * conflate the two). Implemented as release-recency only for M3, documented rather than
     * silently dropped; the provider-arrival half of this signal is the same "leaving/joining
     * soon" data PLAN.md §8 already lists as a stretch item post-M5.
     */
    fun freshness(releaseYear: Int?, todayYear: Int): Double {
        if (releaseYear == null) return 0.0
        val ageYears = (todayYear - releaseYear).coerceAtLeast(0)
        return exp(-LN2 * ageYears / FRESHNESS_HALF_LIFE_YEARS).coerceIn(0.0, 1.0)
    }

    /** PLAN.md §4's full formula for one candidate against one profile's affinity vector. */
    fun score(
        profileVector: Map<AttrKey, Double>,
        candidate: ScoringCandidate,
        todayYear: Int,
        weights: ScoringWeights = ScoringWeights.SPEC_DEFAULT,
    ): Double {
        val affinity = affinityMatch(profileVector, candidate.attributes)
        val quality = tmdbQuality(candidate.voteAverage, candidate.voteCount)
        val fresh = freshness(candidate.releaseYear, todayYear)
        return weights.affinity * affinity + weights.quality * quality + weights.freshness * fresh
    }

    private const val LN2 = 0.6931471805599453
    private const val FRESHNESS_HALF_LIFE_YEARS = 3.0
}
