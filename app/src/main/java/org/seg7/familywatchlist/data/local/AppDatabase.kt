package org.seg7.familywatchlist.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import org.seg7.familywatchlist.data.local.dao.DiscoverCacheDao
import org.seg7.familywatchlist.data.local.dao.ProfileDao
import org.seg7.familywatchlist.data.local.dao.ProviderAvailabilityDao
import org.seg7.familywatchlist.data.local.dao.ProviderDao
import org.seg7.familywatchlist.data.local.dao.RatingDao
import org.seg7.familywatchlist.data.local.dao.ShortlistDao
import org.seg7.familywatchlist.data.local.dao.TitleAttributeDao
import org.seg7.familywatchlist.data.local.dao.TitleDao
import org.seg7.familywatchlist.data.local.dao.WatchEventDao
import org.seg7.familywatchlist.data.local.dao.WatchlistDao
import org.seg7.familywatchlist.data.local.entity.DiscoverCacheEntity
import org.seg7.familywatchlist.data.local.entity.ProfileEntity
import org.seg7.familywatchlist.data.local.entity.ProviderAvailabilityEntity
import org.seg7.familywatchlist.data.local.entity.ProviderEntity
import org.seg7.familywatchlist.data.local.entity.RatingEntity
import org.seg7.familywatchlist.data.local.entity.ShortlistEntryEntity
import org.seg7.familywatchlist.data.local.entity.TitleAttributeEntity
import org.seg7.familywatchlist.data.local.entity.TitleEntity
import org.seg7.familywatchlist.data.local.entity.WatchEventEntity
import org.seg7.familywatchlist.data.local.entity.WatchEventProfileEntity
import org.seg7.familywatchlist.data.local.entity.WatchlistEntryEntity

/**
 * PLAN.md §2 data model, plus [DiscoverCacheEntity] (see its kdoc — an implementation detail
 * needed for §3's discover-page TTL that §2 doesn't name as a standalone table).
 */
@Database(
    entities = [
        ProfileEntity::class,
        TitleEntity::class,
        TitleAttributeEntity::class,
        WatchEventEntity::class,
        WatchEventProfileEntity::class,
        RatingEntity::class,
        WatchlistEntryEntity::class,
        ProviderEntity::class,
        ProviderAvailabilityEntity::class,
        ShortlistEntryEntity::class,
        DiscoverCacheEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun titleDao(): TitleDao
    abstract fun titleAttributeDao(): TitleAttributeDao
    abstract fun watchEventDao(): WatchEventDao
    abstract fun ratingDao(): RatingDao
    abstract fun watchlistDao(): WatchlistDao
    abstract fun providerDao(): ProviderDao
    abstract fun providerAvailabilityDao(): ProviderAvailabilityDao
    abstract fun shortlistDao(): ShortlistDao
    abstract fun discoverCacheDao(): DiscoverCacheDao

    companion object {
        const val NAME = "family_watchlist.db"
    }
}
