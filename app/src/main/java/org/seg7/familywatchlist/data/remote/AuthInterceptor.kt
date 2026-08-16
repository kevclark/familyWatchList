package org.seg7.familywatchlist.data.remote

import okhttp3.Interceptor
import okhttp3.Response

/**
 * PLAN.md §3: v4 Read Access Token as an `Authorization: Bearer` header, sourced from
 * BuildConfig.TMDB_ACCESS_TOKEN. [tokenProvider] is a function (not a raw string) so
 * BuildConfig is read lazily and the token is never captured in a log-friendly toString.
 */
class AuthInterceptor(private val tokenProvider: () -> String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .addHeader("Authorization", "Bearer ${tokenProvider()}")
            .addHeader("Accept", "application/json")
            .build()
        return chain.proceed(request)
    }
}
