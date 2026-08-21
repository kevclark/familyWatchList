package org.seg7.familywatchlist.data.recommend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.seg7.familywatchlist.data.local.entity.AttrType

private const val EPS = 1e-9

private fun genre(id: Int) = AttrKey(AttrType.GENRE, id)

/** PLAN.md §4: family-scope `0.5 x mean + 0.5 x min` blend, plus the strictest-age-cap rule. */
class FamilyBlendTest {

    private val p1 = mapOf(genre(1) to 1.0, genre(2) to 0.0)
    private val p2 = mapOf(genre(1) to 0.2, genre(3) to 0.8)

    @Test
    fun `blendVectors at the spec default (0_5 mean, 0_5 min) matches a hand-computed fixture`() {
        val blended = FamilyBlend.blendVectors(listOf(p1, p2))

        // genre(1): values [1.0, 0.2] -> mean 0.6, min 0.2 -> 0.5*0.6 + 0.5*0.2 = 0.4
        // genre(2): values [0.0, 0.0 (missing from p2)] -> 0.0
        // genre(3): values [0.0 (missing from p1), 0.8] -> mean 0.4, min 0.0 -> 0.2
        assertEquals(0.4, blended.getValue(genre(1)), EPS)
        assertEquals(0.0, blended.getValue(genre(2)), EPS)
        assertEquals(0.2, blended.getValue(genre(3)), EPS)
    }

    @Test
    fun `pure-average blend (meanWeight=1) ignores the least-misery term`() {
        val blended = FamilyBlend.blendVectors(listOf(p1, p2), FamilyBlendWeights(meanWeight = 1.0, minWeight = 0.0))
        assertEquals(0.6, blended.getValue(genre(1)), EPS)
        assertEquals(0.4, blended.getValue(genre(3)), EPS)
    }

    @Test
    fun `pure least-misery blend (minWeight=1) always takes the harshest opinion`() {
        val blended = FamilyBlend.blendVectors(listOf(p1, p2), FamilyBlendWeights(meanWeight = 0.0, minWeight = 1.0))
        assertEquals(0.2, blended.getValue(genre(1)), EPS)
        assertEquals(0.0, blended.getValue(genre(3)), EPS)
    }

    @Test
    fun `a single profile's vector passes through blendVectors unchanged`() {
        assertEquals(p1, FamilyBlend.blendVectors(listOf(p1)))
    }

    @Test
    fun `strictestCap picks the most restrictive of several caps`() {
        assertEquals("12", FamilyBlend.strictestCap(listOf("12", "15")))
        assertEquals("12", FamilyBlend.strictestCap(listOf("18", "12", "15")))
    }

    @Test
    fun `strictestCap treats null as no cap, never tightening the result`() {
        assertEquals("15", FamilyBlend.strictestCap(listOf(null, "15")))
        assertNull(FamilyBlend.strictestCap(listOf(null, null)))
    }

    @Test
    fun `strictestCap ignores an unrecognised certification string`() {
        assertEquals("12", FamilyBlend.strictestCap(listOf("UNKNOWN", "12")))
    }

    /** M3g: the shared "is this title over the cap" check reused by every title-surfacing path. */
    @Test
    fun `isOverCap excludes a title whose certification outranks the cap`() {
        assertEquals(true, FamilyBlend.isOverCap(certification = "18", cap = "12"))
    }

    @Test
    fun `isOverCap keeps a title at or under the cap`() {
        assertEquals(false, FamilyBlend.isOverCap(certification = "12", cap = "12"))
        assertEquals(false, FamilyBlend.isOverCap(certification = "U", cap = "12"))
    }

    /** PLAN.md §8: unknown certification data never excludes a title — must not be stricter than the recommender's own established behaviour. */
    @Test
    fun `isOverCap never excludes a title with missing or unrecognised certification data`() {
        assertEquals(false, FamilyBlend.isOverCap(certification = null, cap = "12"))
        assertEquals(false, FamilyBlend.isOverCap(certification = "UNKNOWN", cap = "12"))
    }

    @Test
    fun `isOverCap never excludes anything when there is no cap`() {
        assertEquals(false, FamilyBlend.isOverCap(certification = "18", cap = null))
    }
}
