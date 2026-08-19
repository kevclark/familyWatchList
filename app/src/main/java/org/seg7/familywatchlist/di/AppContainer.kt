package org.seg7.familywatchlist.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import org.seg7.familywatchlist.BuildConfig
import org.seg7.familywatchlist.common.AppClock
import org.seg7.familywatchlist.common.SystemAppClock
import org.seg7.familywatchlist.data.local.AppDatabase
import org.seg7.familywatchlist.data.remote.TmdbApi
import org.seg7.familywatchlist.data.remote.TmdbClient
import org.seg7.familywatchlist.data.repository.AvailabilityGate
import org.seg7.familywatchlist.data.repository.DiscoverRepository
import org.seg7.familywatchlist.data.repository.ProfileRepository
import org.seg7.familywatchlist.data.repository.ProviderRepository
import org.seg7.familywatchlist.data.repository.RatingRepository
import org.seg7.familywatchlist.data.repository.SearchRepository
import org.seg7.familywatchlist.data.repository.TitleRepository
import org.seg7.familywatchlist.data.repository.UserPreferencesRepository
import org.seg7.familywatchlist.data.repository.WatchEventRepository
import org.seg7.familywatchlist.data.repository.WatchlistRepository

/**
 * Manual DI container (PLAN.md §1: "Manual (single AppContainer) — avoids Hilt's KSP overhead").
 * One instance lives on [org.seg7.familywatchlist.FamilyWatchListApp]; screens (from M2 on)
 * pull what they need from it.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val clock: AppClock = SystemAppClock()

    val database: AppDatabase = Room.databaseBuilder(appContext, AppDatabase::class.java, AppDatabase.NAME)
        .addMigrations(AppDatabase.MIGRATION_1_2)
        .build()

    val tmdbApi: TmdbApi = TmdbClient.create(
        baseUrl = BuildConfig.TMDB_BASE_URL,
        accessToken = { BuildConfig.TMDB_ACCESS_TOKEN },
    )

    val profileRepository: ProfileRepository by lazy {
        ProfileRepository(database.profileDao(), clock)
    }

    val titleRepository: TitleRepository by lazy {
        TitleRepository(database.titleDao(), database.titleAttributeDao(), database.providerAvailabilityDao(), tmdbApi, clock)
    }

    val discoverRepository: DiscoverRepository by lazy {
        DiscoverRepository(database.discoverCacheDao(), database.titleDao(), tmdbApi, clock)
    }

    val providerRepository: ProviderRepository by lazy {
        ProviderRepository(database.providerDao(), tmdbApi, discoverRepository)
    }

    // PLAN.md §5a: shared "is this on a service we pay for" check, reused by search and the
    // watchlist add path so the two can't disagree about what "available" means.
    val availabilityGate: AvailabilityGate by lazy {
        AvailabilityGate(titleRepository, providerRepository)
    }

    val searchRepository: SearchRepository by lazy {
        SearchRepository(database.titleDao(), tmdbApi, clock, availabilityGate)
    }

    val watchlistRepository: WatchlistRepository by lazy {
        WatchlistRepository(database.watchlistDao(), clock, availabilityGate::isAvailableOnSubscribedProvider)
    }

    val ratingRepository: RatingRepository by lazy {
        RatingRepository(database.ratingDao(), clock)
    }

    val watchEventRepository: WatchEventRepository by lazy {
        WatchEventRepository(database.watchEventDao(), database.watchlistDao())
    }

    // Onboarding-complete flag + active profile (M2a, PLAN.md §1/§5).
    private val userPreferencesDataStore: DataStore<Preferences> by lazy {
        PreferenceDataStoreFactory.create(
            produceFile = { appContext.preferencesDataStoreFile("user_prefs") },
        )
    }

    val userPreferencesRepository: UserPreferencesRepository by lazy {
        UserPreferencesRepository(userPreferencesDataStore)
    }
}
