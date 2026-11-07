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
}
