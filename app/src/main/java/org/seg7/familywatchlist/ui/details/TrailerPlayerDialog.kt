package org.seg7.familywatchlist.ui.details

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.seg7.familywatchlist.ui.components.clickableNoRipple
import org.seg7.familywatchlist.ui.theme.Chalk
import org.seg7.familywatchlist.ui.theme.Dimens
import org.seg7.familywatchlist.ui.theme.Ink

/**
 * M4a-2: in-app trailer playback. TMDB's trailer data is only ever a YouTube video key — there's
 * no direct downloadable file URL, so raw ExoPlayer/VLC-style playback isn't possible, and
 * scraping YouTube for one is against their ToS (off the table). YouTube's own IFrame Player
 * embeds legitimately in a `WebView`, so that's the fix: a full-screen [Dialog] hosting the
 * embed, replacing the external-app/browser `Intent` M4a shipped.
 *
 * A [Dialog] rather than a [androidx.compose.material3.ModalBottomSheet] (used elsewhere in this
 * codebase, e.g. `LogWatchSheet`, `SettingsScreen`) because the sheet's partial-height, swipe-to-
 * dismiss-over-content pattern is for glanceable forms — a video wants the full screen and a
 * dedicated dismiss action, not a half-covered backdrop. `DialogProperties(usePlatformDefaultWidth
 * = false)` lets the dialog's content fill the screen instead of Dialog's default wrap-content
 * width.
 *
 * Back handling: [Dialog] already intercepts system/predictive back and calls [onDismiss] by
 * default (its own `onDismissRequest`), which composes fine with the manifest's
 * `enableOnBackInvokedCallback="true"` from M4a — no separate `BackHandler` is needed for the
 * dismiss itself. It's still declared here (scoped to this composable, i.e. only while the
 * dialog is in composition) purely as a defensive backstop matching PROGRESS.md's ask that back
 * closes *only* the modal, not the details screen underneath; since the caller unconditionally
 * removes this composable from composition on dismiss, both mechanisms agree.
 */
@Composable
fun TrailerPlayerDialog(youTubeKey: String, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(modifier = Modifier.fillMaxWidth().background(Ink)) {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                TrailerWebView(youTubeKey = youTubeKey)
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Dimens.Gutter)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0x800B0B0D))
                    .clickableNoRipple(onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close trailer", tint = Chalk)
            }
        }
    }
}

/**
 * The YouTube IFrame Player embed. `javaScriptEnabled` is required — the IFrame API is JS-driven
 * — and `mediaPlaybackRequiresUserGesture = false` is required for `autoplay=1` to actually fire:
 * tapping the Trailer button satisfies Android's *activity-launch* gesture requirement, but the
 * WebView has its own, separate in-page autoplay gate that a same-session Activity-level tap
 * doesn't automatically satisfy once control has passed into the embedded page.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun TrailerWebView(youTubeKey: String) {
    val context = LocalContext.current
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = {
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                settings.javaScriptEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                // The IFrame Player API stores its player state in localStorage; without DOM
                // storage enabled (off by default on a bare WebView) it fails with YouTube's own
                // "Video player configuration error", not a network or key problem.
                settings.domStorageEnabled = true
                setBackgroundColor(android.graphics.Color.BLACK)
                loadUrl("https://www.youtube.com/embed/$youTubeKey?autoplay=1&playsinline=1")
            }
        },
    )
}
