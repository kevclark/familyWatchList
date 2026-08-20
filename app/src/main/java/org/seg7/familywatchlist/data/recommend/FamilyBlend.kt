package org.seg7.familywatchlist.data.recommend

/**
 * PLAN.md §4: family-scope aggregation — `0.5 x mean + 0.5 x min` (least-misery blend, "nothing
 * anyone hates") over selected profiles' affinity vectors, plus the strictest age cap among them.
 * Slider 4 (PLAN.md §4a) tunes the mean/min split; s=0 reproduces the fixed 0.5/0.5 spec exactly.
 */
object FamilyBlend {

    /**
     * Blends N profiles' (already IDF-damped, L2-normalised) affinity vectors into one. An
     * attribute missing from one profile's vector is treated as 0 for that profile (no opinion),
     * not excluded — a title's genre that only half the family has any history with should still
     * be down-weighted by the "min" term for the half that doesn't, per "least-misery".
     */
    fun blendVectors(
        vectors: List<Map<AttrKey, Double>>,
        weights: FamilyBlendWeights = FamilyBlendWeights.SPEC_DEFAULT,
    ): Map<AttrKey, Double> {
        require(vectors.isNotEmpty()) { "blendVectors requires at least one profile" }
        if (vectors.size == 1) return vectors.single()
        val allKeys = vectors.flatMapTo(LinkedHashSet()) { it.keys }
        return allKeys.associateWith { key ->
            val values = vectors.map { it[key] ?: 0.0 }
            weights.meanWeight * values.average() + weights.minWeight * values.min()
        }
    }

    /**
     * UK certification strictness order (PLAN.md §2's `ageRatingCap`: "e.g. '12' (UK certs
     * U/PG/12/15/18)"). 12A is BBFC's cinema-only sibling of 12 (same audience floor) and is
     * accepted here too since TMDB's GB certification field sometimes carries it.
     */
    private val CERT_RANK: Map<String, Int> = listOf("U", "PG", "12", "12A", "15", "18")
        .withIndex().associate { (index, cert) -> cert to index }

    /**
     * The same ranking [strictestCap] uses, exposed for candidate-side age-cap filtering
     * ([org.seg7.familywatchlist.data.repository.RecommendationRepository] excludes a candidate
     * whose certification outranks a profile's/family's cap). Null for an unrecognised string —
     * caller treats that as "can't compare, don't exclude on this basis" per PLAN.md §8's UK
     * provider/certification-data-quality note.
     */
    fun certRank(cert: String): Int? = CERT_RANK[cert]

    /**
     * PLAN.md §4: "apply the strictest ageRatingCap among them." Null means "no cap" for that
     * profile and never tightens the result; an unrecognised (non-UK-cert) string is ignored for
     * ranking purposes rather than crashing, since it can't be compared — real GB certifications
     * are always one of [CERT_RANK]'s keys.
     */
    fun strictestCap(caps: List<String?>): String? =
        caps.filterNotNull()
            .mapNotNull { cap -> CERT_RANK[cap]?.let { rank -> cap to rank } }
            .minByOrNull { it.second }
            ?.first
}
