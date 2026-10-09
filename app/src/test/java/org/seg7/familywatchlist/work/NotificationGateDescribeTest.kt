package org.seg7.familywatchlist.work

import org.junit.Assert.assertEquals
import org.junit.Test
import org.seg7.familywatchlist.data.repository.ProfileRefreshResult

/** PLAN.md §5d (M15): the refresh log's "posted / suppressed, and why" sentence. */
class NotificationGateDescribeTest {
    private val kev = ProfileRefreshResult(1, "Kev")
    private val sam = ProfileRefreshResult(2, "Sam")

    @Test
    fun `master off`() {
        assertEquals(
            "Not sent: notifications are turned off in Settings",
            NotificationGate.describe(listOf(kev), masterEnabled = false, toNotify = emptyList(), posted = null),
        )
    }

    @Test
    fun `every profile toggled off names them`() {
        assertEquals(
            "Not sent: notifications are off for Kev, Sam",
            NotificationGate.describe(listOf(kev, sam), masterEnabled = true, toNotify = emptyList(), posted = null),
        )
    }

    @Test
    fun `OS permission denied`() {
        assertEquals(
            "Not sent: Android notification permission was denied",
            NotificationGate.describe(listOf(kev), true, listOf(kev), ShortlistNotifier.Result.PERMISSION_DENIED),
        )
    }

    @Test
    fun `blocked in system settings`() {
        assertEquals(
            "Not sent: notifications are blocked for this app in Android settings",
            NotificationGate.describe(listOf(kev), true, listOf(kev), ShortlistNotifier.Result.BLOCKED_BY_SYSTEM),
        )
    }

    @Test
    fun `posted for all`() {
        assertEquals(
            "Posted for Kev, Sam",
            NotificationGate.describe(listOf(kev, sam), true, listOf(kev, sam), ShortlistNotifier.Result.POSTED),
        )
    }

    @Test
    fun `posted for some notes who was off`() {
        assertEquals(
            "Posted for Kev (off for Sam)",
            NotificationGate.describe(listOf(kev, sam), true, listOf(kev), ShortlistNotifier.Result.POSTED),
        )
    }

    @Test
    fun `no completed profiles`() {
        assertEquals(
            "Not sent: no profile finished refreshing",
            NotificationGate.describe(emptyList(), true, emptyList(), null),
        )
    }
}
