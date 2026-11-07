package org.seg7.familywatchlist.data.search

/**
 * PLAN.md §5f: the one place that maps words a person types to TMDB ids. Deterministic lookup
 * only (Kev's hard constraint: no inference of any kind). Everything here is reviewable data.
 *
 * Movie and TV genre ids differ, so each [GenreTerm] carries both, and a null id means "no
 * equivalent on that side" (e.g. Horror and Romance have no TV genre; the term is dropped from the
 * TV side). The official lists the ids must belong to are pinned in `PhraseTableTest`.
 */
data class GenreTerm(val name: String, val movieId: Int?, val tvId: Int?) {
    /** Stable chip key, also what removing the chip puts in the removed set. */
    val key: String get() = "genre:$name"
}

enum class MediaWord { MOVIE, TV }

object PhraseTable {
    val ACTION = GenreTerm("Action", movieId = 28, tvId = 10759)
    val ADVENTURE = GenreTerm("Adventure", movieId = 12, tvId = 10759)
    val ANIMATION = GenreTerm("Animation", movieId = 16, tvId = 16)
    val COMEDY = GenreTerm("Comedy", movieId = 35, tvId = 35)
    val CRIME = GenreTerm("Crime", movieId = 80, tvId = 80)
    val DOCUMENTARY = GenreTerm("Documentary", movieId = 99, tvId = 99)
    val DRAMA = GenreTerm("Drama", movieId = 18, tvId = 18)
    val FAMILY = GenreTerm("Family", movieId = 10751, tvId = 10751)
    val FANTASY = GenreTerm("Fantasy", movieId = 14, tvId = 10765)
    val HISTORY = GenreTerm("History", movieId = 36, tvId = null)
    val HORROR = GenreTerm("Horror", movieId = 27, tvId = null)
    val MUSIC = GenreTerm("Music", movieId = 10402, tvId = null)
    val MYSTERY = GenreTerm("Mystery", movieId = 9648, tvId = 9648)
    val REALITY = GenreTerm("Reality", movieId = null, tvId = 10764)
    val ROMANCE = GenreTerm("Romance", movieId = 10749, tvId = null)
    val SCI_FI = GenreTerm("Sci-Fi", movieId = 878, tvId = 10765)
    val THRILLER = GenreTerm("Thriller", movieId = 53, tvId = null)
    val WAR = GenreTerm("War", movieId = 10752, tvId = 10768)
    val WESTERN = GenreTerm("Western", movieId = 37, tvId = 37)

    val allGenreTerms: List<GenreTerm> = listOf(
        ACTION, ADVENTURE, ANIMATION, COMEDY, CRIME, DOCUMENTARY, DRAMA, FAMILY, FANTASY, HISTORY,
        HORROR, MUSIC, MYSTERY, REALITY, ROMANCE, SCI_FI, THRILLER, WAR, WESTERN,
    )

    /** Normalised phrase (lowercase, words separated by single spaces) to the genre terms it means. */
    val genrePhrases: Map<String, List<GenreTerm>> = buildMap {
        fun add(terms: List<GenreTerm>, vararg phrases: String) = phrases.forEach { put(it, terms) }
        add(listOf(ACTION), "action")
        add(listOf(ADVENTURE), "adventure", "adventures")
        add(listOf(ANIMATION), "animation", "animated", "animations", "cartoon", "cartoons", "anime")
        add(listOf(COMEDY), "comedy", "comedies", "comic", "comedic", "funny", "hilarious")
        add(listOf(COMEDY, ROMANCE), "romcom", "romcoms", "rom com", "rom coms", "romantic comedy", "romantic comedies")
        add(listOf(CRIME), "crime", "crimes", "gangster", "gangsters")
        add(listOf(DOCUMENTARY), "documentary", "documentaries", "doc", "docs", "docu", "docuseries")
        add(listOf(DRAMA), "drama", "dramas", "dramatic")
        add(listOf(FAMILY), "family", "kids", "kid", "children", "childrens")
        add(listOf(FANTASY), "fantasy", "fantasies")
        add(listOf(HISTORY), "history", "historical")
        add(listOf(HORROR), "horror", "horrors", "scary", "spooky", "frightening")
        add(listOf(MUSIC), "music", "musical", "musicals")
        add(listOf(MYSTERY), "mystery", "mysteries")
        add(listOf(MYSTERY, CRIME), "whodunnit", "whodunnits", "whodunit", "whodunits", "murder mystery", "murder mysteries")
        add(listOf(REALITY), "reality")
        add(listOf(ROMANCE), "romance", "romances", "romantic")
        add(listOf(SCI_FI), "scifi", "sci fi", "science fiction")
        add(listOf(THRILLER), "thriller", "thrillers", "suspense", "suspenseful")
        add(listOf(WAR), "war", "wars")
        add(listOf(WESTERN), "western", "westerns")
    }

    val mediaWords: Map<String, MediaWord> = mapOf(
        "movie" to MediaWord.MOVIE, "movies" to MediaWord.MOVIE, "film" to MediaWord.MOVIE, "films" to MediaWord.MOVIE,
        "show" to MediaWord.TV, "shows" to MediaWord.TV, "series" to MediaWord.TV, "tv" to MediaWord.TV,
        "programme" to MediaWord.TV, "programmes" to MediaWord.TV, "program" to MediaWord.TV, "programs" to MediaWord.TV,
    )

    /** Words that carry no search meaning. They are dropped silently (not listed under "Ignored"). */
    val stopWords: Set<String> = setOf(
        "find", "me", "us", "some", "a", "an", "the", "good", "great", "best", "nice", "new", "for", "please",
        "something", "anything", "to", "watch", "watching", "see", "i", "we", "you", "want", "wanna", "like", "about",
        "of", "and", "or", "in", "on", "that", "this", "is", "are", "any", "my", "our", "tonight", "today", "night",
        "evening", "weekend", "show", "give", "get", "search", "looking", "look", "need", "can", "could", "would",
    ).minus(mediaWords.keys)

    /** "with X", "starring X": the next words are a person's name. */
    val castCueWords: Set<String> = setOf("with", "starring", "featuring", "stars", "starring", "ft", "feat")

    /** Longest multi-word phrase in [genrePhrases], so the parser knows how far to look ahead. */
    val maxPhraseWords: Int = genrePhrases.keys.maxOf { it.split(' ').size }
}
