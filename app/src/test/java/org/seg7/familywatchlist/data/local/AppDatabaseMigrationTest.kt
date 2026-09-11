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
 * M6 regression fix (PROGRESS.md "M6 regression found on Kev's real phone" and its
 * "Two more real-device findings" follow-up): proves [AppDatabase.MIGRATION_7_8] widens
 * `provider_availability`'s primary key correctly, and that [AppDatabase.MIGRATION_8_9] — not
 * `MIGRATION_7_8` — is the migration that resets a title's `fetchedAt` so it no longer looks
 * deceptively fresh after `provider_availability` was dropped and recreated. The reset was
 * originally (and incorrectly) patched into `MIGRATION_7_8`'s already-shipped body without a
 * version bump, so it never ran on devices that had already migrated past v8; `MIGRATION_8_9`
 * is a genuine new version transition that does run. Runs the *real* migration SQL against the
 * *real* exported schemas (checked into `app/schemas/`), the same schemas Room validates
 * production installs against, rather than hand-rolled fixtures — so this would have caught the
 * original bug.
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
     * Proves [AppDatabase.MIGRATION_7_8] still validates cleanly against the real v7 schema —
     * i.e. the primary-key-widening fix on its own, independent of the (now separate)
     * `fetchedAt` reset.
     */
    @Test
    fun migrate7to8_validatesAgainstRealSchema() {
        helper.createDatabase(dbName, 7).apply { close() }

        helper.runMigrationsAndValidate(dbName, 8, true, AppDatabase.MIGRATION_7_8)
    }

    /**
     * A title fetched shortly before the migration ran (like Kev's real Central Intelligence
     * row: fetched the previous evening) has a `fetchedAt` well inside the 7-day provider TTL.
     * Before the fix, that timestamp survived the v7->v8 migration untouched even though
     * `provider_availability` was just wiped out from under it — [TitleRepository.isProviderDataStale]
     * would then wrongly report the row as fresh for up to another 7 days. [AppDatabase.MIGRATION_8_9]
     * zeroes `fetchedAt`, so the very next freshness check trips a real refetch — and it does so
     * as a genuine v8->v9 transition, so it actually runs on a device already sitting at v8
     * (unlike the abandoned in-place patch to `MIGRATION_7_8`).
     */
    @Test
    fun migrate8to9_resetsFetchedAtSoProviderRefreshTrips() {
        val recentFetchedAt = 9_999_999_999L // "yesterday", i.e. well within the 7-day TTL

        helper.createDatabase(dbName, 8).apply {
            execSQL(
                "INSERT INTO titles (tmdbId, mediaType, title, year, posterPath, backdropPath, " +
                    "overview, runtimeMin, certification, voteAverage, voteCount, popularity, " +
                    "trailerKey, fetchedAt) VALUES (11, 'MOVIE', 'Central Intelligence', 2016, " +
                    "NULL, NULL, NULL, 107, '12A', 6.4, 4000, 20.0, NULL, $recentFetchedAt)"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(dbName, 9, true, AppDatabase.MIGRATION_8_9)

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

    /**
     * PLAN.md §5c (M14): [AppDatabase.MIGRATION_9_10] adds `titles.imdbId` (existing rows get
     * NULL, proven here against a real pre-migration row) and creates the new `reviews` table —
     * against the real exported v9/v10 schemas, mirroring this class's existing v7/v8/v9 pattern.
     */
    @Test
    fun `migrate9to10_addsImdbIdColumnAndCreatesReviewsTable`() {
        helper.createDatabase(dbName, 9).apply {
            execSQL(
                "INSERT INTO titles (tmdbId, mediaType, title, year, posterPath, backdropPath, " +
                    "overview, runtimeMin, certification, voteAverage, voteCount, popularity, " +
                    "trailerKey, fetchedAt) VALUES (11, 'MOVIE', 'Central Intelligence', 2016, " +
                    "NULL, NULL, NULL, 107, '12A', 6.4, 4000, 20.0, NULL, 0)"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(dbName, 10, true, AppDatabase.MIGRATION_9_10)

        migrated.query("SELECT imdbId FROM titles WHERE tmdbId = 11 AND mediaType = 'MOVIE'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.isNull(0))
        }

        migrated.execSQL(
            "INSERT INTO reviews (tmdbId, mediaType, reviewId, author, content, url, rating, createdAt) " +
                "VALUES (11, 'MOVIE', 'r1', 'A Reviewer', 'Great film.', 'https://example.com/r1', 8.0, NULL)"
        )
        migrated.query("SELECT author, rating FROM reviews WHERE tmdbId = 11 AND mediaType = 'MOVIE'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("A Reviewer", cursor.getString(0))
            assertEquals(8.0, cursor.getDouble(1), 0.0)
        }
    }
}
