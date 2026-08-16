package org.seg7.familywatchlist.data.local

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.seg7.familywatchlist.data.local.entity.ProfileEntity
import org.seg7.familywatchlist.testutil.buildInMemoryDb

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProfileDaoTest {
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
    fun `insert then getById returns the row`() = runTest {
        val id = db.profileDao().insert(
            ProfileEntity(name = "Kev", avatarKey = "fox-blue", ageRatingCap = null, createdAt = 1L)
        )

        val loaded = db.profileDao().getById(id)

        assertEquals("Kev", loaded?.name)
    }

    @Test
    fun `count reflects number of rows for max-10 enforcement`() = runTest {
        repeat(3) { i ->
            db.profileDao().insert(
                ProfileEntity(name = "P$i", avatarKey = "a", ageRatingCap = null, createdAt = i.toLong())
            )
        }

        assertEquals(3, db.profileDao().count())
    }

    @Test
    fun `observeAll emits in createdAt order`() = runTest {
        db.profileDao().insert(ProfileEntity(name = "Second", avatarKey = "a", ageRatingCap = null, createdAt = 2L))
        db.profileDao().insert(ProfileEntity(name = "First", avatarKey = "a", ageRatingCap = null, createdAt = 1L))

        val all = db.profileDao().observeAll().first()

        assertEquals(listOf("First", "Second"), all.map { it.name })
    }

    @Test
    fun `update changes stored fields`() = runTest {
        val id = db.profileDao().insert(
            ProfileEntity(name = "Kid", avatarKey = "a", ageRatingCap = null, createdAt = 1L)
        )
        val loaded = db.profileDao().getById(id)!!

        db.profileDao().update(loaded.copy(ageRatingCap = "12"))

        assertEquals("12", db.profileDao().getById(id)?.ageRatingCap)
    }

    @Test
    fun `delete removes the row`() = runTest {
        val id = db.profileDao().insert(
            ProfileEntity(name = "Temp", avatarKey = "a", ageRatingCap = null, createdAt = 1L)
        )
        val loaded = db.profileDao().getById(id)!!

        db.profileDao().delete(loaded)

        assertNull(db.profileDao().getById(id))
    }
}
