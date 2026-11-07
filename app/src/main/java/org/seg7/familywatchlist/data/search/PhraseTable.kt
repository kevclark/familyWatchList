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

/**
 * A theme: one everyday idea ("Spy", "London") mapped to a fixed list of TMDB keyword ids. The ids are
 * one OR group (`|` in `with_keywords`); different themes and other keyword groups AND together.
 * Hard-coded, never resolved at runtime (PLAN.md §5g).
 */
data class ThemeTerm(val name: String, val keywordIds: List<Int>) {
    /** Stable chip key, also what removing the chip puts in the removed set. */
    val key: String get() = "theme:$name"
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
        add(listOf(CRIME), "crime", "crimes")
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

    /**
     * PLAN.md §5g: themes. Pure lookup, no inference. Every id was checked against live TMDB on 2026-11-07
     * (`search/keyword`, then `discover/movie` and `discover/tv` `total_results` with `with_keywords=<id>`);
     * the counts beside each entry are "films / shows" and the chosen ids are the well-used tags, not near-unused
     * variants (e.g. "london, england" 212 rather than the near-empty "london" 382039, "dystopia" rather than "dystopian").
     *
     * Judgement calls (see PLAN.md §5g): "gangster(s)" moved here from the Crime genre phrases (a word never maps to both
     * a genre and a theme); "musical(s)" stays a genre because the Music genre has no TV id but the keyword works for both,
     * and the genre behaviour pre-dates themes, so it is deliberately not a theme; "football" means soccer (UK) and
     * "american football"/"nfl" is its own theme; "cop"/"cops" (not "cop show") map to Police procedural so that "cop show"
     * still gets its TV restriction from the media word "show".
     */
    val themes: List<ThemeTerm>

    /** Normalised phrase to the theme it means. Longer phrases are matched before shorter ones by the parser. */
    val themePhrases: Map<String, ThemeTerm>

