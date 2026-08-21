package org.seg7.familywatchlist.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import org.seg7.familywatchlist.data.local.dao.DiscoverCacheDao
import org.seg7.familywatchlist.data.local.dao.FamilyProfileDao
import org.seg7.familywatchlist.data.local.dao.ProfileDao
import org.seg7.familywatchlist.data.local.dao.ProfileSlidersDao
import org.seg7.familywatchlist.data.local.dao.ProviderAvailabilityDao
import org.seg7.familywatchlist.data.local.dao.ProviderDao
import org.seg7.familywatchlist.data.local.dao.RatingDao
import org.seg7.familywatchlist.data.local.dao.ShortlistDao
import org.seg7.familywatchlist.data.local.dao.TitleAttributeDao
import org.seg7.familywatchlist.data.local.dao.TitleDao
import org.seg7.familywatchlist.data.local.dao.WatchEventDao
import org.seg7.familywatchlist.data.local.dao.WatchlistDao
import org.seg7.familywatchlist.data.local.entity.DiscoverCacheEntity
import org.seg7.familywatchlist.data.local.entity.FamilyProfileEntity
import org.seg7.familywatchlist.data.local.entity.FamilyProfileMemberEntity
import org.seg7.familywatchlist.data.local.entity.ProfileEntity
import org.seg7.familywatchlist.data.local.entity.ProfileSlidersEntity
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
        ProfileSlidersEntity::class,
        FamilyProfileEntity::class,
        FamilyProfileMemberEntity::class,
    ],
    version = 6,
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
    abstract fun profileSlidersDao(): ProfileSlidersDao
    abstract fun familyProfileDao(): FamilyProfileDao

    companion object {
        const val NAME = "family_watchlist.db"

        /**
         * v1 → v2: adds `titles.trailerKey` (PLAN.md §2 called for it at M1; it was missed).
         * A real migration rather than a destructive fallback, because the same table holds
         * cached metadata for anything already on a user's watchlist or in their history —
         * dropping it would silently blank out their list until each title refetched.
         * Existing rows get NULL and pick the key up on their next TTL refresh.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE titles ADD COLUMN trailerKey TEXT")
            }
        }

        /**
         * v2 -> v3 (PLAN.md §4/§4a, M3): adds `titles.voteCount` — the recommender's `tmdbQuality`
         * term needs the "min 20 votes" floor PLAN.md §4 specifies, which the app never persisted
         * before now. Existing rows get NULL; [org.seg7.familywatchlist.data.recommend.Scorer.tmdbQuality]
         * treats a null count as "not enough evidence" (score 0) until the title's next TTL
         * refresh backfills it, rather than crediting an unknown vote count.
         */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE titles ADD COLUMN voteCount INTEGER")
            }
        }

        /**
         * v3 -> v4 (PLAN.md §4a, M3): creates `profile_sliders` — per-profile storage for the
         * three "Tune my picks" sliders (see [ProfileSlidersEntity]'s kdoc for why this is a
         * companion table rather than columns on `profiles`). No seed data: a profile with no row
         * here reads as [org.seg7.familywatchlist.data.recommend.SliderSettings.DEFAULT] at the
         * repository layer, so nothing needs backfilling for existing profiles.
         */
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "CREATE TABLE IF NOT EXISTS `profile_sliders` (`profileId` INTEGER NOT NULL, " +
                        "`discovery` REAL NOT NULL, `recency` REAL NOT NULL, `personalMatch` REAL NOT NULL, " +
                        "PRIMARY KEY(`profileId`))"
                )
            }
        }

        /**
         * v4 -> v5 (PLAN.md §4a slider 5, M3b — design corrected same day, see
         * [ProfileSlidersEntity]'s kdoc): adds two columns to `profile_sliders`, not one —
         * `suggestionCount` (what the user *requested*) and `eligibleCandidateCount` (the real,
         * last-known number of candidates actually available, persisted on every refresh; the
         * "Tune my picks" slider's max). Both are literal `DEFAULT 30` columns rather than
         * nullable ones, so existing rows (and any profile with no row at all) both read as the
         * spec default with no extra null-handling at the repository layer.
         */
        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "ALTER TABLE profile_sliders ADD COLUMN suggestionCount INTEGER NOT NULL DEFAULT 30"
                )
                connection.execSQL(
                    "ALTER TABLE profile_sliders ADD COLUMN eligibleCandidateCount INTEGER NOT NULL DEFAULT 30"
                )
            }
        }

        /**
         * v5 -> v6 (PLAN.md §4/§2, M3d): the first-class, persistent Family profile — a singleton
         * `family_profile` row (see [FamilyProfileEntity]'s kdoc for why its PK is fixed, not
         * `autoGenerate`) plus its curated membership join table. `family_profile_members` is this
         * codebase's first table with a real `@ForeignKey` — `ON DELETE CASCADE` back to
         * `profiles(id)`, so deleting a profile that happens to be a family member cleans up its
         * membership row automatically (PLAN.md §2: "cascades on member deletion"). No seed data:
         * an app with no Family profile yet simply has no row in either table, same "nothing to
         * backfill" posture as every other additive migration in this file.
         */
        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "CREATE TABLE IF NOT EXISTS `family_profile` (`id` INTEGER NOT NULL, " +
                        "`name` TEXT NOT NULL, `avatarKey` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
                connection.execSQL(
                    "CREATE TABLE IF NOT EXISTS `family_profile_members` (`memberProfileId` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`memberProfileId`), FOREIGN KEY(`memberProfileId`) REFERENCES `profiles`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_family_profile_members_memberProfileId` " +
                        "ON `family_profile_members` (`memberProfileId`)"
                )
            }
        }
    }
}
