package org.seg7.familywatchlist.data.search

import org.junit.Assert.assertEquals
import org.junit.Test

class AgeFilterTest {
    @Test
    fun `chip for a cap uses the shared ranking including M12 equivalents`() {
        assertEquals("U", AgeFilter.optionForCap("U").label)
        assertEquals("PG", AgeFilter.optionForCap("7+").label)
        assertEquals("12", AgeFilter.optionForCap("12A").label)
        assertEquals("12", AgeFilter.optionForCap("13+").label)
        assertEquals("15", AgeFilter.optionForCap("16+").label)
        assertEquals("18", AgeFilter.optionForCap("18+").label)
        assertEquals("Any", AgeFilter.optionForCap(null).label)
        assertEquals("Any", AgeFilter.optionForCap("bogus").label)
    }

    @Test
    fun `12 chip sends 12A to TMDB so 12A films are kept`() {
        assertEquals("12A", AgeFilter.optionForCap("12").tmdbCertification)
    }

    @Test
    fun `rated titles over the cap are hidden, at or under are shown`() {
        assertEquals(AgeVerdict.HIDE, AgeFilter.evaluate("15", "12"))
        assertEquals(AgeVerdict.SHOW, AgeFilter.evaluate("12A", "12"))
        assertEquals(AgeVerdict.SHOW, AgeFilter.evaluate("PG", "15"))
        assertEquals(AgeVerdict.SHOW, AgeFilter.evaluate("18", null))
    }

    @Test
    fun `unrated is hidden at 12 or below and labelled above`() {
        for (cap in listOf("U", "PG", "12")) assertEquals(cap, AgeVerdict.HIDE, AgeFilter.evaluate(null, cap))
        for (cap in listOf("15", "18", null)) assertEquals(cap, AgeVerdict.SHOW_UNRATED, AgeFilter.evaluate(null, cap))
        assertEquals(AgeVerdict.HIDE, AgeFilter.evaluate("NR", "PG"))
    }
}
