package org.seg7.familywatchlist.testutil

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.Executors
import org.seg7.familywatchlist.data.local.AppDatabase

/**
 * A single, process-lifetime executor shared by every in-memory Room database this test suite
 * builds (not recreated/leaked per test). Room's own un-configured default query/transaction
 * executor pool can be small enough, combined with many ViewModel-level tests whose
 * `viewModelScope` coroutines aren't always fully joined before the next test starts (cancelling
 * a coroutine is cooperative — it can't forcibly interrupt a thread already blocked inside a real
 * Room/SQLite call), to genuinely starve/deadlock under this suite's volume of concurrent
 * suspend-DAO round-trips. A larger, explicit, shared pool gives real headroom without changing
 * any query's actual behaviour.
 */
private val sharedRoomExecutor = Executors.newFixedThreadPool(16)

/** Fresh in-memory Room database for DAO tests — Robolectric context, no emulator. */
fun buildInMemoryDb(): AppDatabase =
    Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        AppDatabase::class.java,
    )
        .allowMainThreadQueries()
        .setQueryExecutor(sharedRoomExecutor)
        .setTransactionExecutor(sharedRoomExecutor)
        .build()
