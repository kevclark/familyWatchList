package org.seg7.familywatchlist.data.remote

import org.seg7.familywatchlist.data.remote.dto.ConfigurationDto
import org.seg7.familywatchlist.data.remote.dto.KeywordDto
import org.seg7.familywatchlist.data.remote.dto.MediaSummaryDto
import org.seg7.familywatchlist.data.remote.dto.MovieDetailDto
import org.seg7.familywatchlist.data.remote.dto.PagedResponseDto
import org.seg7.familywatchlist.data.remote.dto.PersonDto
import org.seg7.familywatchlist.data.remote.dto.PersonTvCreditsDto
import org.seg7.familywatchlist.data.remote.dto.ProviderListResponseDto
import org.seg7.familywatchlist.data.remote.dto.TvDetailDto
import org.seg7.familywatchlist.data.remote.dto.WatchProviderRegionsResponseDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** Endpoints per PLAN.md §3. Auth header and throttling are applied by OkHttp interceptors. */
interface TmdbApi {

    @GET("search/multi")
    suspend fun searchMulti(
        @Query("query") query: String,
        @Query("page") page: Int = 1,
    ): PagedResponseDto<MediaSummaryDto>

    @GET("movie/{id}")
    suspend fun movieDetail(
        @Path("id") id: Int,
        @Query("append_to_response") appendToResponse: String = APPEND_MOVIE,
    ): MovieDetailDto

    @GET("tv/{id}")
    suspend fun tvDetail(
        @Path("id") id: Int,
        @Query("append_to_response") appendToResponse: String = APPEND_TV,
    ): TvDetailDto

    /**
     * The trailing nullable params are PLAN.md §5f's structured search filters (null = omitted from
     * the URL, so the recommender's existing calls are unchanged). Genres/cast/keywords use `,` for
     * AND; providers use `|` for OR.
     */
    @GET("discover/movie")
    suspend fun discoverMovies(
        @Query("watch_region") watchRegion: String = REGION_GB,
        @Query("with_watch_providers") withWatchProviders: String? = null,
        @Query("with_watch_monetization_types") monetizationTypes: String = "flatrate|free",
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("page") page: Int = 1,
        @Query("with_genres") withGenres: String? = null,
        @Query("with_cast") withCast: String? = null,
        @Query("with_keywords") withKeywords: String? = null,
        @Query("vote_count.gte") voteCountGte: Int? = null,
        @Query("certification_country") certificationCountry: String? = null,
        @Query("certification.lte") certificationLte: String? = null,
    ): PagedResponseDto<MediaSummaryDto>

    /** TV discover has no cast or certification filter (PLAN.md §5f); people come from [personTvCredits]. */
    @GET("discover/tv")
    suspend fun discoverTv(
        @Query("watch_region") watchRegion: String = REGION_GB,
        @Query("with_watch_providers") withWatchProviders: String? = null,
        @Query("with_watch_monetization_types") monetizationTypes: String = "flatrate|free",
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("page") page: Int = 1,
        @Query("with_genres") withGenres: String? = null,
        @Query("with_keywords") withKeywords: String? = null,
        @Query("vote_count.gte") voteCountGte: Int? = null,
    ): PagedResponseDto<MediaSummaryDto>

    @GET("search/person")
    suspend fun searchPerson(
        @Query("query") query: String,
        @Query("page") page: Int = 1,
    ): PagedResponseDto<PersonDto>

    @GET("search/keyword")
    suspend fun searchKeyword(
        @Query("query") query: String,
        @Query("page") page: Int = 1,
    ): PagedResponseDto<KeywordDto>

    @GET("person/{id}/tv_credits")
    suspend fun personTvCredits(@Path("id") id: Int): PersonTvCreditsDto

    @GET("movie/{id}/recommendations")
    suspend fun movieRecommendations(
        @Path("id") id: Int,
        @Query("page") page: Int = 1,
    ): PagedResponseDto<MediaSummaryDto>

    @GET("tv/{id}/recommendations")
    suspend fun tvRecommendations(
        @Path("id") id: Int,
        @Query("page") page: Int = 1,
    ): PagedResponseDto<MediaSummaryDto>

    @GET("watch/providers/movie")
    suspend fun movieProviders(
        @Query("watch_region") watchRegion: String = REGION_GB,
    ): ProviderListResponseDto

    @GET("watch/providers/tv")
    suspend fun tvProviders(
        @Query("watch_region") watchRegion: String = REGION_GB,
    ): ProviderListResponseDto

    @GET("configuration")
    suspend fun configuration(): ConfigurationDto

    /**
     * PLAN.md §7 M2f: the live, always-current list of regions TMDB's watch-provider data
     * supports — sourced from TMDB itself rather than a hand-maintained country list, so
     * Settings' region picker never drifts from what the API actually understands.
     */
    @GET("watch/providers/regions")
    suspend fun watchProviderRegions(): WatchProviderRegionsResponseDto

    companion object {
        const val REGION_GB = "GB"
        const val APPEND_MOVIE = "credits,keywords,videos,watch/providers,release_dates,external_ids,reviews"
        const val APPEND_TV = "credits,keywords,videos,watch/providers,content_ratings,external_ids,reviews"
    }
}
