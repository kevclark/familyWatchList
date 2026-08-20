package org.seg7.familywatchlist.data.recommend

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * PLAN.md §4a: the three per-profile "Tune my picks" sliders, each `s ∈ [-1, +1]`, default 0
 * exactly reproducing [RecommenderSpec]'s fixed values. The fourth slider (family mean/min
 * blend) is [FamilyBlendSlider] — it isn't per-profile, see that class's kdoc.
 *
 * Every derived property is a pure function of `s`; the acceptance bar in
 * `SliderAcceptanceBarTest` proves [DEFAULT] (all sliders at 0) derives *exactly* the same
 * [ScoringWeights]/[ShortlistConfig]/half-life the no-slider spec constants give directly.
 */
data class SliderSettings(
    /** "Sticks to my taste" (-1) <-> "Surprise me" (+1). Shortlist-assembly stage only. */
    val discovery: Double = 0.0,
    /** "Recent favourites" (+1) <-> "All-time favourites" (-1). Recency half-life. */
    val recency: Double = 0.0,
    /** "Personal match" (-1) <-> "Popular & well-reviewed" (+1). affinity/quality weight split. */
    val personalMatch: Double = 0.0,
) {
    init {
        require(discovery in -1.0..1.0) { "discovery slider must be in [-1, 1], was $discovery" }
        require(recency in -1.0..1.0) { "recency slider must be in [-1, 1], was $recency" }
        require(personalMatch in -1.0..1.0) { "personalMatch slider must be in [-1, 1], was $personalMatch" }
    }

    /** PLAN.md §4a slider 1: `round(1 + s)` -> 0 at s=-1, 1 at s=0 (spec default), 2 at s=+1. */
    val wildcardCount: Int
        get() = (1.0 + discovery).roundToInt()

    /** PLAN.md §4a slider 1: `round(2 - s)` -> 3 at s=-1, 2 at s=0 (spec default), 1 at s=+1. */
    val diversityCap: Int
        get() = (2.0 - discovery).roundToInt()

    /** PLAN.md §4a slider 2: `180 x 4^(-s)` -> exactly 180 at s=0, ~45 days at s=+1, ~720 at s=-1. */
    val halfLifeDays: Double
        get() = RecommenderSpec.HALF_LIFE_DAYS * 4.0.pow(-recency)

    /**
     * PLAN.md §4a slider 3: piecewise-linear through the three anchor points the plan calls out
     * — (s=-1 -> 0.85), (s=0 -> 0.70, the exact spec default), (s=+1 -> 0.40) — monotonic and
     * continuous, with both weights staying positive across the whole range as the plan requires.
     */
    val affinityWeight: Double
        get() = if (personalMatch <= 0.0) {
            RecommenderSpec.AFFINITY_WEIGHT - 0.15 * personalMatch
        } else {
            RecommenderSpec.AFFINITY_WEIGHT - 0.30 * personalMatch
        }

    /** PLAN.md §4a slider 3: piecewise-linear through (s=-1 -> 0.05), (s=0 -> 0.15, spec default), (s=+1 -> 0.35). */
    val qualityWeight: Double
        get() = if (personalMatch <= 0.0) {
            RecommenderSpec.QUALITY_WEIGHT + 0.10 * personalMatch
        } else {
            RecommenderSpec.QUALITY_WEIGHT + 0.20 * personalMatch
        }

    fun toScoringWeights(): ScoringWeights =
        ScoringWeights(affinity = affinityWeight, quality = qualityWeight, freshness = RecommenderSpec.FRESHNESS_WEIGHT)

    fun toShortlistConfig(targetSize: Int = RecommenderSpec.SHORTLIST_TARGET_SIZE): ShortlistConfig =
        ShortlistConfig(wildcardCount = wildcardCount, diversityCap = diversityCap, targetSize = targetSize)

    companion object {
        /** All three sliders at 0 — must derive identically to [RecommenderSpec]'s constants (proven in tests). */
        val DEFAULT = SliderSettings()
    }
}

/**
 * PLAN.md §4a slider 4: "Everyone's happy" (-1) <-> "Average taste wins" (+1) — the family-scope
 * mean/min blend. Deliberately **not** a field on [SliderSettings]: this is a property of a
 * *family-scope shortlist request*, not any one profile's own taste settings (PLAN.md §4a: "no
 * natural home on a single profile's own slider set"). See `UserPreferencesRepository
 * .familyBlendSlider` for where this app stores it and the build report for the reasoning.
 */
data class FamilyBlendSlider(val value: Double = 0.0) {
    init {
        require(value in -1.0..1.0) { "family blend slider must be in [-1, 1], was $value" }
    }

    /** `0.5 + 0.5 x s` -> 0 at s=-1 (pure least-misery), 0.5 at s=0 (spec default), 1 at s=+1 (pure average). */
    val meanWeight: Double
        get() = RecommenderSpec.FAMILY_MEAN_WEIGHT + RecommenderSpec.FAMILY_MEAN_WEIGHT * value

    val minWeight: Double
        get() = 1.0 - meanWeight

    fun toFamilyBlendWeights(): FamilyBlendWeights = FamilyBlendWeights(meanWeight = meanWeight, minWeight = minWeight)

    companion object {
        val DEFAULT = FamilyBlendSlider()
    }
}
