package org.seg7.familywatchlist.data.local

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.seg7.familywatchlist.data.local.entity.ProviderEntity
import org.seg7.familywatchlist.testutil.buildInMemoryDb

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProviderDaoTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = buildInMemoryDb()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `setSubscribed toggles a provider`() = runTest {
        val dao = db.providerDao()
        dao.upsertAll(listOf(ProviderEntity(8, "Netflix", "/n.png", subscribed = false, displayPriority = 1)))

        dao.setSubscribed(8, true)

        assertEquals(listOf(8), dao.getSubscribed().map { it.providerId })
    }

    @Test
    fun `observeAll orders by displayPriority`() = runTest {
        val dao = db.providerDao()
        dao.upsertAll(
            listOf(
                ProviderEntity(2, "Disney+", null, subscribed = true, displayPriority = 2),
                ProviderEntity(1, "Netflix", null, subscribed = true, displayPriority = 1),
            )
        )

        val all = dao.observeAll().first()

        assertEquals(listOf("Netflix", "Disney+"), all.map { it.name })
    }

    @Test
    fun `count reflects seeded rows`() = runTest {
        val dao = db.providerDao()
        dao.upsertAll(
            listOf(
                ProviderEntity(1, "Netflix", null, subscribed = true, displayPriority = 1),
                ProviderEntity(2, "BBC iPlayer", null, subscribed = true, displayPriority = 2),
            )
        )

        assertEquals(2, dao.count())
    }
}
