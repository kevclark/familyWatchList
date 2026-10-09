package org.seg7.familywatchlist.data.recommend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.seg7.familywatchlist.data.local.entity.MediaType

class ShortlistDiffTest {
    private fun m(id: Int) = TitleKey(id, MediaType.MOVIE)
    private fun t(id: Int) = TitleKey(id, MediaType.TV)

    @Test
    fun `added are in current but not previous, dropped the reverse`() {
        val diff = ShortlistDiff.compute(previous = listOf(m(1), m(2), m(3)), current = listOf(m(2), m(4), m(5)))
        assertEquals(listOf(m(4), m(5)), diff.added)
        assertEquals(listOf(m(1), m(3)), diff.dropped)
        assertEquals(3, diff.total)
        assertTrue(diff.hadPrevious)
    }

    @Test
    fun `identical shortlists produce an empty diff`() {
        val diff = ShortlistDiff.compute(listOf(m(1), m(2)), listOf(m(2), m(1)))
        assertEquals(emptyList<TitleKey>(), diff.added)
        assertEquals(emptyList<TitleKey>(), diff.dropped)
    }

    @Test
    fun `same tmdbId with a different media type is a different title`() {
        val diff = ShortlistDiff.compute(listOf(m(1)), listOf(t(1)))
        assertEquals(listOf(t(1)), diff.added)
        assertEquals(listOf(m(1)), diff.dropped)
    }

    @Test
    fun `no previous shortlist at all is a baseline with nothing marked new`() {
        val diff = ShortlistDiff.compute(previous = null, current = listOf(m(1), m(2)))
        assertEquals(emptyList<TitleKey>(), diff.added)
        assertEquals(emptyList<TitleKey>(), diff.dropped)
        assertEquals(2, diff.total)
        assertFalse(diff.hadPrevious)
    }

    @Test
    fun `an empty previous shortlist that did exist counts everything as new`() {
        val diff = ShortlistDiff.compute(previous = emptyList(), current = listOf(m(1)))
        assertEquals(listOf(m(1)), diff.added)
        assertTrue(diff.hadPrevious)
    }
}
