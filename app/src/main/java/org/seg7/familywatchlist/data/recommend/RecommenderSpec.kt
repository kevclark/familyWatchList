package org.seg7.familywatchlist.data.recommend

/**
 * PLAN.md §4's fixed-spec constants — the numbers every slider's s=0 position must reproduce
 * exactly (PLAN.md §4a). Collected in one place so both the no-slider defaults and every
 * slider's `s = 0` derivation can be checked against the same source of truth in tests.
 */
object RecommenderSpec {
    const val HALF_LIFE_DAYS: Double = 180.0
    const val AFFINITY_WEIGHT: Double = 0.70
    const val QUALITY_WEIGHT: Double = 0.15
    const val FRESHNESS_WEIGHT: Double = 0.15
    const val WILDCARD_COUNT: Int = 1
    const val DIVERSITY_CAP: Int = 2
    const val SHORTLIST_TARGET_SIZE: Int = 8
    const val FAMILY_MEAN_WEIGHT: Double = 0.5
    const val FAMILY_MIN_WEIGHT: Double = 0.5

    /** PLAN.md §4: "< 5 watch events for a profile -> popular-on-your-services". */
    const val COLD_START_EVENT_THRESHOLD: Int = 5

    /** PLAN.md §4: "min 20 votes" before `tmdbQuality` credits a title's vote average. */
    const val MIN_VOTES_FOR_QUALITY: Int = 20
}

/** The three scoring-formula weights (PLAN.md §4/§4a). Freshness is fixed at 0.15 regardless of sliders. */
data class ScoringWeights(
    val affinity: Double = RecommenderSpec.AFFINITY_WEIGHT,
    val quality: Double = RecommenderSpec.QUALITY_WEIGHT,
    val freshness: Double = RecommenderSpec.FRESHNESS_WEIGHT,
) {
    companion object {
        val SPEC_DEFAULT = ScoringWeights()
    }
}

/** Shortlist-assembly stage parameters (PLAN.md §4/§4a): how many titles, how much genre repetition, how many wildcards. */
data class ShortlistConfig(
    val wildcardCount: Int = RecommenderSpec.WILDCARD_COUNT,
    val diversityCap: Int = RecommenderSpec.DIVERSITY_CAP,
    val targetSize: Int = RecommenderSpec.SHORTLIST_TARGET_SIZE,
) {
    companion object {
        val SPEC_DEFAULT = ShortlistConfig()
    }
}

/** PLAN.md §4's fixed family blend: 0.5 x mean + 0.5 x min. */
data class FamilyBlendWeights(
    val meanWeight: Double = RecommenderSpec.FAMILY_MEAN_WEIGHT,
    val minWeight: Double = RecommenderSpec.FAMILY_MIN_WEIGHT,
) {
    companion object {
        val SPEC_DEFAULT = FamilyBlendWeights()
    }
}
