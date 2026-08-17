package org.seg7.familywatchlist

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import org.seg7.familywatchlist.di.AppContainer

/**
 * Application entry point. Owns the single [AppContainer] (PLAN.md §1: manual DI, no Hilt) and
 * configures Coil's singleton loader (PLAN.md §1: "Disk/memory caching of posters for free";
 * §5a motion: "Coil crossfade on image load").
 */
class FamilyWatchListApp : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    /**
     * Poster/backdrop images come from TMDB's CDN, which is unauthenticated — so this loader
     * deliberately gets a *plain* OkHttp client rather than the TMDB API one. Reusing the API
     * client would send the bearer token to image.tmdb.org on every poster, and put every
     * poster through the 4 req/s API throttle (PLAN.md §3), which would make a carousel of
     * twenty posters take five seconds to paint.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory()) }
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.20).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(96L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()
}
