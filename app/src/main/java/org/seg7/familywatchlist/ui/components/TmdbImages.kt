package org.seg7.familywatchlist.ui.components

/**
 * PLAN.md §3: "Images: `/configuration` once; poster `w342`, backdrop `w780`."
 *
 * The base URL is hard-coded rather than read from `/configuration` at runtime. TMDB's image
 * base has been `https://image.tmdb.org/t/p/` for the life of the v3 API, and spending a
 * request (and an offline failure mode — no config means no posters at all) to re-learn a
 * constant isn't a good trade for a family app. If TMDB ever moves it, this is the one line to
 * change and `/configuration` is already wired in [org.seg7.familywatchlist.data.remote.TmdbApi].
 */
private const val IMAGE_BASE = "https://image.tmdb.org/t/p/"

/** 2:3 poster art for carousels and grids. */
fun posterUrl(path: String?): String? = path?.let { "${IMAGE_BASE}w342$it" }

/** Wide backdrop for hero surfaces (Home hero, title details). */
fun backdropUrl(path: String?): String? = path?.let { "${IMAGE_BASE}w780$it" }

/** Provider (service) logos on availability badges — small, so the smallest bucket is plenty. */
fun providerLogoUrl(path: String?): String? = path?.let { "${IMAGE_BASE}w92$it" }
