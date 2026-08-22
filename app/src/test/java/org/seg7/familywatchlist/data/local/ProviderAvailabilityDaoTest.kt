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

    /**
     * M6 regression (PLAN.md §5 "Paid (rent/buy) titles" addendum, DB v7 -> v8): the same
     * provider can legitimately be both FLATRATE and RENT for one title (Amazon Video routinely
     * is) — before `kind` joined the primary key, the second `upsertAll` here would have silently
     * clobbered the first row instead of adding a second one.
     */
    @Test
    fun `the same provider can hold both a FLATRATE and a RENT row for the same title without clobbering`() = runTest {
        val dao = db.providerAvailabilityDao()
        dao.upsertAll(listOf(ProviderAvailabilityEntity(1, MediaType.MOVIE, providerId = 8, kind = ProviderKind.FLATRATE, fetchedAt = 1L)))
        dao.upsertAll(listOf(ProviderAvailabilityEntity(1, MediaType.MOVIE, providerId = 8, kind = ProviderKind.RENT, fetchedAt = 1L)))

        val rows = dao.getForTitle(1, MediaType.MOVIE)

        assertEquals(setOf(ProviderKind.FLATRATE, ProviderKind.RENT), rows.map { it.kind }.toSet())
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