    init {
        val list = ArrayList<ThemeTerm>()
        val phrases = LinkedHashMap<String, ThemeTerm>()
        fun theme(name: String, ids: List<Int>, vararg words: String) {
            val t = ThemeTerm(name, ids)
            list += t
            words.forEach { check(phrases.put(it, t) == null) { "duplicate theme phrase: $it" } }
        }
        // ---- mandated by Kev (orchestrator-verified) ----
        theme("Dystopia", listOf(4565), "dystopia", "dystopian", "dystopias", "bleak future") // dystopia 1032/146 ("dystopian" 6/0)
        theme("Superhero", listOf(9715, 33637), "superhero", "superheroes", "super hero", "super heroes", "superpower", "superpowers", "super power", "super powers", "super beings", "super being", "overpowered") // superhero 1245/672 | super power 594/435
        theme("Cyberpunk", listOf(12190), "cyberpunk") // 290/74
        theme("Zombie", listOf(12377), "zombie", "zombies", "undead") // 1550/110
        theme("Heist", listOf(10051), "heist", "heists", "robbery", "robberies") // 579/46
        theme("Time travel", listOf(4379), "time travel", "time traveling", "time travelling", "time traveller", "time travellers") // 1170/606
        theme("Post-apocalyptic", listOf(4458), "apocalypse", "apocalyptic", "post apocalyptic", "post apocalypse", "end of the world") // post-apocalyptic future 715/152
        theme("Alien", listOf(9951), "alien", "aliens", "alien invasion", "extraterrestrial", "extraterrestrials") // 1329/368
        theme("London", listOf(212), "london") // "london, england" 1115/353 (bare "london" 382039 is 14 films)
        theme("Manchester", listOf(208548, 298625), "manchester") // manchester 62/38 | manchester, england 17/13
        theme("Glasgow", listOf(892, 348571), "glasgow") // glasgow, scotland 57/17 | glasgow 9/1
        theme("Edinburgh", listOf(3130), "edinburgh") // edinburgh, scotland 47/13
        theme("Liverpool", listOf(6344, 345400), "liverpool") // liverpool, england 96/28 | liverpool 12/1
        theme("Birmingham", listOf(210590), "birmingham") // 31/13
        theme("Gritty", listOf(286125, 207268, 155451), "gritty", "dark", "bleak") // gritty 109/40 | neo-noir 720/58 | british noir 112/2
        theme("Spy", listOf(470, 5265, 4289, 14555, 190904), "spy", "spies", "espionage", "secret agent", "secret agents", "mi6", "mi5") // spy 847/213 | espionage 386/82 | secret agent 274/89 | mi6 27/11 | mi5 13/15
        theme("Gangster", listOf(3149, 10291), "gangster", "gangsters", "gangland", "mob", "mobster", "mobsters") // gangster 1174/98 | organized crime 328/154
        theme("Undercover", listOf(1568), "undercover") // 249/103
        theme("Police procedural", listOf(268067), "police procedural", "police procedurals", "procedural", "procedurals", "cop", "cops") // 32/170
        // ---- everyday themes ----
        theme("Revenge", listOf(9748), "revenge") // 2878/996
        theme("Survival", listOf(10349), "survival") // 854/335
        theme("Coming of age", listOf(10683), "coming of age") // 4186/646
        theme("High school", listOf(6270), "high school", "highschool") // 1638/1033
        theme("Road trip", listOf(7312, 240484), "road trip", "road trips", "roadtrip") // road trip 1013/117 | roadtrip 99/26
        theme("Sports", listOf(6075), "sport", "sports") // 3661/798
        theme("Boxing", listOf(209476), "boxing", "boxer") // 662/64
        theme("American football", listOf(579), "american football", "gridiron", "nfl") // 360/100
        theme("Football", listOf(13042), "football", "footballer", "footballers", "soccer") // football (soccer) 1379/326; UK "football" is soccer
        theme("Dinosaurs", listOf(12616, 381574), "dinosaur", "dinosaurs") // dinosaur 506/123 | dinosaurs 7/1
        theme("Dragons", listOf(12554), "dragon", "dragons") // 253/99
        theme("Magic", listOf(2343), "magic", "magical") // 1047/782
        theme("Witches", listOf(616), "witch", "witches") // 698/133
        theme("Wizards", listOf(177912), "wizard", "wizards") // 152/50
        theme("Vampires", listOf(3133), "vampire", "vampires") // 1535/239
        theme("Werewolves", listOf(12564), "werewolf", "werewolves") // 446/55
        theme("Ghosts", listOf(162846), "ghost", "ghosts") // 1916/302
        theme("Haunted house", listOf(3358), "haunted house", "haunted houses") // 625/52
        theme("Pirates", listOf(12988), "pirate", "pirates") // 383/54
        theme("Space", listOf(9882), "space", "outer space") // 734/247
        theme("Robots", listOf(14544), "robot", "robots") // 603/229
        theme("AI", listOf(310), "ai", "a i", "artificial intelligence") // artificial intelligence (a.i.) 736/174
        theme("Monsters", listOf(1299, 289108), "monster", "monsters") // monster 1271/309 | monsters 74/52
        theme("Treasure hunt", listOf(6956, 215470), "treasure hunt", "treasure hunts", "treasure hunting", "treasure hunter", "treasure hunters") // 178/46 | treasure hunter 38/12
        theme("Martial arts", listOf(779), "martial arts", "martial art") // 2077/538
        theme("Kung fu", listOf(780), "kung fu", "kungfu") // 593/31
        theme("Samurai", listOf(1462), "samurai") // 504/108
        theme("Christmas", listOf(207317), "christmas", "xmas") // 3367/142
        theme("Halloween", listOf(3335), "halloween") // 1017/83
        theme("Based on a true story", listOf(9672), "true story", "true stories", "based on a true story", "based on true story", "based on true events", "true events") // 3393/478
        theme("Based on a book", listOf(818), "based on a novel", "based on a book", "based on novel", "based on book", "book adaptation", "novel adaptation") // based on novel or book 8511/3534
        theme("Talking animals", listOf(267848, 167625), "talking animal", "talking animals") // 117/37 | talking to animals 26/9
        theme("Dogs", listOf(15162), "dog", "dogs", "puppy", "puppies") // 1384/192
        theme("Cats", listOf(977), "cat", "cats", "kitten", "kittens") // 744/141
        theme("Princess", listOf(7376), "princess", "princesses") // 339/67
        theme("Fairy tale", listOf(3205), "fairy tale", "fairy tales", "fairytale", "fairytales") // 1000/83
        theme("Mermaids", listOf(5938), "mermaid", "mermaids") // 178/40
        theme("Time loop", listOf(10854), "time loop", "time loops") // 246/52
        theme("Body swap", listOf(33721), "body swap", "body swaps", "body swapping") // 108/79
        theme("Parody", listOf(9755, 11931), "parody", "parodies", "spoof", "spoofs") // parody 952/405 | spoof 401/58
        theme("Dark comedy", listOf(10123), "dark comedy", "dark comedies", "black comedy", "black comedies") // 2048/353
        theme("Conspiracy", listOf(10410, 11208), "conspiracy", "conspiracies", "conspiracy theory") // conspiracy 582/280 | conspiracy theory 213/30
        theme("Serial killer", listOf(10714), "serial killer", "serial killers") // 1846/327
        theme("Detective", listOf(703), "detective", "detectives") // 1267/720
        theme("Mafia", listOf(10391), "mafia") // 629/178
        theme("Courtroom", listOf(33519, 214780), "courtroom", "court room", "courtroom drama") // courtroom 279/102 | courtroom drama 157/51
        theme("Hospital", listOf(11612), "hospital", "hospitals") // 737/318
        theme("Natural disaster", listOf(5096, 10617), "natural disaster", "natural disasters", "disaster", "disasters") // natural disaster 191/37 | disaster 330/80
        theme("Shipwreck", listOf(2580, 193083), "shipwreck", "shipwrecked") // shipwreck 186/21 | shipwrecked 39/5
        theme("Prison break", listOf(9777, 169271), "prison break", "prison escape", "jailbreak", "jail break") // prison escape 203/17 | escaped prisoner 69/19
        theme("Cold war", listOf(2106), "cold war") // 379/99
        theme("World War II", listOf(1956), "world war ii", "world war 2", "world war two", "ww2", "wwii", "second world war") // 3218/487
        theme("World War I", listOf(2504), "world war i", "world war 1", "world war one", "ww1", "wwi", "first world war") // 611/121
        theme("Medieval", listOf(161257), "medieval", "middle ages") // 214/64
        theme("Psychological thriller", listOf(12565), "psychological thriller", "psychological thrillers") // 1203/186
        theme("Assassin", listOf(782, 2708), "assassin", "assassins", "hitman", "hitmen") // assassin 436/63 | hitman 524/38
        theme("Friendship", listOf(6054), "friendship", "friendships") // 2884/1618
        theme("New York", listOf(242), "new york", "new york city", "nyc") // new york city 2215/390
        themes = list
        themePhrases = phrases
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

    /** Longest multi-word phrase in [genrePhrases] and [themePhrases], so the parser knows how far to look ahead. */
    val maxPhraseWords: Int = (genrePhrases.keys + themePhrases.keys).maxOf { it.split(' ').size }
}
