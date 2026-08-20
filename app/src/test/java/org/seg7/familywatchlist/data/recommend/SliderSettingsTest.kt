package org.seg7.familywatchlist.data.recommend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private const val EPS = 1e-9

/** PLAN.md §4a: each slider's derived values, including the s=0 exact-reproduction requirement. */
class SliderSettingsTest {

    @Test
    fun `discovery slider at s=0 reproduces the spec wildcard count and diversity cap exactly`() {
        val defaults = SliderSettings()
        assertEquals(RecommenderSpec.WILDCARD_COUNT, defaults.wildcardCount)
        assertEquals(RecommenderSpec.DIVERSITY_CAP, defaults.diversityCap)
    }

    @Test
    fun `discovery slider extremes match PLAN md §4a`() {
        assertEquals(0, SliderSettings(discovery = -1.0).wildcardCount)
        assertEquals(3, SliderSettings(discovery = -1.0).diversityCap)
        assertEquals(2, SliderSettings(discovery = 1.0).wildcardCount)
        assertEquals(1, SliderSettings(discovery = 1.0).diversityCap)
    }

    @Test
    fun `recency slider at s=0 reproduces the 180-day spec half-life exactly`() {
        assertEquals(180.0, SliderSettings().halfLifeDays, EPS)
    }

    @Test
    fun `recency slider extremes push half-life below and above 180 days`() {
        val recent = SliderSettings(recency = 1.0).halfLifeDays
        val allTime = SliderSettings(recency = -1.0).halfLifeDays
        assertTrue("recent-weighted half-life should be well under 180 days, was $recent", recent < 90.0)
        assertTrue("all-time half-life should be well over 180 days, was $allTime", allTime > 360.0)
    }

    @Test
    fun `recency slider half-life is monotonically decreasing in s`() {
        val values = listOf(-1.0, -0.5, 0.0, 0.5, 1.0).map { SliderSettings(recency = it).halfLifeDays }
        for (i in 0 until values.size - 1) {
            assertTrue("expected ${values[i]} > ${values[i + 1]}", values[i] > values[i + 1])
        }
    }

    @Test
    fun `personalMatch slider at s=0 reproduces the spec affinity and quality weights exactly`() {
        val defaults = SliderSettings()
        assertEquals(RecommenderSpec.AFFINITY_WEIGHT, defaults.affinityWeight, EPS)
        assertEquals(RecommenderSpec.QUALITY_WEIGHT, defaults.qualityWeight, EPS)
    }

    @Test
    fun `personalMatch slider extremes stay positive and within the suggested ranges`() {
        val personal = SliderSettings(personalMatch = -1.0)
        val popular = SliderSettings(personalMatch = 1.0)

        assertEquals(0.85, personal.affinityWeight, EPS)
        assertEquals(0.05, personal.qualityWeight, EPS)
        assertEquals(0.40, popular.affinityWeight, EPS)
        assertEquals(0.35, popular.qualityWeight, EPS)

        assertTrue(personal.affinityWeight > 0 && personal.qualityWeight > 0)
        assertTrue(popular.affinityWeight > 0 && popular.qualityWeight > 0)
    }

    @Test
    fun `freshness weight is fixed regardless of the personalMatch slider`() {
        assertEquals(RecommenderSpec.FRESHNESS_WEIGHT, SliderSettings(personalMatch = -1.0).toScoringWeights().freshness, EPS)
        assertEquals(RecommenderSpec.FRESHNESS_WEIGHT, SliderSettings(personalMatch = 1.0).toScoringWeights().freshness, EPS)
    }

    @Test
    fun `out-of-range slider values are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { SliderSettings(discovery = 1.5) }
        assertThrows(IllegalArgumentException::class.java) { SliderSettings(recency = -1.01) }
        assertThrows(IllegalArgumentException::class.java) { SliderSettings(personalMatch = 2.0) }
    }

    @Test
    fun `family blend slider at s=0 reproduces the spec 0_5 0_5 blend exactly`() {
        val defaults = FamilyBlendSlider()
        assertEquals(RecommenderSpec.FAMILY_MEAN_WEIGHT, defaults.meanWeight, EPS)
        assertEquals(RecommenderSpec.FAMILY_MIN_WEIGHT, defaults.minWeight, EPS)
    }

    @Test
    fun `family blend slider extremes are pure least-misery and pure average`() {
        assertEquals(0.0, FamilyBlendSlider(-1.0).meanWeight, EPS)
        assertEquals(1.0, FamilyBlendSlider(-1.0).minWeight, EPS)
        assertEquals(1.0, FamilyBlendSlider(1.0).meanWeight, EPS)
        assertEquals(0.0, FamilyBlendSlider(1.0).minWeight, EPS)
    }

    @Test
    fun `family blend slider rejects out-of-range values`() {
        assertThrows(IllegalArgumentException::class.java) { FamilyBlendSlider(1.1) }
    }
}
