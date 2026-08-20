package org.seg7.familywatchlist.data.recommend

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt
import org.seg7.familywatchlist.data.local.entity.RatingValue

/** One watched title contributing to a profile's affinity vector (PLAN.md §4). */
data class RatedWatch(
    val title: TitleKey,
    val attributes: List<AttrKey>,
    val watchedAt: LocalDate,
    /** Null means "unrated" — shares [AffinityEngine.ratingWeight] with [RatingValue.NEUTRAL]. */
    val rating: RatingValue?,
)

/** An ACTIVE Want-to-Watch entry contributing the "adding is a taste signal" watchlist bonus (PLAN.md §4). */
data class WatchlistSignal(
    val title: TitleKey,
    val attributes: List<AttrKey>,
    val addedAt: LocalDate,
)

/**
 * PLAN.md §4's affinity-vector math: rating x recency weighted attribute accumulation, the
 * watchlist bonus, IDF damping over the watched corpus, and per-attribute-type L2 normalisation.
 * Pure functions over plain fixtures — no Room/TMDB coupling — so every step is independently
 * unit-testable against the plan's hand-computable examples.
 */
object AffinityEngine {

    /** PLAN.md §4: `UP = +1.0, unrated/NEUTRAL = +0.4, DOWN = -0.8`. */
    fun ratingWeight(rating: RatingValue?): Double = when (rating) {
        RatingValue.UP -> 1.0
        RatingValue.DOWN -> -0.8
        RatingValue.NEUTRAL, null -> 0.4
    }

    /** PLAN.md §4: `exp(-ln2 x daysSince / halfLifeDays)`. [daysSince] is clamped to >= 0 (a future-dated event decays no less than "just now"). */
    fun recencyWeight(daysSince: Long, halfLifeDays: Double): Double {
        val days = daysSince.coerceAtLeast(0L)
        return exp(-LN2 * days / halfLifeDays)
    }

    /**
     * Raw (pre-damping, pre-normalisation) accumulation: for each watch, `ratingWeight x
     * recencyWeight(watchedAt)` added to every attribute of the title; for each watchlist entry,
     * PLAN.md §4's `+0.6 x recencyWeight(addedAt)` bonus added the same way.
     */
    fun buildRawVector(
        watches: List<RatedWatch>,
        watchlist: List<WatchlistSignal>,
        today: LocalDate,
        halfLifeDays: Double,
    ): Map<AttrKey, Double> {
        val acc = LinkedHashMap<AttrKey, Double>()
        watches.forEach { watch ->
            val days = ChronoUnit.DAYS.between(watch.watchedAt, today)
            val weight = ratingWeight(watch.rating) * recencyWeight(days, halfLifeDays)
            watch.attributes.forEach { attr -> acc[attr] = (acc[attr] ?: 0.0) + weight }
        }
        watchlist.forEach { entry ->
            val days = ChronoUnit.DAYS.between(entry.addedAt, today)
            val weight = WATCHLIST_SIGNAL_WEIGHT * recencyWeight(days, halfLifeDays)
            entry.attributes.forEach { attr -> acc[attr] = (acc[attr] ?: 0.0) + weight }
        }
        return acc
    }

    /**
     * PLAN.md §4: "dampen ubiquitous attributes (everything is 'Drama') with an IDF-style
     * factor computed over the watched corpus." [corpus] is one entry per *distinct watched
     * title* (deduplicated — a rewatch doesn't count twice towards "how common is this
     * attribute"), each a set of that title's attributes.
     *
     * `idf(a) = ln(totalDocs / documentFrequency(a))`, clamped to >= 0 — an attribute present on
     * every single watched title (documentFrequency == totalDocs) is fully damped to zero
     * contribution, exactly the "everything is Drama" case the plan names. An empty corpus
     * (nothing watched yet) leaves the raw vector untouched rather than dividing by zero — this
     * path is only reachable in practice via a caller that bypasses the cold-start gate.
     */
    fun applyIdfDamping(raw: Map<AttrKey, Double>, corpus: List<Set<AttrKey>>): Map<AttrKey, Double> {
        if (corpus.isEmpty()) return raw
        val totalDocs = corpus.size
        val documentFrequency = HashMap<AttrKey, Int>()
        corpus.forEach { doc -> doc.forEach { attr -> documentFrequency[attr] = (documentFrequency[attr] ?: 0) + 1 } }
        return raw.mapValues { (attr, value) ->
            val df = documentFrequency[attr] ?: 1
            val idf = ln(totalDocs.toDouble() / df).coerceAtLeast(0.0)
            value * idf
        }
    }

    /**
     * PLAN.md §4: "L2-normalise per attribute type" — each of GENRE/CAST/CREW/KEYWORD is
     * normalised as its own sub-vector, so (e.g.) a profile with 40 cast credits but only 3
     * genres doesn't have its genre signal dwarfed by cast merely having more dimensions.
     */
    fun l2NormalisePerType(vector: Map<AttrKey, Double>): Map<AttrKey, Double> {
        val byType = vector.entries.groupBy { it.key.attrType }
        val result = LinkedHashMap<AttrKey, Double>()
        byType.values.forEach { entries ->
            val norm = sqrt(entries.sumOf { it.value * it.value })
            entries.forEach { (key, value) -> result[key] = if (norm > 0.0) value / norm else 0.0 }
        }
        return result
    }

    /** The full pipeline: raw accumulation -> IDF damping -> per-type L2 normalisation. */
    fun buildAffinityVector(
        watches: List<RatedWatch>,
        watchlist: List<WatchlistSignal>,
        today: LocalDate,
        halfLifeDays: Double = RecommenderSpec.HALF_LIFE_DAYS,
    ): Map<AttrKey, Double> {
        val raw = buildRawVector(watches, watchlist, today, halfLifeDays)
        val corpus = watches
            .distinctBy { it.title }
            .map { it.attributes.toSet() }
        val damped = applyIdfDamping(raw, corpus)
        return l2NormalisePerType(damped)
    }

    private const val LN2 = 0.6931471805599453
    const val WATCHLIST_SIGNAL_WEIGHT = 0.6
}
