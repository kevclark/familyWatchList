package org.seg7.familywatchlist.data.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.seg7.familywatchlist.data.local.entity.MediaType

/** PLAN.md §5f item 1: deterministic parser, Kev's examples first, then edge cases. */
class QueryParserTest {
    private fun genres(q: String) = QueryParser.parse(q).genres.map { it.name }
    private fun candidates(q: String) = QueryParser.parse(q).candidates

    @Test
    fun `find slapstick comedies is Comedy plus a slapstick keyword candidate, both media`() {
        val p = QueryParser.parse("find slapstick comedies")
        assertEquals(listOf("Comedy"), p.genres.map { it.name })
        assertEquals(listOf("slapstick"), p.candidates.map { it.text })
        assertFalse(p.candidates.single().personFirst)
        assertEquals(setOf(MediaType.MOVIE, MediaType.TV), p.mediaTypes)
        assertTrue(p.hasStructure)
    }

    @Test
    fun `find action comedies is two ANDed genres`() {
        val p = QueryParser.parse("find action comedies")
        assertEquals(listOf("Action", "Comedy"), p.genres.map { it.name })
        assertTrue(p.candidates.isEmpty())
    }

    @Test
    fun `find movies with Ryan Reynolds is movies only plus a cued person`() {
        val p = QueryParser.parse("find movies with Ryan Reynolds")
        assertEquals(setOf(MediaType.MOVIE), p.mediaTypes)
        val c = p.candidates.single()
        assertEquals("ryan reynolds", c.text)
        assertTrue(c.cued)
        assertTrue(c.personFirst)
        assertTrue(p.hasStructure)
    }

    @Test
    fun `comedy movies means movies only`() {
        val p = QueryParser.parse("comedy movies")
        assertEquals(listOf("Comedy"), p.genres.map { it.name })
        assertEquals(setOf(MediaType.MOVIE), p.mediaTypes)
    }

    @Test
    fun `tv words select TV only`() {
        for (q in listOf("comedy shows", "comedy series", "comedy tv", "comedy programmes")) {
            assertEquals(q, setOf(MediaType.TV), QueryParser.parse(q).mediaTypes)
        }
        assertEquals(setOf(MediaType.MOVIE), QueryParser.parse("comedy films").mediaTypes)
    }

    @Test
    fun `movie and tv words together mean both`() {
        assertEquals(setOf(MediaType.MOVIE, MediaType.TV), QueryParser.parse("comedy movies and shows").mediaTypes)
    }

    @Test
    fun `plurals and variants map to the same genre`() {
        assertEquals(listOf("Comedy"), genres("comedy"))
        assertEquals(listOf("Comedy"), genres("comedies"))
        assertEquals(listOf("Comedy"), genres("funny"))
        assertEquals(listOf("Western"), genres("westerns"))
        assertEquals(listOf("Documentary"), genres("docs"))
        assertEquals(listOf("Animation"), genres("cartoons"))
        assertEquals(listOf("Horror"), genres("scary"))
        assertEquals(listOf("Family"), genres("kids"))
    }

    @Test
    fun `rom com variants are Comedy plus Romance`() {
        for (q in listOf("rom com", "rom-com", "romcom", "romcoms", "rom coms", "romantic comedies")) {
            assertEquals(q, listOf("Comedy", "Romance"), genres(q))
        }
    }

    @Test
    fun `sci fi variants and whodunnit phrases`() {
        for (q in listOf("sci fi", "sci-fi", "scifi", "science fiction")) assertEquals(q, listOf("Sci-Fi"), genres(q))
        assertEquals(listOf("Mystery", "Crime"), genres("murder mystery"))
        assertEquals(listOf("Mystery", "Crime"), genres("whodunnit"))
    }

    @Test
    fun `unknown words become keyword candidates, stop words vanish`() {
        val p = QueryParser.parse("find me some good zombie comedies please")
        assertEquals(listOf("Comedy"), p.genres.map { it.name })
        assertEquals(listOf("zombie"), p.candidates.map { it.text })
    }

    @Test
    fun `a run of two unknown words is a name candidate, person first`() {
        val c = candidates("ryan reynolds comedy").single()
        assertEquals("ryan reynolds", c.text)
        assertTrue(c.personFirst)
        assertFalse(c.cued)
    }

    @Test
    fun `with A and B yields two cued people`() {
        val cs = candidates("action movies with tom hanks and meg ryan")
        assertEquals(listOf("tom hanks", "meg ryan"), cs.map { it.text })
        assertTrue(cs.all { it.cued })
    }

    @Test
    fun `title-only queries have no structure`() {
        assertFalse(QueryParser.parse("inception").hasStructure)
        assertFalse(QueryParser.parse("movies").hasStructure)
        assertFalse(QueryParser.parse("the").hasStructure)
    }

    @Test
    fun `a keyword with a media word has structure`() {
        assertTrue(QueryParser.parse("christmas movies").hasStructure)
    }

    @Test
    fun `genre without a TV equivalent still parses, the term just has no tv id`() {
        val horror = QueryParser.parse("horror").genres.single()
        assertEquals(27, horror.movieId)
        assertEquals(null, horror.tvId)
    }

    @Test
    fun `empty and whitespace input parse to nothing`() {
        for (q in listOf("", "   ", "\t\n")) {
            val p = QueryParser.parse(q)
            assertTrue(p.genres.isEmpty() && p.media.isEmpty() && p.candidates.isEmpty())
            assertFalse(p.hasStructure)
        }
    }

    @Test
    fun `punctuation and case are ignored`() {
        assertEquals(listOf("Action", "Comedy"), genres("  ACTION, Comedies!! "))
    }

    @Test
    fun `without removes a chip's term and records the key`() {
        val p = QueryParser.parse("action comedy movies").without(setOf("genre:Action", "media:movie"))
        assertEquals(listOf("Comedy"), p.genres.map { it.name })
        assertTrue(p.media.isEmpty())
        assertEquals(setOf("genre:Action", "media:movie"), p.removed)
        assertEquals(setOf(MediaType.MOVIE, MediaType.TV), p.mediaTypes)
    }
}
