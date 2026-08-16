package org.seg7.familywatchlist.data.remote

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit

/** Builds the TMDB Retrofit client: auth + throttle interceptors, kotlinx.serialization body conversion. */
object TmdbClient {
    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun create(
        baseUrl: String,
        accessToken: () -> String,
        okHttpClient: OkHttpClient = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(accessToken))
            .addInterceptor(ThrottleInterceptor())
            .build(),
    ): TmdbApi {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(TmdbApi::class.java)
    }
}
