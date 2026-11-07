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
        val p = QueryParser.parse("find me some good cozy comedies please")
        assertEquals(listOf("Comedy"), p.genres.map { it.name })
        assertEquals(listOf("cozy"), p.candidates.map { it.text })
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
        assertFalse(QueryParser.parse("movies").hasStructure)
        assertFalse(QueryParser.parse("the").hasStructure)
    }

    @Test
    fun `a lone unplaced word is a keyword candidate with structure`() {
        val p = QueryParser.parse("slapstick")
        assertTrue(p.hasStructure)
        assertFalse(p.candidates.single().personFirst)
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

    // ---- themes (PLAN.md §5g) -----------------------------------------------------------------

    private fun themes(q: String) = QueryParser.parse(q).themes.map { it.name }

    @Test
    fun `gritty london spy shows is TV only with three theme groups and no leftovers`() {
        val p = QueryParser.parse("gritty london spy shows")
        assertEquals(listOf("Gritty", "London", "Spy"), p.themes.map { it.name })
        assertEquals(listOf(listOf(286125, 207268, 155451), listOf(212), listOf(470, 5265, 4289, 14555, 190904)), p.themes.map { it.keywordIds })
        assertEquals(setOf(MediaType.TV), p.mediaTypes)
        assertTrue(p.genres.isEmpty())
        assertTrue(p.candidates.isEmpty())
        assertTrue(p.hasStructure)
    }

    @Test
    fun `dystopian movies is films with the dystopia group`() {
        val p = QueryParser.parse("dystopian movies")
        assertEquals(setOf(MediaType.MOVIE), p.mediaTypes)
        assertEquals(listOf(listOf(4565)), p.themes.map { it.keywordIds })
        assertTrue(p.candidates.isEmpty())
    }

    @Test
    fun `london is 212 and never reaches the generic keyword lookup`() {
        val p = QueryParser.parse("london")
        assertEquals(listOf(listOf(212)), p.themes.map { it.keywordIds })
        assertTrue(p.candidates.isEmpty())
    }

    @Test
    fun `multi-word phrases win over their single words`() {
        // "cold war" is the Cold war theme, not the War genre; "world war ii" likewise.
        assertEquals(listOf("Cold war"), themes("cold war"))
        assertTrue(genres("cold war thrillers").none { it == "War" })
        assertEquals(listOf("Thriller"), genres("cold war thrillers"))
        assertEquals(listOf("World War II"), themes("world war ii films"))
        assertTrue(genres("world war ii films").isEmpty())
        // "american football" beats "football"; "dark comedy" beats "dark" and "comedy".
        assertEquals(listOf("American football"), themes("american football"))
        assertEquals(listOf("Football"), themes("football"))
        assertEquals(listOf("Dark comedy"), themes("dark comedy"))
        assertTrue(genres("dark comedy").isEmpty())
        // ...but "dark" next to a plain "comedy" is two separate things.
        assertEquals(listOf("Gritty"), themes("dark funny"))
        assertEquals(listOf("Comedy"), genres("dark funny"))
        // "psychological thriller" is its own theme, plain "thriller" stays a genre.
        assertEquals(listOf("Psychological thriller"), themes("psychological thriller"))
        assertEquals(listOf("Thriller"), genres("thriller"))
        // Five-word phrase with stop words inside it.
        assertEquals(listOf("Based on a true story"), themes("based on a true story"))
        assertTrue(QueryParser.parse("based on a true story").candidates.isEmpty())
    }

    @Test
    fun `hyphens and A dot I dot normalise before matching`() {
        assertEquals(listOf("Time travel"), themes("time-travel"))
        assertEquals(listOf("Post-apocalyptic"), themes("post-apocalyptic"))
        assertEquals(listOf("AI"), themes("A.I."))
    }

    @Test
    fun `cop show keeps its TV restriction because the theme does not swallow the media word`() {
        val p = QueryParser.parse("cop show")
        assertEquals(listOf("Police procedural"), p.themes.map { it.name })
        assertEquals(setOf(MediaType.TV), p.mediaTypes)
    }

    @Test
    fun `the same theme from two phrases appears once`() {
        assertEquals(listOf("Superhero"), themes("superheroes with superpowers"))
    }

    @Test
    fun `clash cases keep genre words genres and theme words themes`() {
        // Genre words are untouched by themes.
        assertEquals(listOf("Horror"), genres("scary"))
        assertTrue(themes("scary").isEmpty())
        assertEquals(listOf("Music"), genres("musicals")) // deliberately a genre, not a theme
        assertTrue(themes("musicals").isEmpty())
        assertEquals(listOf("War"), genres("war films"))
        assertTrue(themes("war films").isEmpty())
        // "gangster" moved from the Crime genre to a theme; "crime" itself is still the genre.
        assertTrue(genres("gangster films").isEmpty())
        assertEquals(listOf("Gangster"), themes("gangster films"))
        assertEquals(listOf("Crime"), genres("crime"))
        assertEquals(listOf("Crime"), genres("gangster crime"))
        assertEquals(listOf("Gangster"), themes("gangster crime"))
        // A theme and a genre together are both kept: zombie comedies.
        val p = QueryParser.parse("zombie comedies")
        assertEquals(listOf("Comedy"), p.genres.map { it.name })
        assertEquals(listOf("Zombie"), p.themes.map { it.name })
    }

    @Test
    fun `removing a theme chip drops only that theme`() {
        val p = QueryParser.parse("gritty london spy shows").without(setOf("theme:Spy"))
        assertEquals(listOf("Gritty", "London"), p.themes.map { it.name })
        assertEquals(setOf(MediaType.TV), p.mediaTypes)
        assertTrue("theme:Spy" in p.removed)
    }
}
