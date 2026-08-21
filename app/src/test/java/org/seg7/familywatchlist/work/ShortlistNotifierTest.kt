package org.seg7.familywatchlist.work

import android.Manifest
import android.app.NotificationManager
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNotificationManager

/**
 * PLAN.md §4 "Per-profile notification control" (M3e): [ShortlistNotifier] now takes the
 * already-filtered list of profile names to mention (computed upstream by [NotificationGate]),
 * and posts nothing at all when that list is empty (master toggle off, or every completed
 * profile individually toggled off) rather than an empty/blank notification.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShortlistNotifierTest {

    private val context: Application = ApplicationProvider.getApplicationContext()

    /**
     * These tests are about the *content* of what gets posted once the OS gate is open, not the
     * gate itself (that's [notifyShortlistReady]'s own `ActivityCompat.checkSelfPermission`
     * early-return, unchanged by M3e) — grant it explicitly here rather than depending on
     * Robolectric's un-configured default for a runtime (dangerous) permission.
     */
    @Before
    fun grantNotificationPermission() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun notificationManager(): ShadowNotificationManager =
        shadowOf(context.getSystemService(NotificationManager::class.java))

    @Test
    fun `empty profile list posts no notification at all`() {
        ShortlistNotifier.notifyShortlistReady(context, emptyList())

        assertEquals(0, notificationManager().allNotifications.size)
    }

    @Test
    fun `a single profile name posts a notification naming just that profile`() {
        ShortlistNotifier.notifyShortlistReady(context, listOf("Kev"))

        val posted = notificationManager().allNotifications
        assertEquals(1, posted.size)
        assertEquals("Kev's picks are ready.", posted[0].extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString())
    }

    @Test
    fun `two profile names are joined with 'and', not a bare comma`() {
        ShortlistNotifier.notifyShortlistReady(context, listOf("Kev", "Family"))

        val posted = notificationManager().allNotifications
        assertEquals(1, posted.size)
        assertEquals(
            "Kev's and Family's picks are ready.",
            posted[0].extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString(),
        )
    }

    @Test
    fun `three or more profile names use a comma list with 'and' before the last`() {
        ShortlistNotifier.notifyShortlistReady(context, listOf("Kev", "Sam", "Family"))

        val posted = notificationManager().allNotifications
        assertEquals(1, posted.size)
        assertEquals(
            "Kev's, Sam's and Family's picks are ready.",
            posted[0].extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString(),
        )
    }

    @Test
    fun `a second call with the same fixed notification id replaces the first, no stacking`() {
        ShortlistNotifier.notifyShortlistReady(context, listOf("Kev"))
        ShortlistNotifier.notifyShortlistReady(context, listOf("Kev", "Sam"))

        assertEquals(1, notificationManager().allNotifications.size)
        assertEquals(
            "Kev's and Sam's picks are ready.",
            notificationManager().allNotifications[0].extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString(),
        )
    }
}
