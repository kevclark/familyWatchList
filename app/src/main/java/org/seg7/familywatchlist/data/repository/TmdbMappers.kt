package org.seg7.familywatchlist.data.repository

import org.seg7.familywatchlist.data.local.entity.AttrType
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.ProviderAvailabilityEntity
import org.seg7.familywatchlist.data.local.entity.ProviderKind
import org.seg7.familywatchlist.data.local.entity.ReviewEntity
import org.seg7.familywatchlist.data.local.entity.TitleAttributeEntity
import org.seg7.familywatchlist.data.local.entity.TitleEntity
import org.seg7.familywatchlist.data.remote.TmdbApi
import org.seg7.familywatchlist.data.remote.dto.MediaSummaryDto
import org.seg7.familywatchlist.data.remote.dto.MovieDetailDto
import org.seg7.familywatchlist.data.remote.dto.PagedResponseDto
import org.seg7.familywatchlist.data.remote.dto.ReviewDto
import org.seg7.familywatchlist.data.remote.dto.TvDetailDto
import org.seg7.familywatchlist.data.remote.dto.WatchProvidersResponseDto

private const val GB = "GB"
private const val MAX_CAST = 10

fun MovieDetailDto.toTitleEntity(fetchedAt: Long): TitleEntity = TitleEntity(
    tmdbId = id,
    mediaType = MediaType.MOVIE,
    title = title,
    year = releaseDate.yearOrNull(),
    posterPath = posterPath,
    backdropPath = backdropPath,
    overview = overview,
    runtimeMin = runtime,
    certification = releaseDates?.gbCertification(),
    voteAverage = voteAverage,
    voteCount = voteCount,
    popularity = popularity,
    trailerKey = videos.youTubeTrailerKey(),
    fetchedAt = fetchedAt,
    imdbId = externalIds?.imdbId,
)

fun TvDetailDto.toTitleEntity(fetchedAt: Long): TitleEntity = TitleEntity(
    tmdbId = id,
    mediaType = MediaType.TV,
    title = name,
    year = firstAirDate.yearOrNull(),
    posterPath = posterPath,
    backdropPath = backdropPath,
    overview = overview,
    runtimeMin = episodeRunTime.firstOrNull(),
    certification = contentRatings?.results?.firstOrNull { it.iso3166_1 == GB }?.rating?.takeIf { it.isNotBlank() },
    voteAverage = voteAverage,
    voteCount = voteCount,
    popularity = popularity,
    trailerKey = videos.youTubeTrailerKey(),
    fetchedAt = fetchedAt,
    imdbId = externalIds?.imdbId,
)

fun MovieDetailDto.toAttributes(): List<TitleAttributeEntity> = buildList {
    genres.forEach { add(TitleAttributeEntity(id, MediaType.MOVIE, AttrType.GENRE, it.id, it.name, null)) }
    credits?.cast.orEmpty().sortedBy { it.order }.take(MAX_CAST).forEach {
        add(TitleAttributeEntity(id, MediaType.MOVIE, AttrType.CAST, it.id, it.name, it.order))
    }
    credits?.crew.orEmpty().filter { it.job == "Director" }.forEach {
        add(TitleAttributeEntity(id, MediaType.MOVIE, AttrType.CREW, it.id, it.name, null))
    }
    keywords?.keywords.orEmpty().forEach {
        add(TitleAttributeEntity(id, MediaType.MOVIE, AttrType.KEYWORD, it.id, it.name, null))
    }
}

fun TvDetailDto.toAttributes(): List<TitleAttributeEntity> = buildList {
    genres.forEach { add(TitleAttributeEntity(id, MediaType.TV, AttrType.GENRE, it.id, it.name, null)) }
    credits?.cast.orEmpty().sortedBy { it.order }.take(MAX_CAST).forEach {
        add(TitleAttributeEntity(id, MediaType.TV, AttrType.CAST, it.id, it.name, it.order))
    }
    createdBy.forEach {
        add(TitleAttributeEntity(id, MediaType.TV, AttrType.CREW, it.id, it.name, null))
    }
    keywords?.results.orEmpty().forEach {
        add(TitleAttributeEntity(id, MediaType.TV, AttrType.KEYWORD, it.id, it.name, null))
    }
}

/**
 * PLAN.md §7 M2f: [region] picks which country's key to pull out of the multi-country
 * `watch/providers` payload — the TMDB detail call itself has no `watch_region` parameter (it
 * returns every country TMDB has data for in one response), so region only matters here, at
 * extraction time. Defaults to [org.seg7.familywatchlist.data.remote.TmdbApi.REGION_GB] so the
 * many pre-existing call sites (tests, and any caller that hasn't been threaded through yet)
 * keep their original GB behaviour unless they deliberately pass something else.
 */
