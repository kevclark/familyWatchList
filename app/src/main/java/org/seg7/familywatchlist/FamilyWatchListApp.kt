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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.seg7.familywatchlist.di.AppContainer
import org.seg7.familywatchlist.work.RecommendationScheduler

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
        // PLAN.md §4 / M3f: weekly shortlist regeneration + notification, at the user's
        // configured day/hour (UserPreferencesRepository.refreshDayOfWeek/refreshHour, default
        // Friday 06:00 — was a hardcoded Monday 06:00 literal through M3e). Idempotent
        // (ExistingPeriodicWorkPolicy.KEEP via scheduleWeekly) — safe on every process start,
        // and deliberately does NOT reset an already-scheduled job's next-run time just because
        // the app launched again; only a genuine settings change
        // (RecommendationScheduler.rescheduleForSettingsChange, called from SettingsScreen) does
        // that, via UPDATE. Reading the preference needs a suspend DataStore read, so it's pushed
        // onto a background coroutine rather than blocking onCreate.
        //
        // Guarded: on real Android, WorkManager's manifest-merged androidx.startup initializer
        // always runs before Application.onCreate() reaches here, so this succeeds in production;
        // Robolectric's JVM unit-test manifest processing doesn't carry that merged initializer
        // provider through, so every one of this project's ~250 unit tests (which instantiate
        // this real Application class, not a stub) would otherwise fail at app startup on an
        // unrelated IllegalStateException before their own test body ever runs. Scheduling is
        // simply skipped in that environment.
        CoroutineScope(Dispatchers.Default).launch {
            runCatching {
                val prefs = container.userPreferencesRepository
                val day = prefs.refreshDayOfWeek.first()
                val hour = prefs.refreshHour.first()
                RecommendationScheduler.scheduleWeekly(this@FamilyWatchListApp, day, hour)
            }
        }
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
