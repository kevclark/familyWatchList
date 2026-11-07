package org.seg7.familywatchlist.data.repository

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import org.seg7.familywatchlist.common.AppClock
import org.seg7.familywatchlist.data.local.dao.TitleAttributeDao
import org.seg7.familywatchlist.data.local.dao.TitleDao
import org.seg7.familywatchlist.data.local.entity.AttrType
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.TitleEntity
import org.seg7.familywatchlist.data.recommend.FamilyBlend
import org.seg7.familywatchlist.data.remote.TmdbApi
import org.seg7.familywatchlist.data.remote.TmdbApi.Companion.REGION_GB
import org.seg7.familywatchlist.data.remote.dto.MediaSummaryDto
import org.seg7.familywatchlist.data.remote.dto.PersonDto
import org.seg7.familywatchlist.data.search.AgeFilter
import org.seg7.familywatchlist.data.search.AgeVerdict
import org.seg7.familywatchlist.data.search.MediaWord
import org.seg7.familywatchlist.data.search.ParsedQuery
import org.seg7.familywatchlist.data.search.QueryParser
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

/** One removable interpretation chip. [key] is what goes into [ParsedQuery.without] when it is removed. */
data class SearchChip(val key: String, val label: String)

data class Interpretation(val chips: List<SearchChip>, val ignored: List<String>)

/**
 * The filters for one media type. Genre, cast and keyword groups are ANDed; the ids inside one keyword
 * group are ORed (one typed word maps to its exact keyword plus "<word> ..." variants).
 */
data class MediaSpec(val genreIds: List<Int>, val personIds: List<Int>, val keywordGroups: List<List<Int>>)

sealed interface SearchPlan {
    /** Nothing structured was understood: run the existing title search on [text]. */
    data class Titles(val text: String) : SearchPlan

    /** A null spec means that media type is not searched. */
    data class Structured(val interpretation: Interpretation, val movie: MediaSpec?, val tv: MediaSpec?) : SearchPlan
}

/** One page of structured results. [unratedKeys] uses the same `"MEDIATYPE-tmdbId"` format as the search screen's listed keys. */
data class ResultPage(
    val titles: List<TitleEntity>,
    val unratedKeys: Set<String>,
    val hasMore: Boolean,
    /** TMDB pages this load has fetched so far; the caller's next start page is `page + pagesConsumed` (PLAN.md §5g). */
    val pagesConsumed: Int = 1,
)

/**
 * PLAN.md §5f: descriptive search. [plan] resolves the parser's person and keyword candidates
 * against TMDB (cached in memory for the process), [run] executes the plan on `discover` (movies)
 * and `discover`/`person tv_credits` (TV), then pushes every candidate through the same
 * [AvailabilityGate] the title search uses and the age rule in [AgeFilter].
 *
 * Only TMDB endpoints are called. No schema change: lookups are cached in memory, titles go through
 * the existing stub/detail path.
 */
