package org.seg7.familywatchlist.work

import org.junit.Assert.assertEquals
import org.junit.Test
import org.seg7.familywatchlist.data.repository.ProfileRefreshResult

/**
 * PLAN.md §4 "Per-profile notification control" (M3e): the master × per-profile combination
 * table this milestone's testing bar explicitly calls for — a plain JVM test, no Robolectric,
 * since [NotificationGate] touches no Room/DataStore/Android API.
 */
class NotificationGateTest {

    private val kev = ProfileRefreshResult(profileId = 1L, name = "Kev")
    private val sam = ProfileRefreshResult(profileId = 2L, name = "Sam")
    private val family = ProfileRefreshResult(profileId = -1L, name = "Family")

    @Test
    fun `master off and a profile on -- nothing notifies`() {
        val result = NotificationGate.profilesToNotify(
            completed = listOf(kev),
            masterEnabled = false,
            perProfileEnabled = { true },
        )

        assertEquals(emptyList<ProfileRefreshResult>(), result)
    }

    @Test
    fun `master on and one profile off -- that profile is excluded, others still notify`() {
        val result = NotificationGate.profilesToNotify(
            completed = listOf(kev, sam),
            masterEnabled = true,
            perProfileEnabled = { id -> id != sam.profileId },
        )

        assertEquals(listOf(kev), result)
    }

    @Test
    fun `master on and every profile on -- matches today's existing behaviour exactly, all completed profiles notify`() {
        val result = NotificationGate.profilesToNotify(
            completed = listOf(kev, sam, family),
            masterEnabled = true,
            perProfileEnabled = { true },
        )

        assertEquals(listOf(kev, sam, family), result)
    }

    @Test
    fun `master off and every profile on -- still nothing, master always wins`() {
        val result = NotificationGate.profilesToNotify(
            completed = listOf(kev, sam, family),
            masterEnabled = false,
            perProfileEnabled = { true },
        )

        assertEquals(emptyList<ProfileRefreshResult>(), result)
    }

    @Test
    fun `a profile absent from completed never notifies even if its own toggle is on -- for example, a failed refresh`() {
        // Only kev is in `completed` -- sam isn't, simulating sam's refresh having failed this
        // run (RecommendationRepository.refreshAll only returns profiles that finished).
        val result = NotificationGate.profilesToNotify(
            completed = listOf(kev),
            masterEnabled = true,
            perProfileEnabled = { true }, // sam's own toggle is on, but sam never appears in `completed`
        )

        assertEquals(listOf(kev), result)
    }

    @Test
    fun `empty completed list -- nothing notifies regardless of toggles`() {
        val result = NotificationGate.profilesToNotify(
            completed = emptyList(),
            masterEnabled = true,
            perProfileEnabled = { true },
        )

        assertEquals(emptyList<ProfileRefreshResult>(), result)
    }
}
