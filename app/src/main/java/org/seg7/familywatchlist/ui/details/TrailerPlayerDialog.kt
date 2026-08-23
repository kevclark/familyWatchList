package org.seg7.familywatchlist.ui.details

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
 * Back handling: originally [Dialog]'s own `dismissOnBackPress` (default `true`) intercepted
 * system/predictive back and called [onDismiss] directly at the Android level — fine for a
 * single-stage dismiss, but presenting a real fullscreen `View` (below) needs a two-stage back:
 * exit fullscreen first, close the dialog on a second press. `dismissOnBackPress = false` turns
 * off the Dialog's own handling and hands back entirely to the single [BackHandler] below, which
 * branches on `fullscreenView` at call time (via [onBackPressed], a [rememberUpdatedState] so the
 * handler always reads current state without needing to re-register). Confirmed live on a real
 * emulator for the full sequence: fullscreen → back exits fullscreen back to the normal 16:9
 * player → back again closes the dialog.
 *
 * Fullscreen (YouTube's own on-screen control, not this app's UI): the [WebChromeClient] callback
 * pair `onShowCustomView`/`onHideCustomView` — the standard Android hook for a WebView page that
 * calls the Fullscreen API — is wired from [TrailerWebView] up into [fullscreenView]/
 * [fullscreenCallback] state held here, so this composable can render either the normal 16:9
 * embed or a full-size overlay showing YouTube's own fullscreen `View` on top of it.
 */
@Composable
fun TrailerPlayerDialog(youTubeKey: String, onDismiss: () -> Unit) {
    var fullscreenView by remember { mutableStateOf<View?>(null) }
    var fullscreenCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    // rememberUpdatedState so the single BackHandler below always reads current fullscreen state
    // without needing to unregister/re-register its callback on every fullscreenView change.
    val onBackPressed = rememberUpdatedState<() -> Unit> {
        if (fullscreenView != null) {
            fullscreenCallback?.onCustomViewHidden()
            fullscreenView = null
            fullscreenCallback = null
        } else {
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        // dismissOnBackPress = false: the Dialog's own default back handling would unconditionally
        // dismiss (see the two-stage back explanation above) — BackHandler below owns back
        // handling instead.
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false),
    ) {
        BackHandler(onBack = { onBackPressed.value() })
        // M8: fillMaxSize (not fillMaxWidth) + BoxWithConstraints below so the 16:9 embed is
        // letterboxed against whichever dimension is tighter. In portrait the screen is narrower
        // than it is tall, so width is the binding constraint (as it always was); in landscape
        // the screen is wider than a 16:9 box computed off full width would be tall, so height
        // becomes the binding constraint instead — without this a width-derived box in landscape
        // could ask for more height than the screen has. The outer Box.fillMaxSize also gives the
        // Ink background full-dialog coverage, so the letterboxed bars either side/above-below
        // read as deliberate rather than leaving the scrim showing through.
        Box(modifier = Modifier.fillMaxSize().background(Ink)) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val targetWidth = minOf(maxWidth, maxHeight * 16f / 9f)
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .width(targetWidth)
                        .aspectRatio(16f / 9f),
                ) {
                    TrailerWebView(
                        youTubeKey = youTubeKey,
                        onFullscreenShow = { view, callback ->
                            fullscreenView = view
                            fullscreenCallback = callback
                        },
                        onFullscreenHide = {
                            fullscreenView = null
                            fullscreenCallback = null
                        },
                    )
                }
            }
            if (fullscreenView == null) {
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
            fullscreenView?.let { view ->
                key(view) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize().background(Color.Black),
                        factory = {
                            (view.parent as? ViewGroup)?.removeView(view)
                            view
                        },
                    )
                }
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
 *
 * A [WebChromeClient] granting [PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID] is kept for
 * DRM-protected trailer streams (a bare `WebView` has no EME/Widevine permission wired up the way
 * Chrome grants it automatically) — but this alone does **not** fix YouTube's "Error 153, Video
 * player configuration error", confirmed by Kev on a real Widevine-capable device. The actual
 * cause: since late 2025 YouTube's embedded player strictly requires a valid `Referer` identifying
 * the embedding page. Navigating the `WebView` directly to
 * `youtube.com/embed/...` via [WebView.loadUrl] makes that URL the top-level document with no
 * parent page at all, so there's no referrer for YouTube to check — hence Error 153, unrelated to
 * DRM/EME. The fix: load a tiny local HTML wrapper via [WebView.loadDataWithBaseURL] containing a
 * real `<iframe>` pointed at the embed URL. The iframe's own navigation then carries a genuine
 * `Referer` derived from the wrapper page's origin. The base URL passed to
 * `loadDataWithBaseURL` (`https://familywatchlist.app/`, chosen to read as this app's own origin;
 * it doesn't need to resolve to anything, `loadDataWithBaseURL` never fetches it — it only needs
 * to be a genuine `https://` origin string, not `null`/`about:blank`/`file://`, for the WebView to
 * treat the page as having a legitimate origin) becomes the referrer's origin.
 * `referrerpolicy="strict-origin-when-cross-origin"` on the iframe controls exactly what's sent:
 * the base origin only, not the full wrapper URL/path.
 *
 * The [WebChromeClient] grant is unconditional (no `request.origin` check) because this `WebView`
 * only ever loads content we construct ourselves (the wrapper HTML, whose only child navigation is
 * the fixed `youtube.com/embed/...` URL) — there's no arbitrary/untrusted content that could
 * request this permission.
 *
 * Fullscreen: a nested `<iframe>` can't call the Fullscreen API at all unless its parent page
 * explicitly permits it, so the iframe carries both `fullscreen` in its `allow` list (the modern
 * Permissions-Policy-based mechanism) *and* the legacy boolean `allowfullscreen` attribute (still
 * what some Chromium/WebView versions check) — belt and braces, since which one a given
 * WebView-backing Chromium build honours isn't guaranteed. That only grants the *browser-side*
 * permission to request fullscreen; presenting it is Android's job via
 * [WebChromeClient.onShowCustomView]/[WebChromeClient.onHideCustomView], which fire when the
 * page's JS successfully enters/exits fullscreen. Both callbacks are forwarded to the
 * `onFullscreenShow`/`onFullscreenHide` lambdas so [TrailerPlayerDialog] — which owns the
 * Compose-side fullscreen state — can swap in YouTube's own fullscreen `View` as an overlay.
 *
 * M9: the embed URL previously carried `playsinline=1`. That parameter's whole purpose is telling
 * the *browser*, not just iOS Safari specifically, to keep video playback inline and never hand
 * off to a native/system fullscreen surface — directly opposed to what [onShowCustomView] needs
 * (it only fires when the page's JS actually invokes the real Fullscreen API instead of an
 * in-page CSS resize). On the emulator's WebView/Chromium build this had no visible effect and
 * `onShowCustomView` fired anyway, but on Kev's real phone it didn't: tapping YouTube's fullscreen
 * control there paused playback and resized the video in place while the status bar stayed
 * visible — YouTube's in-page CSS fallback, not real fullscreen. `playsinline` is documented by
 * YouTube itself as primarily an iOS Safari concern; this app is Android-only, so it's dropped
 * here rather than made conditional. Checked for the opposite regression this risks — Android
 * doesn't auto-invoke fullscreen just because `autoplay=1` fires; entering fullscreen still
 * requires the page's JS to call the Fullscreen API, which YouTube's player only does from an
 * explicit user tap on its own fullscreen control — confirmed live on the emulator: autoplay
 * starts inline in the normal 16:9 dialog, no forced fullscreen jump.
 */
