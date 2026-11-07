package org.seg7.familywatchlist.data.search

import org.seg7.familywatchlist.data.local.entity.MediaType

/**
 * A word or phrase the parser could not place from the built-in tables. The repository tries to
 * resolve it against TMDB (`search/person`, `search/keyword`); what fails is shown as "Ignored".
 *
 * [personFirst]: cued ("with X") or a run of two or more words, which look like a name. A single
 * uncued word is only tried as a keyword. [words] lets a failed multi-word phrase fall back to one
 * keyword lookup per word (only when the query is otherwise descriptive, see
 * [org.seg7.familywatchlist.data.repository.DescriptiveSearchRepository]).
 */
data class Candidate(val text: String, val words: List<String>, val personFirst: Boolean, val cued: Boolean) {
    val key: String get() = "term:$text"
}

/** Structured reading of a search box string. Pure data; no network. */
data class ParsedQuery(
    val raw: String,
    val media: List<MediaWord>,
    val genres: List<GenreTerm>,
    val candidates: List<Candidate>,
    /** Chip keys the user removed. Applied by [without]; split-word keys ("kw:<word>") are read at resolution time. */
    val removed: Set<String> = emptySet(),
) {
    /** The media types searched. No media word (or both kinds) means both. */
    val mediaTypes: Set<MediaType>
        get() {
            val movie = MediaWord.MOVIE in media
            val tv = MediaWord.TV in media
            return when {
                movie && !tv -> setOf(MediaType.MOVIE)
                tv && !movie -> setOf(MediaType.TV)
                else -> setOf(MediaType.MOVIE, MediaType.TV)
            }
        }

    /**
     * Whether to attempt a structured search at all (PLAN.md §5f item 2): any genre or unplaced
     * word or name. A lone single word ("slapstick", "zombie") is tried as a keyword; if nothing
     * resolves the repository falls back to title search. A lone media word ("movies") has no
     * structure. A title search always runs alongside (see the search ViewModel).
     */
    val hasStructure: Boolean
        get() = genres.isNotEmpty() ||
            candidates.isNotEmpty()

    fun without(keys: Set<String>): ParsedQuery = copy(
        media = media.filter { mediaKey(it) !in keys },
        genres = genres.filter { it.key !in keys },
        candidates = candidates.filter { it.key !in keys },
        removed = removed + keys,
    )

    companion object {
        fun mediaKey(word: MediaWord): String = if (word == MediaWord.MOVIE) "media:movie" else "media:tv"
    }
}

/**
 * PLAN.md §5f item 1. Deterministic word and phrase matching over [PhraseTable]; no inference.
 * Genre phrases are matched longest-first ("sci fi", "rom com", "murder mystery"), then media
 * words, cast cues ("with", "starring"), stop words; whatever remains becomes [Candidate]s.
 */
object QueryParser {
    fun parse(text: String): ParsedQuery {
        val tokens = tokenize(text)
        val media = LinkedHashSet<MediaWord>()
        val genres = LinkedHashMap<String, GenreTerm>()
        val candidates = LinkedHashMap<String, Candidate>()

        fun addCandidate(words: List<String>, cued: Boolean) {
            if (words.isEmpty()) return
            val c = Candidate(words.joinToString(" "), words, personFirst = cued || words.size >= 2, cued = cued)
            candidates.putIfAbsent(c.key, c)
        }

        /** Index where a genre phrase starts at [i], or null. Returns word count matched. */
        fun genreAt(i: Int): Pair<Int, List<GenreTerm>>? {
            for (n in minOf(PhraseTable.maxPhraseWords, tokens.size - i) downTo 1) {
                val terms = PhraseTable.genrePhrases[tokens.subList(i, i + n).joinToString(" ")]
                if (terms != null) return n to terms
            }
            return null
        }

        fun isPlain(i: Int): Boolean {
            val t = tokens[i]
            return t !in PhraseTable.stopWords && t !in PhraseTable.mediaWords && t !in PhraseTable.castCueWords && genreAt(i) == null
        }

        var i = 0
        while (i < tokens.size) {
            val genre = genreAt(i)
            when {
                genre != null -> {
                    genre.second.forEach { genres.putIfAbsent(it.name, it) }
                    i += genre.first
                }
                tokens[i] in PhraseTable.mediaWords -> {
                    media.add(PhraseTable.mediaWords.getValue(tokens[i]))
                    i++
                }
                tokens[i] in PhraseTable.castCueWords -> {
                    i++
                    // One name after the cue; "and" followed by more name words starts another.
                    while (i < tokens.size) {
                        val name = ArrayList<String>()
                        while (i < tokens.size && isPlain(i)) name.add(tokens[i++])
                        addCandidate(name, cued = true)
                        if (name.isNotEmpty() && i + 1 < tokens.size && tokens[i] == "and" && isPlain(i + 1)) i++ else break
                    }
                }
                tokens[i] in PhraseTable.stopWords -> i++
                else -> {
                    val run = ArrayList<String>()
                    while (i < tokens.size && isPlain(i)) run.add(tokens[i++])
                    addCandidate(run, cued = false)
                }
            }
        }
        return ParsedQuery(text.trim(), media.toList(), genres.values.toList(), candidates.values.toList())
    }

    /** Lowercase; hyphens and punctuation become spaces (so "sci-fi" is "sci fi"); apostrophes vanish ("children's"). */
    fun tokenize(text: String): List<String> =
        text.lowercase()
            .replace("'", "").replace("’", "")
            .map { if (it.isLetterOrDigit()) it else ' ' }
            .joinToString("")
            .split(' ')
            .filter { it.isNotEmpty() }
}
