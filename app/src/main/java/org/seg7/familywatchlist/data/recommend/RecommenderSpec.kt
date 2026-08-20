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
    const val SHORTLIST_TARGET_SIZE: Int = 30
    const val FAMILY_MEAN_WEIGHT: Double = 0.5
    const val FAMILY_MIN_WEIGHT: Double = 0.5

    /**
     * PLAN.md §4a slider 5 ("Suggestion count"): a per-profile integer *request* overriding
     * [SHORTLIST_TARGET_SIZE] for that profile's own personal shortlist only — family-scope
     * refreshes always use the fixed [SHORTLIST_TARGET_SIZE], never this per-profile request.
     *
     * There is deliberately no fixed maximum any more (design corrected 2026-08-20, same day, after
     * Kev pushed back on an initial fixed-50 UI ceiling and proposed a better mechanism): the
     * slider's real ceiling is the profile's actual eligible-candidate count, computed fresh by
     * [org.seg7.familywatchlist.data.repository.RecommendationRepository.refreshProfileShortlist]
     * and persisted via [org.seg7.familywatchlist.data.repository.ProfileSlidersRepository.setEligibleCandidateCount]
     * every time it runs — see [suggestionCountRange]. [SUGGESTION_COUNT_MIN] survives only as the
     * *baseline* floor that ceiling is compared against (`min(SUGGESTION_COUNT_MIN, eligibleCount)`,
     * PLAN.md §4a's edge-case handling) — it is not a hard lower bound on its own any more.
     */
    const val SUGGESTION_COUNT_MIN: Int = 4

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

/**
 * PLAN.md §4a slider 5's edge-case handling: the "Tune my picks" suggestion-count slider's valid
 * range, derived from the profile's real (last-known persisted) [eligibleCandidateCount] rather
 * than a fixed guess. The min adapts downward with a shrunk pool
 * (`min(RecommenderSpec.SUGGESTION_COUNT_MIN, eligibleCandidateCount)`) so min never exceeds max;
 * a ceiling of zero returns `null` — "disable the slider with a short explanatory message" rather
 * than rendering an inverted/broken range.
 */
fun suggestionCountRange(eligibleCandidateCount: Int): IntRange? =
    if (eligibleCandidateCount <= 0) {
        null
    } else {
        RecommenderSpec.SUGGESTION_COUNT_MIN.coerceAtMost(eligibleCandidateCount)..eligibleCandidateCount
    }