/**
 * M9: extracted so the URL YouTube actually gets built with is independently unit-testable
 * (JVM, no `WebView`) without depending on the composable factory lambda executing. Deliberately
 * no `playsinline=1` — see the doc comment above [TrailerWebView] for why.
 */
internal fun trailerEmbedUrl(youTubeKey: String): String =
    "https://www.youtube.com/embed/$youTubeKey?autoplay=1"

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun TrailerWebView(
    youTubeKey: String,
    onFullscreenShow: (View, WebChromeClient.CustomViewCallback) -> Unit,
    onFullscreenHide: () -> Unit,
) {
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
                webChromeClient = object : WebChromeClient() {
                    override fun onPermissionRequest(request: PermissionRequest) {
                        request.grant(arrayOf(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID))
                    }

                    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                        onFullscreenShow(view, callback)
                    }

                    override fun onHideCustomView() {
                        onFullscreenHide()
                    }
                }
                val embedUrl = trailerEmbedUrl(youTubeKey)
                val html = """
                    <!DOCTYPE html>
                    <html><head><style>html,body{height:100%;width:100%;margin:0;padding:0;background:#000}iframe{width:100%;height:100%;border:0}</style></head>
                    <body>
                        <iframe src="$embedUrl"
                                referrerpolicy="strict-origin-when-cross-origin"
                                allow="autoplay; encrypted-media; fullscreen"
                                allowfullscreen
                                frameborder="0"></iframe>
                    </body></html>
                """.trimIndent()
                loadDataWithBaseURL(
                    "https://familywatchlist.app/",
                    html,
                    "text/html",
                    "UTF-8",
                    null,
                )
            }
        },
    )
}
