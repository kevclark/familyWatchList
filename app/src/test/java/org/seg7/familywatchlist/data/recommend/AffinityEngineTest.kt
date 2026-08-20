package org.seg7.familywatchlist.data.recommend

import java.time.LocalDate
import kotlin.math.ln
import org.junit.Assert.assertEquals
import org.junit.Test
import org.seg7.familywatchlist.data.local.entity.AttrType
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.RatingValue

private const val EPS = 1e-9

private fun genre(id: Int) = AttrKey(AttrType.GENRE, id)
private fun cast(id: Int) = AttrKey(AttrType.CAST, id)

/** PLAN.md §4's affinity-vector math, verified stage-by-stage against hand-computed fixtures. */
class AffinityEngineTest {

    @Test
    fun `ratingWeight matches PLAN md §4 exactly`() {
        assertEquals(1.0, AffinityEngine.ratingWeight(RatingValue.UP), EPS)
        assertEquals(0.4, AffinityEngine.ratingWeight(RatingValue.NEUTRAL), EPS)
        assertEquals(0.4, AffinityEngine.ratingWeight(null), EPS)
        assertEquals(-0.8, AffinityEngine.ratingWeight(RatingValue.DOWN), EPS)
    }

    @Test
    fun `recencyWeight is 1 at zero days and 0_5 at exactly the half-life`() {
        assertEquals(1.0, AffinityEngine.recencyWeight(0, 180.0), EPS)
        assertEquals(0.5, AffinityEngine.recencyWeight(180, 180.0), 1e-6)
    }

    @Test
    fun `recencyWeight never goes past 1 for a future-dated (negative days) event`() {
        assertEquals(1.0, AffinityEngine.recencyWeight(-10, 180.0), EPS)
    }

    @Test
    fun `buildRawVector accumulates rating x recency across attributes, plus the +0_6 watchlist bonus`() {
        val today = LocalDate.of(2026, 8, 16)
        val watches = listOf(
            RatedWatch(
                title = TitleKey(100, MediaType.MOVIE),
                attributes = listOf(genre(1), genre(2)),
                watchedAt = today,
                rating = RatingValue.UP,
            )
        )
        val watchlist = listOf(
            WatchlistSignal(
                title = TitleKey(200, MediaType.MOVIE),
                attributes = listOf(genre(1)),
                addedAt = today,
            )
        )
        val raw = AffinityEngine.buildRawVector(watches, watchlist, today, halfLifeDays = 180.0)

        // genre(1): 1.0 (watch, UP @ 0 days) + 0.6 (watchlist bonus @ 0 days) = 1.6
        // genre(2): 1.0 (watch only)
        assertEquals(1.6, raw.getValue(genre(1)), EPS)
        assertEquals(1.0, raw.getValue(genre(2)), EPS)
        assertEquals(2, raw.size)
    }

    @Test
    fun `applyIdfDamping fully zeroes an attribute present on every watched title`() {
        val raw = mapOf(genre(1) to 2.0, genre(2) to 3.0)
        val corpus = listOf(
            setOf(genre(1), genre(2)), // doc A
            setOf(genre(1)),           // doc B
        )
        val damped = AffinityEngine.applyIdfDamping(raw, corpus)

        // genre(1): df=2, totalDocs=2 -> idf = ln(2/2) = 0 -> fully damped
        assertEquals(0.0, damped.getValue(genre(1)), EPS)
        // genre(2): df=1, totalDocs=2 -> idf = ln(2/1)
        assertEquals(3.0 * ln(2.0 / 1.0), damped.getValue(genre(2)), 1e-9)
    }

    @Test
    fun `applyIdfDamping leaves the vector untouched with an empty corpus`() {
        val raw = mapOf(genre(1) to 2.0)
        assertEquals(raw, AffinityEngine.applyIdfDamping(raw, emptyList()))
    }

    @Test
    fun `l2NormalisePerType normalises each attribute type as its own unit vector`() {
        val vector = mapOf(genre(1) to 3.0, genre(2) to 4.0, cast(5) to 6.0)
        val normalised = AffinityEngine.l2NormalisePerType(vector)

        // GENRE sub-vector norm = sqrt(3^2 + 4^2) = 5
        assertEquals(0.6, normalised.getValue(genre(1)), EPS)
        assertEquals(0.8, normalised.getValue(genre(2)), EPS)
        // CAST sub-vector has one entry, norm = 6 -> normalises to 1.0
        assertEquals(1.0, normalised.getValue(cast(5)), EPS)
    }

    @Test
    fun `l2NormalisePerType never divides by zero for an all-zero type`() {
        val normalised = AffinityEngine.l2NormalisePerType(mapOf(genre(1) to 0.0))
        assertEquals(0.0, normalised.getValue(genre(1)), EPS)
    }

    @Test
    fun `buildAffinityVector runs the full pipeline end to end against a hand-computed fixture`() {
        val today = LocalDate.of(2026, 8, 16)
        val watches = listOf(
            // Comedy(35) + Action(28), watched today, UP.
            RatedWatch(TitleKey(100, MediaType.MOVIE), listOf(genre(35), genre(28)), today, RatingValue.UP),
            // Comedy(35) only, watched exactly one half-life ago, DOWN.
            RatedWatch(TitleKey(200, MediaType.MOVIE), listOf(genre(35)), today.minusDays(180), RatingValue.DOWN),
        )

        val vector = AffinityEngine.buildAffinityVector(watches, emptyList(), today, halfLifeDays = 180.0)

        // raw: genre35 = 1.0*1.0 + (-0.8)*0.5 = 0.6 ; genre28 = 1.0*1.0 = 1.0
        // idf: genre35 in both of 2 docs -> ln(2/2)=0 -> damped to 0.0
        //      genre28 in 1 of 2 docs -> ln(2/1) -> damped = 1.0 * ln2
        // L2-normalise (only GENRE type present): norm = ln2 -> genre35=0, genre28=1.0
        assertEquals(0.0, vector.getValue(genre(35)), 1e-9)
        assertEquals(1.0, vector.getValue(genre(28)), 1e-9)
    }
}
