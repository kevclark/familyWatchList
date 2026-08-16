package org.seg7.familywatchlist.testutil

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.seg7.familywatchlist.data.local.AppDatabase

/** Fresh in-memory Room database for DAO tests — Robolectric context, no emulator. */
fun buildInMemoryDb(): AppDatabase =
    Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        AppDatabase::class.java,
    )
        .allowMainThreadQueries()
        .build()