fun MovieDetailDto.toAvailability(fetchedAt: Long, region: String = TmdbApi.REGION_GB): List<ProviderAvailabilityEntity> =
    watchProviders.toAvailability(id, MediaType.MOVIE, fetchedAt, region)

fun TvDetailDto.toAvailability(fetchedAt: Long, region: String = TmdbApi.REGION_GB): List<ProviderAvailabilityEntity> =
    watchProviders.toAvailability(id, MediaType.TV, fetchedAt, region)

private fun WatchProvidersResponseDto?.toAvailability(
    tmdbId: Int,
    mediaType: MediaType,
    fetchedAt: Long,
    region: String,
): List<ProviderAvailabilityEntity> {
    val forRegion = this?.results?.get(region) ?: return emptyList()
    return buildList {
        forRegion.flatrate.forEach { add(ProviderAvailabilityEntity(tmdbId, mediaType, it.providerId, ProviderKind.FLATRATE, fetchedAt)) }
        forRegion.free.forEach { add(ProviderAvailabilityEntity(tmdbId, mediaType, it.providerId, ProviderKind.FREE, fetchedAt)) }
        // PLAN.md §5's "Paid (rent/buy) titles" addendum (M6, Kev 2026-08-22): the same per-title
        // `/watch/providers` response already carries `rent`/`buy` alongside `flatrate`/`free` —
        // no new network call. These rows only ever widen [AvailabilityGate]'s Search/watchlist
        // check and badge rendering; [RecommendationRepository.gatherCandidatePool] never reads
        // this table at all (it filters candidates via `DiscoverRepository`'s
        // `with_watch_monetization_types = "flatrate|free"` query param instead, left untouched),
        // so persisting these rows cannot leak paid titles into Home/the recommender.
        forRegion.rent.forEach { add(ProviderAvailabilityEntity(tmdbId, mediaType, it.providerId, ProviderKind.RENT, fetchedAt)) }
        forRegion.buy.forEach { add(ProviderAvailabilityEntity(tmdbId, mediaType, it.providerId, ProviderKind.BUY, fetchedAt)) }
    }
}

/** PLAN.md §5c (M14): TMDB review snippets, free on the same detail call — reused by both media types. */
fun MovieDetailDto.toReviews(): List<ReviewEntity> = reviews.toReviews(id, MediaType.MOVIE)

fun TvDetailDto.toReviews(): List<ReviewEntity> = reviews.toReviews(id, MediaType.TV)

private fun PagedResponseDto<ReviewDto>?.toReviews(tmdbId: Int, mediaType: MediaType): List<ReviewEntity> =
    this?.results.orEmpty().map {
        ReviewEntity(
            tmdbId = tmdbId,
            mediaType = mediaType,
            reviewId = it.id,
            author = it.author,
            content = it.content,
            url = it.url,
            rating = it.authorDetails?.rating,
            createdAt = it.createdAt,
        )
    }

/** Discover/recommendations/search results only ever carry a summary — no runtime or certification. */
fun MediaSummaryDto.toStubTitleEntity(mediaType: MediaType, fetchedAt: Long): TitleEntity = TitleEntity(
    tmdbId = id,
    mediaType = mediaType,
    title = (title ?: name).orEmpty(),
    year = (releaseDate ?: firstAirDate).yearOrNull(),
    posterPath = posterPath,
    backdropPath = backdropPath,
    overview = overview,
    runtimeMin = null,
    certification = null,
    voteAverage = voteAverage,
    voteCount = voteCount,
    popularity = popularity,
    // Summary payloads carry no videos array; the key arrives with the first detail fetch.
    trailerKey = null,
    fetchedAt = fetchedAt,
)

private fun org.seg7.familywatchlist.data.remote.dto.ReleaseDatesDto.gbCertification(): String? =
    results.firstOrNull { it.iso3166_1 == GB }
        ?.releaseDates
        ?.firstOrNull { it.certification.isNotBlank() }
        ?.certification

/**
 * PLAN.md §1/§2: "trailer key -> Intent to YouTube. Filter to site == YouTube && type ==
 * Trailer." Official trailers win over fan-uploaded ones; a Teaser is accepted only when the
 * title has no trailer at all, since "no button" is worse than "a teaser".
 */
private fun org.seg7.familywatchlist.data.remote.dto.VideosDto?.youTubeTrailerKey(): String? {
    val youTube = this?.results.orEmpty().filter { it.site.equals("YouTube", ignoreCase = true) }
    val trailers = youTube.filter { it.type.equals("Trailer", ignoreCase = true) }
    val teasers = youTube.filter { it.type.equals("Teaser", ignoreCase = true) }
    return (trailers.firstOrNull { it.official } ?: trailers.firstOrNull()
        ?: teasers.firstOrNull { it.official } ?: teasers.firstOrNull())?.key
}

private fun String?.yearOrNull(): Int? = this?.take(4)?.toIntOrNull()
