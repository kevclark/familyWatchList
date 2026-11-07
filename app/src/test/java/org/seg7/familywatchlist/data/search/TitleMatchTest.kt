package org.seg7.familywatchlist.data.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleMatchTest {
    @Test
    fun `equal or starts-with after normalising`() {
        assertTrue(TitleMatch.matches("Star Wars", "star wars"))
        assertTrue(TitleMatch.matches("Star Wars: The Clone Wars", "Star Wars"))
        assertTrue(TitleMatch.matches("Family Guy", "family guy"))
        assertFalse(TitleMatch.matches("The Making of Star Wars", "star wars"))
        assertFalse(TitleMatch.matches("Anything", ""))
    }

    @Test
    fun `ampersand and the word and are equal, punctuation is ignored`() {
        assertTrue(TitleMatch.matches("Tom & Jerry", "tom and jerry"))
        assertTrue(TitleMatch.matches("Tom and Jerry", "Tom & Jerry"))
        assertTrue(TitleMatch.matches("Schitt's Creek", "schitts creek"))
        assertEquals("tom and jerry", TitleMatch.normalise("Tom & Jerry!"))
    }
}
