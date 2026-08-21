package org.seg7.familywatchlist.data.local

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.seg7.familywatchlist.data.local.entity.FamilyProfileEntity
import org.seg7.familywatchlist.data.local.entity.ProfileEntity
import org.seg7.familywatchlist.testutil.buildInMemoryDb

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FamilyProfileDaoTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = buildInMemoryDb()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun addProfile(name: String): Long =
        db.profileDao().insert(ProfileEntity(name = name, avatarKey = "a", ageRatingCap = null, createdAt = 1L))

    @Test
    fun `upsert then get returns the singleton row`() = runTest {
        db.familyProfileDao().upsert(FamilyProfileEntity(name = "Family", avatarKey = "a", createdAt = 1L))

        val loaded = db.familyProfileDao().get()

        assertEquals("Family", loaded?.name)
        assertEquals(FamilyProfileEntity.SINGLETON_ID, loaded?.id)
    }

    @Test
    fun `no row yet returns null`() = runTest {
        assertNull(db.familyProfileDao().get())
    }

    @Test
    fun `upsert on an existing row updates it in place, not a second row`() = runTest {
        db.familyProfileDao().upsert(FamilyProfileEntity(name = "Family", avatarKey = "a", createdAt = 1L))
        db.familyProfileDao().upsert(FamilyProfileEntity(name = "The Clarks", avatarKey = "b", createdAt = 1L))

        assertEquals("The Clarks", db.familyProfileDao().get()?.name)
    }

    @Test
    fun `replaceMembers fully swaps the membership set`() = runTest {
        val kev = addProfile("Kev")
        val sam = addProfile("Sam")
        val ellie = addProfile("Ellie")
        db.familyProfileDao().replaceMembers(listOf(kev, sam))

        assertEquals(setOf(kev, sam), db.familyProfileDao().getMemberIds().toSet())

        db.familyProfileDao().replaceMembers(listOf(sam, ellie))

        assertEquals(setOf(sam, ellie), db.familyProfileDao().getMemberIds().toSet())
    }

    @Test
    fun `observeMemberIds emits the current membership`() = runTest {
        val kev = addProfile("Kev")
        val sam = addProfile("Sam")

        db.familyProfileDao().replaceMembers(listOf(kev, sam))

        assertEquals(setOf(kev, sam), db.familyProfileDao().observeMemberIds().first().toSet())
    }

    @Test
    fun `deleting a member profile cascades their membership row away`() = runTest {
        val kev = addProfile("Kev")
        val sam = addProfile("Sam")
        db.familyProfileDao().replaceMembers(listOf(kev, sam))

        db.profileDao().delete(db.profileDao().getById(sam)!!)

        assertEquals(listOf(kev), db.familyProfileDao().getMemberIds())
    }

    @Test
    fun `deleting a member profile does not delete the family profile itself`() = runTest {
        val kev = addProfile("Kev")
        val sam = addProfile("Sam")
        db.familyProfileDao().upsert(FamilyProfileEntity(name = "Family", avatarKey = "a", createdAt = 1L))
        db.familyProfileDao().replaceMembers(listOf(kev, sam))

        db.profileDao().delete(db.profileDao().getById(sam)!!)

        assertTrue(db.familyProfileDao().get() != null)
    }
}
