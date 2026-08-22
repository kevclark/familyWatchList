package org.seg7.familywatchlist.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * M6 regression fix (PROGRESS.md "M6 regression found on Kev's real phone"): proves
 * [AppDatabase.MIGRATION_7_8] no longer leaves a title's `fetchedAt` looking deceptively fresh
 * after the migration drops and recreates `provider_availability`. Runs the *real* migration
 * SQL against the *real* exported v7 schema (checked into `app/schemas/`), the same schema
 * Room validates production installs against, rather than a hand-rolled fixture — so this would
 * have caught the original bug.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppDatabaseMigrationTest {
    private val dbName = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    /**
     * A title fetched shortly before the migration ran (like Kev's real Central Intelligence
     * row: fetched the previous evening) has a `fetchedAt` well inside the 7-day provider TTL.
     * Before the fix, that timestamp survived the migration untouched even though
     * `provider_availability` was just wiped out from under it — [TitleRepository.isProviderDataStale]
     * would then wrongly report the row as fresh for up to another 7 days. After the fix, the
     * migration itself zeroes `fetchedAt`, so the very next freshness check trips a real refetch.
     */
    @Test
    fun migrate7to8_resetsFetchedAtSoProviderRefreshTrips() {
        val recentFetchedAt = 9_999_999_999L // "yesterday", i.e. well within the 7-day TTL

        helper.createDatabase(dbName, 7).apply {
            execSQL(
                "INSERT INTO titles (tmdbId, mediaType, title, year, posterPath, backdropPath, " +
                    "overview, runtimeMin, certification, voteAverage, voteCount, popularity, " +
                    "trailerKey, fetchedAt) VALUES (11, 'MOVIE', 'Central Intelligence', 2016, " +
                    "NULL, NULL, NULL, 107, '12A', 6.4, 4000, 20.0, NULL, $recentFetchedAt)"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(dbName, 8, true, AppDatabase.MIGRATION_7_8)

        migrated.query("SELECT fetchedAt FROM titles WHERE tmdbId = 11 AND mediaType = 'MOVIE'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            val migratedFetchedAt = cursor.getLong(0)
            assertEquals(0L, migratedFetchedAt)

            // The actual regression: TitleRepository.isProviderDataStale/isMetadataStale derive
            // freshness from "now - fetchedAt >= TTL". A zeroed fetchedAt trips both TTLs
            // immediately for any real "now", proving a refetch is correctly forced rather than
            // silently trusted as still fresh.
            val now = System.currentTimeMillis()
            val providerTtlMs = 7L * 24 * 60 * 60 * 1000
            assertTrue(
                "expected the migrated fetchedAt to be outside the 7-day provider TTL",
                now - migratedFetchedAt >= providerTtlMs,
            )
        }
    }
}
