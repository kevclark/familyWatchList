package org.seg7.familywatchlist.data.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The official TMDB genre lists, hardcoded: a wrong or mistyped id in [PhraseTable] fails here. */
class PhraseTableTest {
    private val tmdbMovieGenres = mapOf(
        28 to "Action", 12 to "Adventure", 16 to "Animation", 35 to "Comedy", 80 to "Crime", 99 to "Documentary",
        18 to "Drama", 10751 to "Family", 14 to "Fantasy", 36 to "History", 27 to "Horror", 10402 to "Music",
        9648 to "Mystery", 10749 to "Romance", 878 to "Science Fiction", 10770 to "TV Movie", 53 to "Thriller",
        10752 to "War", 37 to "Western",
    )
    private val tmdbTvGenres = mapOf(
        10759 to "Action & Adventure", 16 to "Animation", 35 to "Comedy", 80 to "Crime", 99 to "Documentary",
        18 to "Drama", 10751 to "Family", 10762 to "Kids", 9648 to "Mystery", 10763 to "News", 10764 to "Reality",
        10765 to "Sci-Fi & Fantasy", 10766 to "Soap", 10767 to "Talk", 10768 to "War & Politics", 37 to "Western",
    )

    @Test
    fun `every mapped movie id is a valid TMDB movie genre id`() {
        PhraseTable.allGenreTerms.mapNotNull { it.movieId }.forEach { assertTrue("movie id $it", it in tmdbMovieGenres) }
    }

    @Test
    fun `every mapped tv id is a valid TMDB tv genre id`() {
        PhraseTable.allGenreTerms.mapNotNull { it.tvId }.forEach { assertTrue("tv id $it", it in tmdbTvGenres) }
    }

    @Test
    fun `movie and tv ids differ where TMDB's lists differ`() {
        assertEquals(28, PhraseTable.ACTION.movieId)
        assertEquals(10759, PhraseTable.ACTION.tvId)
        assertEquals(878, PhraseTable.SCI_FI.movieId)
        assertEquals(10765, PhraseTable.SCI_FI.tvId)
    }

    @Test
    fun `every phrase maps to terms from the table and every term has at least one id`() {
        PhraseTable.genrePhrases.values.flatten().forEach { assertTrue(it in PhraseTable.allGenreTerms) }
        PhraseTable.allGenreTerms.forEach { assertTrue(it.name, it.movieId != null || it.tvId != null) }
    }

    @Test
    fun `phrases are stored normalised so the parser can look them up`() {
        PhraseTable.genrePhrases.keys.forEach { assertEquals(it, QueryParser.tokenize(it).joinToString(" ")) }
    }

    @Test
    fun `media words are not stop words`() {
        assertTrue(PhraseTable.stopWords.intersect(PhraseTable.mediaWords.keys).isEmpty())
    }

    // ---- themes (PLAN.md §5g) -----------------------------------------------------------------

    @Test
    fun `there are 50 to 80 themes with unique names and non-empty id lists`() {
        assertTrue("themes: ${PhraseTable.themes.size}", PhraseTable.themes.size in 50..80)
        assertEquals(PhraseTable.themes.size, PhraseTable.themes.map { it.name }.toSet().size)
        PhraseTable.themes.forEach {
            assertTrue(it.name, it.keywordIds.isNotEmpty())
            assertEquals(it.name, it.keywordIds.size, it.keywordIds.toSet().size)
        }
    }

    @Test
    fun `theme phrases are normalised and every phrase maps to a listed theme`() {
        PhraseTable.themePhrases.keys.forEach { assertEquals(it, QueryParser.tokenize(it).joinToString(" ")) }
        PhraseTable.themePhrases.values.forEach { assertTrue(it.name, it in PhraseTable.themes) }
        PhraseTable.themes.forEach { t -> assertTrue(t.name, PhraseTable.themePhrases.values.any { it == t }) }
    }

    @Test
    fun `no phrase is both a genre and a theme, and themes never swallow media words`() {
        assertEquals(emptySet<String>(), PhraseTable.genrePhrases.keys.intersect(PhraseTable.themePhrases.keys))
        assertEquals(emptySet<String>(), PhraseTable.mediaWords.keys.intersect(PhraseTable.themePhrases.keys))
        PhraseTable.themePhrases.keys.forEach { phrase ->
            assertTrue("$phrase contains a media word", phrase.split(' ').none { it in PhraseTable.mediaWords })
        }
    }

    @Test
    fun `the near-unused tags are never chosen`() {
        val all = PhraseTable.themes.flatMap { it.keywordIds }
        assertTrue(382039 !in all) // "london" (14 films); the theme uses "london, england" 212
        assertTrue(386351 !in all) // "dystopian" (6 films); the theme uses "dystopia" 4565
        assertEquals(listOf(212), PhraseTable.themePhrases.getValue("london").keywordIds)
        assertEquals(listOf(4565), PhraseTable.themePhrases.getValue("dystopian").keywordIds)
    }

    @Test
    fun `the mandated entries map to the verified ids`() {
        fun ids(phrase: String) = PhraseTable.themePhrases.getValue(phrase).keywordIds
        assertEquals(listOf(9715, 33637), ids("superpowers"))
        assertEquals(ids("superpowers"), ids("overpowered"))
        assertEquals(ids("superpowers"), ids("super beings"))
        assertEquals(listOf(12190), ids("cyberpunk"))
        assertEquals(listOf(12377), ids("undead"))
        assertEquals(listOf(10051), ids("robbery"))
        assertEquals(listOf(4379), ids("time travel"))
        assertEquals(listOf(4458), ids("end of the world"))
        assertEquals(listOf(9951), ids("aliens"))
        assertEquals(listOf(286125, 207268, 155451), ids("bleak"))
        assertEquals(listOf(470, 5265, 4289, 14555, 190904), ids("mi5"))
        assertEquals(listOf(3149, 10291), ids("mob"))
        assertEquals(listOf(1568), ids("undercover"))
        assertEquals(listOf(268067), ids("cop"))
        assertEquals(listOf(208548, 298625), ids("manchester"))
        listOf("glasgow", "edinburgh", "liverpool", "birmingham").forEach { assertTrue(it, it in PhraseTable.themePhrases) }
    }
}
