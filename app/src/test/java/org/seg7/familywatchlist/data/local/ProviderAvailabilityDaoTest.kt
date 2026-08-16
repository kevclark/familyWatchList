package org.seg7.familywatchlist.data.local

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.ProviderAvailabilityEntity
import org.seg7.familywatchlist.data.local.entity.ProviderKind
import org.seg7.familywatchlist.testutil.buildInMemoryDb

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProviderAvailabilityDaoTest {
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
    fun `replaceForTitle drops stale providers`() = runTest {
        val dao = db.providerAvailabilityDao()
        dao.replaceForTitle(
            1, MediaType.MOVIE,
            listOf(ProviderAvailabilityEntity(1, MediaType.MOVIE, providerId = 8, kind = ProviderKind.FLATRATE, fetchedAt = 1L))
        )

        dao.replaceForTitle(
            1, MediaType.MOVIE,
            listOf(ProviderAvailabilityEntity(1, MediaType.MOVIE, providerId = 337, kind = ProviderKind.FLATRATE, fetchedAt = 2L))
        )

        val rows = dao.getForTitle(1, MediaType.MOVIE)
        assertEquals(listOf(337), rows.map { it.providerId })
    }

    @Test
    fun `availability is scoped per title`() = runTest {
        val dao = db.providerAvailabilityDao()
        dao.upsertAll(listOf(ProviderAvailabilityEntity(1, MediaType.MOVIE, 8, ProviderKind.FLATRATE, 1L)))
        dao.upsertAll(listOf(ProviderAvailabilityEntity(2, MediaType.MOVIE, 337, ProviderKind.FREE, 1L)))

        assertEquals(1, dao.getForTitle(1, MediaType.MOVIE).size)
        assertEquals(1, dao.getForTitle(2, MediaType.MOVIE).size)
    }
}
