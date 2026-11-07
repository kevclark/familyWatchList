package org.seg7.familywatchlist.data.repository

import org.seg7.familywatchlist.data.local.dao.TitleDao
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.remote.dto.MediaSummaryDto
import org.seg7.familywatchlist.data.search.AgeFilter
import org.seg7.familywatchlist.data.search.AgeVerdict

/**
 * The checks every "browse" surface applies to a TMDB candidate before showing it (structured search,
 * PLAN.md §5f/§5g, and "More like this", §5h): on a subscribed provider in [region], not rent/buy-only
 * (Kev: browsing never nudges towards paid options), and inside the age [cap] with the shared unrated rule.
 *
 * Returns the [AgeVerdict] (SHOW or SHOW_UNRATED) when the title passes, or null when it must be hidden.
 * The availability call refreshes the title's detail row if stale, so the paid-only and certification
 * reads that follow are cache-only. [fallbackCertification] is used if the row has vanished.
 */
internal suspend fun passesBrowseGate(
    gate: AvailabilityGate,
    titleDao: TitleDao,
    tmdbId: Int,
    mediaType: MediaType,
    region: String,
    cap: String?,
    fallbackCertification: String?,
): AgeVerdict? {
    if (!gate.isAvailableOnSubscribedProvider(tmdbId, mediaType, region)) return null
    if (gate.isPaidOnlyCached(tmdbId, mediaType)) return null
    val certification = titleDao.get(tmdbId, mediaType)?.certification ?: fallbackCertification
    val verdict = AgeFilter.evaluate(certification, cap)
    return verdict.takeUnless { it == AgeVerdict.HIDE }
}

/** Writes TMDB list hits as stubs without overwriting titles that already have full detail (same rule as [SearchRepository]). */
internal suspend fun TitleDao.persistStubs(results: List<Pair<MediaSummaryDto, MediaType>>, now: Long) {
    MediaType.entries.forEach { type ->
        val forType = results.filter { it.second == type }.map { it.first }
        if (forType.isEmpty()) return@forEach
        val detailed = getByIds(forType.map { it.id }, type).filter { it.runtimeMin != null || it.certification != null }.map { it.tmdbId }.toSet()
        val stubs = forType.filter { it.id !in detailed }.map { it.toStubTitleEntity(type, now) }
        if (stubs.isNotEmpty()) upsertAll(stubs)
    }
}
