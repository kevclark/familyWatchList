package org.seg7.familywatchlist.ui.details

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.seg7.familywatchlist.ui.theme.FamilyWatchListTheme

/**
 * M4a-2: covers the one piece of [TrailerPlayerDialog] that's cleanly testable without a real,
 * rendering `WebView` — the dismiss wiring. The embedded YouTube IFrame Player itself (autoplay
 * actually firing, video actually rendering) is a live-verification concern per the milestone
 * brief, not something a JVM/Robolectric test can meaningfully assert without becoming brittle
 * against `WebView` internals, so it's deliberately left out here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrailerPlayerDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun closeButtonTap_invokesOnDismiss() {
        var dismissed = false
        composeRule.setContent {
            FamilyWatchListTheme {
                TrailerPlayerDialog(youTubeKey = "dQw4w9WgXcQ", onDismiss = { dismissed = true })
            }
        }

        composeRule.onNodeWithContentDescription("Close trailer").performClick()

        assertTrue(dismissed)
    }

    /**
     * M9: the real-device fullscreen fix. `playsinline=1` was dropped from the embed URL because
     * it tells the browser to keep video inline instead of handing off to native fullscreen —
     * directly opposed to `onShowCustomView`, which only fires for a real fullscreen hand-off.
     * Not itself proof the fix works on real hardware (that's a live-verification concern per the
     * milestone brief) — just confirms the URL construction the fix actually depends on.
     */
    @Test
    fun trailerEmbedUrl_hasAutoplayAndNoPlaysinline() {
        val url = trailerEmbedUrl("dQw4w9WgXcQ")

        assertEquals("https://www.youtube.com/embed/dQw4w9WgXcQ?autoplay=1", url)
        assertFalse(url.contains("playsinline"))
    }

    /**
     * M9: the JS-bridge fullscreen fallback. `onShowCustomView` is confirmed to never fire on at
     * least one real device, so this listens for `fullscreenchange` on the wrapper page's own
     * document instead (per the Fullscreen API spec, a same-origin ancestor document receives
     * this event when a nested cross-origin iframe — the YouTube embed — enters/exits
     * fullscreen). These assertions cover the JS-string-construction piece, which is genuinely
     * unit-testable; the live WebView behaviour itself isn't (see the milestone brief).
     */
    @Test
    fun fullscreenListenerJs_dispatchesToNamedBridgeOnBothEdges() {
        val js = fullscreenListenerJs("FwlFullscreenBridge")

        assertTrue(js.contains("addEventListener('fullscreenchange'"))
        assertTrue(js.contains("document.fullscreenElement"))
        assertTrue(js.contains("FwlFullscreenBridge.onEnterFullscreen();"))
        assertTrue(js.contains("FwlFullscreenBridge.onExitFullscreen();"))
    }

    @Test
    fun fullscreenListenerJs_defaultsToTheBridgeNameActuallyRegistered() {
        val js = fullscreenListenerJs()

        assertTrue(js.contains("$FULLSCREEN_JS_BRIDGE_NAME.onEnterFullscreen();"))
        assertTrue(js.contains("$FULLSCREEN_JS_BRIDGE_NAME.onExitFullscreen();"))
    }

    @Test
    fun exitFullscreenJs_callsTheRealFullscreenApiExitMethod() {
        assertEquals("document.exitFullscreen();", exitFullscreenJs())
    }

    /**
     * M9: the app's own manual fullscreen toggle — the fourth, WebView/Fullscreen-API-independent
     * path. This covers the purely-Compose-state half of it (icon toggling, close button hiding
     * while manual fullscreen is active): the actual system-bar-hiding via
     * [androidx.core.view.WindowInsetsControllerCompat] is real-Window-API territory, not
     * meaningfully unit-testable, and is covered by live emulator verification instead (see the
     * milestone brief).
     */
    @Test
    fun manualFullscreenToggle_flipsIconAndHidesCloseButtonWhileActive() {
        composeRule.setContent {
            FamilyWatchListTheme {
                TrailerPlayerDialog(youTubeKey = "dQw4w9WgXcQ", onDismiss = {})
            }
        }

        composeRule.onNodeWithContentDescription("Enter fullscreen").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Close trailer").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Enter fullscreen").performClick()

        composeRule.onNodeWithContentDescription("Exit fullscreen").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Close trailer").assertDoesNotExist()

        composeRule.onNodeWithContentDescription("Exit fullscreen").performClick()

        composeRule.onNodeWithContentDescription("Enter fullscreen").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Close trailer").assertIsDisplayed()
    }
}