class DescriptiveSearchRepository(
    private val titleDao: TitleDao,
    private val titleAttributeDao: TitleAttributeDao,
    private val api: TmdbApi,
    private val clock: AppClock,
    private val availabilityGate: AvailabilityGate,
    private val providerRepository: ProviderRepository,
) {
    private val personCache = ConcurrentHashMap<String, Optional<PersonDto>>()
    private val keywordCache = ConcurrentHashMap<String, Optional<List<Int>>>()
    private val tvCreditsCache = ConcurrentHashMap<Int, List<MediaSummaryDto>>()

    /**
     * Resolves [parsed] into a plan. With [allowTitleFallback] (the user has not removed any chip)
     * a query where nothing structured survives resolution becomes a [SearchPlan.Titles] plan.
     */
    suspend fun plan(parsed: ParsedQuery, allowTitleFallback: Boolean): SearchPlan {
        // Nothing worth a lookup (a bare title, a lone media word): no TMDB calls at all.
        if (allowTitleFallback && !parsed.hasStructure) return SearchPlan.Titles(parsed.raw)
        val chips = ArrayList<SearchChip>()
        val ignored = ArrayList<String>()
        parsed.media.forEach { chips += SearchChip(ParsedQuery.mediaKey(it), if (it == MediaWord.MOVIE) "Movies" else "TV") }
        parsed.genres.forEach { chips += SearchChip(it.key, it.name) }
        // PLAN.md §5g: a theme is one fixed OR group of keyword ids; no lookup, and it ANDs with everything else.
        parsed.themes.forEach { chips += SearchChip(it.key, it.name) }

        val personIds = ArrayList<Int>()
        val keywordIds = ArrayList<List<Int>>()
        parsed.themes.forEach { keywordIds += it.keywordIds }
        val descriptive = parsed.genres.isNotEmpty() || parsed.media.isNotEmpty() || parsed.themes.isNotEmpty()

        parsed.candidates.forEachIndexed { index, cand ->
            if (index >= MAX_CANDIDATE_LOOKUPS) {
                ignored += cand.text
                return@forEachIndexed
            }
            var resolved = false
            if (cand.personFirst) {
                person(cand.text)?.let {
                    personIds += it.id
                    chips += SearchChip(cand.key, it.name)
                    resolved = true
                }
            }
            if (!resolved) {
                keyword(cand.text)?.let {
                    keywordIds += it
                    chips += SearchChip(cand.key, cand.text)
                    resolved = true
                }
            }
            if (!resolved && cand.words.size > 1 && descriptive) {
                cand.words.filter { "kw:$it" !in parsed.removed }.forEach { word ->
                    val kw = keyword(word)
                    if (kw != null) {
                        keywordIds += kw
                        chips += SearchChip("kw:$word", word)
                        resolved = true
                    } else {
                        ignored += word
                    }
                }
            } else if (!resolved) {
                ignored += cand.text
            }
        }

        if (allowTitleFallback && parsed.genres.isEmpty() && personIds.isEmpty() && keywordIds.isEmpty()) {
            return SearchPlan.Titles(parsed.raw)
        }

        val scope = parsed.mediaTypes
        val movieGenres = parsed.genres.mapNotNull { it.movieId }
        val tvGenres = parsed.genres.mapNotNull { it.tvId }.distinct()
        // A genre with no equivalent is dropped from that side (PLAN.md §5f). If that drops every
        // requested genre the side is skipped: with no filter left it would be "all popular TV".
        val movie = MediaSpec(movieGenres, personIds.toList(), keywordIds.toList())
            .takeIf { MediaType.MOVIE in scope && !(parsed.genres.isNotEmpty() && movieGenres.isEmpty()) }
        val tv = MediaSpec(tvGenres, personIds.toList(), keywordIds.toList())
            .takeIf { MediaType.TV in scope && !(parsed.genres.isNotEmpty() && tvGenres.isEmpty()) }
        val nothingToSearch = chips.isEmpty()
        return SearchPlan.Structured(
            Interpretation(chips, ignored),
            movie.takeUnless { nothingToSearch },
            tv.takeUnless { nothingToSearch },
        )
    }

    /**
     * Runs one load of [plan], starting at TMDB page [page] (PLAN.md §5g). A load keeps fetching further
     * pages until [TARGET_TITLES] titles have passed the local checks, [MAX_PAGES_PER_LOAD] pages have been
     * fetched, or TMDB has no more pages. Titles whose `"MEDIATYPE-tmdbId"` key is in [exclude] (already on
     * screen) are skipped before any check. Emissions are progressive and cumulative for this load; the last
     * one is settled and carries [ResultPage.pagesConsumed] and the final [ResultPage.hasMore].
     * [cap] is the BBFC cap string or null for Any.
     */
    fun run(
        plan: SearchPlan.Structured,
        page: Int,
        cap: String?,
        region: String = REGION_GB,
        exclude: Set<String> = emptySet(),
    ): Flow<ResultPage> = channelFlow {
        val providerIds = providerRepository.getSubscribedIds()
        if (providerIds.isEmpty() || (plan.movie == null && plan.tv == null)) {
            send(ResultPage(emptyList(), emptySet(), hasMore = false, pagesConsumed = 0))
            return@channelFlow
        }
        val providers = providerIds.sorted().joinToString("|")
        val now = clock.nowMillis()

        // TV discover has no with_cast: intersect the people's credits once, filter locally, page locally.
        val localTv: List<MediaSummaryDto>? = plan.tv?.takeIf { it.personIds.isNotEmpty() }?.let { spec ->
            val credits = spec.personIds.map { tvCredits(it) }
            val common = credits.drop(1).fold(credits.first().map { it.id }.toSet()) { acc, list -> acc intersect list.map { it.id }.toSet() }
            credits.first()
                .filter { it.id in common }
                .filter { dto -> spec.genreIds.all { it in dto.genreIds } }
                .filter { (it.voteCount ?: 0) >= VOTE_FLOOR }
                .filterNot { it.character?.contains("self", ignoreCase = true) == true }
                .distinctBy { it.id }
                .sortedWith(compareByDescending<MediaSummaryDto> { it.popularity ?: 0.0 }.thenBy { it.id })
        }

        var movieMore = plan.movie != null
        var tvMore = plan.tv != null
        var consumed = 0
        val accepted = ArrayList<TitleEntity>()
        val unrated = HashSet<String>()
        val seen = HashSet(exclude)
        val sendMutex = Mutex()
        val limit = Semaphore(MAX_CONCURRENT_CHECKS)

        while (consumed < MAX_PAGES_PER_LOAD && accepted.size < TARGET_TITLES && (movieMore || tvMore)) {
            val current = page + consumed
            consumed++
            val candidates = ArrayList<Triple<MediaSummaryDto, MediaType, Boolean>>() // dto, type, tvKeywordCheck

            if (movieMore) plan.movie?.let { spec ->
                val response = api.discoverMovies(
                    watchRegion = region,
                    withWatchProviders = providers,
                    monetizationTypes = MONETIZATION,
                    page = current,
                    withGenres = spec.genreIds.csv(),
                    withCast = spec.personIds.csv(),
                    withKeywords = spec.keywordGroups.keywordParam(),
                    voteCountGte = VOTE_FLOOR,
                    certificationCountry = AgeFilter.optionForCap(cap).tmdbCertification?.let { region },
                    certificationLte = AgeFilter.optionForCap(cap).tmdbCertification,
                )
                response.results.forEach { candidates += Triple(it, MediaType.MOVIE, false) }
                movieMore = current < response.totalPages
            }
            if (tvMore) plan.tv?.let { spec ->
                if (localTv == null) {
                    val response = api.discoverTv(
                        watchRegion = region,
                        withWatchProviders = providers,
                        monetizationTypes = MONETIZATION,
                        page = current,
                        withGenres = spec.genreIds.csv(),
                        withKeywords = spec.keywordGroups.keywordParam(),
                        voteCountGte = VOTE_FLOOR,
                    )
                    response.results.forEach { candidates += Triple(it, MediaType.TV, false) }
                    tvMore = current < response.totalPages
                } else {
                    val from = (current - 1) * PAGE_SIZE
                    localTv.drop(from).take(PAGE_SIZE).forEach { candidates += Triple(it, MediaType.TV, spec.keywordGroups.isNotEmpty()) }
                    tvMore = from + PAGE_SIZE < localTv.size
                }
            }

            val ordered = candidates
                .filter { (dto, _, _) -> !(dto.title ?: dto.name).isNullOrBlank() }
                .filter { (dto, type, _) -> seen.add(listedKeyOf(dto.id, type)) }
                .sortedWith(compareByDescending<Triple<MediaSummaryDto, MediaType, Boolean>> { it.first.popularity ?: 0.0 }.thenBy { it.first.id })
            titleDao.persistStubs(ordered.map { it.first to it.second }, now)
            val stored = MediaType.entries.associateWith { type ->
                val ids = ordered.filter { it.second == type }.map { it.first.id }
                if (ids.isEmpty()) emptyMap() else titleDao.getByIds(ids, type).associateBy { it.tmdbId }
            }

            val slots = arrayOfNulls<TitleEntity>(ordered.size)
            coroutineScope {
                ordered.mapIndexed { index, (dto, type, keywordCheck) ->
                    launch {
                        val candidate = stored.getValue(type)[dto.id] ?: return@launch
                        limit.withPermit {
                            if (!availabilityGate.isAvailableOnSubscribedProvider(dto.id, type, region)) return@withPermit
                            // Cache-only: the gate call above just ensured freshness.
                            if (availabilityGate.isPaidOnlyCached(dto.id, type)) return@withPermit
                            val certification = titleDao.get(dto.id, type)?.certification ?: candidate.certification
                            val verdict = AgeFilter.evaluate(certification, cap)
                            if (verdict == AgeVerdict.HIDE) return@withPermit
                            if (keywordCheck) {
                                val have = titleAttributeDao.getForTitle(dto.id, type).filter { it.attrType == AttrType.KEYWORD }.map { it.attrId }.toSet()
                                if (!plan.tv!!.keywordGroups.all { group -> group.any { it in have } }) return@withPermit
                            }
                            sendMutex.withLock {
                                slots[index] = titleDao.get(dto.id, type) ?: candidate
                                if (verdict == AgeVerdict.SHOW_UNRATED) unrated += listedKeyOf(dto.id, type)
                                send(ResultPage(accepted + slots.filterNotNull(), unrated.toSet(), movieMore || tvMore, consumed))
                            }
                        }
                    }
                }.joinAll()
            }
            accepted += slots.filterNotNull()
        }
        sendMutex.withLock { send(ResultPage(accepted.toList(), unrated.toSet(), movieMore || tvMore, consumed)) }
    }

    private fun listedKeyOf(id: Int, type: MediaType) = "$type-$id"

    private suspend fun tvCredits(personId: Int): List<MediaSummaryDto> =
        tvCreditsCache[personId] ?: api.personTvCredits(personId).cast.also { tvCreditsCache[personId] = it }

    /** Top match by popularity among near-exact name matches; cached including misses. */
    private suspend fun person(text: String): PersonDto? {
        val key = QueryParser.tokenize(text).joinToString(" ")
        if (key.isEmpty()) return null
        personCache[key]?.let { return it.orElse(null) }
        val response = lookupOrNull { api.searchPerson(text).results } ?: return null
        return personCache.getOrPut(key) {
            val wanted = key.split(' ')
            val match = response
                .filter { p ->
                    val nameTokens = QueryParser.tokenize(p.name)
                    nameTokens == wanted || (wanted.size >= 2 && nameTokens.containsAll(wanted))
                }
                .maxByOrNull { it.popularity ?: 0.0 }
            Optional.ofNullable(match)
        }.orElse(null)
    }

    /**
     * The OR group for a typed word: near-exact name matches plus keywords whose name starts with
     * "<word> ", in TMDB's order, capped at [MAX_KEYWORD_GROUP]. TMDB's bare "slapstick" keyword is
     * barely used while "slapstick comedy" carries the real titles, so exact alone finds nothing.
     * Null when nothing matches. Cached including misses.
     */
    private suspend fun keyword(text: String): List<Int>? {
        val key = QueryParser.tokenize(text).joinToString(" ")
        if (key.isEmpty()) return null
        keywordCache[key]?.let { return it.orElse(null) }
        val response = lookupOrNull { api.searchKeyword(text).results } ?: return null
        return keywordCache.getOrPut(key) {
            val ids = response.filter {
                val name = QueryParser.tokenize(it.name).joinToString(" ")
                nearExact(key, name) || name.startsWith("$key ")
            }.map { it.id }.distinct().take(MAX_KEYWORD_GROUP)
            Optional.ofNullable(ids.takeIf { it.isNotEmpty() })
        }.orElse(null)
    }

    /**
     * A failed person/keyword lookup must not break a search that may really be a title query
     * ("inception" is now tried as a keyword first): treat it as "no match" and don't cache it.
     */
    private suspend fun <T> lookupOrNull(block: suspend () -> T): T? =
        try {
            block()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    private fun nearExact(a: String, b: String): Boolean =
        a == b || a.replace(" ", "") == b.replace(" ", "") || singular(a) == singular(b)

    private fun singular(s: String): String = when {
        s.endsWith("ies") && s.length > 4 -> s.dropLast(3) + "y"
        s.endsWith("es") && s.length > 4 -> s.dropLast(2)
        s.endsWith("s") && s.length > 3 -> s.dropLast(1)
        else -> s
    }

    /** Groups ANDed with `,`, ids within a group ORed with `|`. */
    private fun List<List<Int>>.keywordParam(): String? =
        takeIf { it.isNotEmpty() }?.joinToString(",") { it.joinToString("|") }

    private fun List<Int>.csv(): String? = takeIf { it.isNotEmpty() }?.joinToString(",")

    companion object {
        /** Minimum TMDB vote count so unrated junk does not surface. */
        const val VOTE_FLOOR = 50
        const val PAGE_SIZE = 20

        /** PLAN.md §5g: one load (first search or a More tap) aims for this many titles that pass the local checks... */
        const val TARGET_TITLES = 20

        /** ...but fetches at most this many TMDB pages (or local PAGE_SIZE chunks for TV-with-person). */
        const val MAX_PAGES_PER_LOAD = 3
        /**
         * Included-with-subscription kinds only, like the existing discover calls. Browsing by genre or
         * actor never nudges towards rent/buy (PLAN.md, Kev 2026-08-22: paid options appear only when
         * someone explicitly searches for a title), so paid-only titles are also dropped after the gate.
         */
        const val MONETIZATION = "flatrate|free"
        private const val MAX_CANDIDATE_LOOKUPS = 4
        private const val MAX_KEYWORD_GROUP = 6
        private const val MAX_CONCURRENT_CHECKS = 5
    }
}

/** Writes search hits as stubs without overwriting titles that already have full detail (same rule as [SearchRepository]). */
private suspend fun TitleDao.persistStubs(results: List<Pair<MediaSummaryDto, MediaType>>, now: Long) {
    MediaType.entries.forEach { type ->
        val forType = results.filter { it.second == type }.map { it.first }
        if (forType.isEmpty()) return@forEach
        val detailed = getByIds(forType.map { it.id }, type).filter { it.runtimeMin != null || it.certification != null }.map { it.tmdbId }.toSet()
        val stubs = forType.filter { it.id !in detailed }.map { it.toStubTitleEntity(type, now) }
        if (stubs.isNotEmpty()) upsertAll(stubs)
    }
}
