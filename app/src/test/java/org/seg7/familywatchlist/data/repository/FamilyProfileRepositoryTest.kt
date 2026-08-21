package org.seg7.familywatchlist.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.seg7.familywatchlist.data.local.AppDatabase
import org.seg7.familywatchlist.testutil.FakeClock
import org.seg7.familywatchlist.testutil.buildInMemoryDb

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FamilyProfileRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: FamilyProfileRepository
    private lateinit var profileRepo: ProfileRepository
    private val clock = FakeClock(1_000L)

    @Before
    fun setUp() {
        db = buildInMemoryDb()
        repo = FamilyProfileRepository(db.familyProfileDao(), db.profileDao(), clock)
        profileRepo = ProfileRepository(db.profileDao(), clock)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun addProfile(name: String): Long = profileRepo.addProfile(name, "a", null).getOrThrow()

    @Test
    fun `no family profile yet returns null`() = runTest {
        assertNull(repo.get())
        assertFalse(repo.exists())
    }

    @Test
    fun `saving with fewer than 2 members fails and writes nothing — PLAN md §2's 'not a blend' rule`() = runTest {
        val kev = addProfile("Kev")

        val result = repo.save("Family", "a", listOf(kev))

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is FamilyProfileMembersException)
        assertFalse(repo.exists())
    }

    @Test
    fun `saving with 2+ distinct members succeeds`() = runTest {
        val kev = addProfile("Kev")
        val sam = addProfile("Sam")

        val result = repo.save("The Clarks", "avatar-x", listOf(kev, sam))

        assertTrue(result.isSuccess)
        val loaded = repo.get()!!
        assertEquals("The Clarks", loaded.profile.name)
        assertEquals(setOf(kev, sam), loaded.memberIds.toSet())
        assertTrue(loaded.hasEnoughMembers)
    }

    @Test
    fun `duplicate ids in the request are deduped before the 2-member check`() = runTest {
        val kev = addProfile("Kev")

        val result = repo.save("Family", "a", listOf(kev, kev, kev))

        assertTrue(result.isFailure)
    }

    @Test
    fun `blank name falls back to the default 'Family'`() = runTest {
        val kev = addProfile("Kev")
        val sam = addProfile("Sam")

        repo.save("   ", "a", listOf(kev, sam))

        assertEquals(FamilyProfileRepository.DEFAULT_NAME, repo.get()?.profile?.name)
    }

    @Test
    fun `save is also edit — a second save updates the same singleton row and its membership`() = runTest {
        val kev = addProfile("Kev")
        val sam = addProfile("Sam")
        val ellie = addProfile("Ellie")
        repo.save("Family", "a", listOf(kev, sam))

        repo.save("The Clarks", "b", listOf(sam, ellie))

        val loaded = repo.get()!!
        assertEquals("The Clarks", loaded.profile.name)
        assertEquals(setOf(sam, ellie), loaded.memberIds.toSet())
    }

    @Test
    fun `createdAt is preserved across an edit, not reset`() = runTest {
        val kev = addProfile("Kev")
        val sam = addProfile("Sam")
        repo.save("Family", "a", listOf(kev, sam))
        val firstCreatedAt = repo.get()!!.profile.createdAt

        clock.advanceBy(5_000L)
        repo.save("Family renamed", "a", listOf(kev, sam))

        assertEquals(firstCreatedAt, repo.get()!!.profile.createdAt)
    }

    @Test
    fun `deleting a member profile removes them from membership but keeps the family profile`() = runTest {
        val kev = addProfile("Kev")
        val sam = addProfile("Sam")
        repo.save("Family", "a", listOf(kev, sam))

        profileRepo.delete(db.profileDao().getById(sam)!!)

        val loaded = repo.get()!!
        assertEquals(listOf(kev), loaded.memberIds)
        assertFalse(loaded.hasEnoughMembers)
    }

    @Test
    fun `observe reflects membership changes live, including a member deletion`() = runTest {
        val kev = addProfile("Kev")
        val sam = addProfile("Sam")
        repo.save("Family", "a", listOf(kev, sam))
        assertTrue(repo.observe().first()!!.hasEnoughMembers)

        profileRepo.delete(db.profileDao().getById(sam)!!)

        assertFalse(repo.observe().first()!!.hasEnoughMembers)
    }

    @Test
    fun `member profile name edits are reflected without re-saving the family profile`() = runTest {
        val kev = addProfile("Kev")
        val sam = addProfile("Sam")
        repo.save("Family", "a", listOf(kev, sam))

        val samProfile = db.profileDao().getById(sam)!!
        profileRepo.update(samProfile.copy(name = "Samantha"))

        val loaded = repo.get()!!
        assertEquals("Samantha", loaded.members.first { it.id == sam }.name)
    }
}
