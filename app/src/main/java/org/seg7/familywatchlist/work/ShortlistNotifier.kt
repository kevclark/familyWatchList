package org.seg7.familywatchlist.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.seg7.familywatchlist.MainActivity
import org.seg7.familywatchlist.R

/**
 * PLAN.md §4: the weekly job's local notification — "Your family shortlist is ready 🍿",
 * deep-linking to Home. `POST_NOTIFICATIONS` (Android 13+) is requested at onboarding
 * ([org.seg7.familywatchlist.ui.AppRoot]'s launcher); declining just means [notifyShortlistReady]
 * silently does nothing (checked here, not assumed) rather than throwing a `SecurityException`.
 *
 * **PLAN.md §4 "Per-profile notification control" (M3e):** [notifyShortlistReady] now takes the
 * specific list of profile display names whose refresh finished *and* passed both the master and
 * per-profile toggles ([NotificationGate.profilesToNotify], computed by
 * [RecommendationWorker] before calling here) — this function's own job stays narrowly "is the OS
 * gate open, and if so, post one notification naming these profiles"; it doesn't know or care
 * about the in-app toggles. **One batched notification per weekly run, not one per profile** —
 * see this file's own design-choice note below for why.
 */
object ShortlistNotifier {
    const val CHANNEL_ID: String = "weekly_shortlist"
    private const val NOTIFICATION_ID = 1001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Weekly shortlist",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Tells you when this week's personalised picks are ready"
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    /**
     * "Deep-links to Home" (PLAN.md §4): Home is [MainActivity]'s only reachable start
     * destination once a profile is active ([org.seg7.familywatchlist.ui.AppRoot]'s reactive
     * top-level switch), so simply relaunching the activity already lands there — no separate
     * deep-link URI scheme is needed for a single-destination target.
     *
     * **Design choice, M3e (documented per the task's explicit ask):** a single batched
     * notification listing whichever enabled profiles actually finished this run, e.g. "Kev's
     * and Family's picks are ready" — not one notification per enabled profile. Reasons:
     *  1. **Matches "today's existing behaviour exactly" cleanly for the all-on case** — before
     *     this pass there was always exactly one notification per weekly run; batching preserves
     *     that shape byte-for-byte (same [NOTIFICATION_ID], same single post) when every profile
     *     is enabled, rather than a behaviour change hiding inside what's supposed to be the
     *     no-op default.
     *  2. **Avoids notification spam for a full household.** Up to 10 individuals + Family is 11
     *     *possible* notifications in the worst case, every Monday morning, for one weekly event
     *     — a family watchlist app buzzing 11 times back-to-back is a worse UX than the toggle
     *     it's meant to reduce.
     *  3. **No per-profile notification-ID scheme needed.** [NOTIFICATION_ID] stays a single
     *     fixed constant; a later run's batch simply replaces the previous one
     *     (`setAutoCancel`/[NotificationManagerCompat.notify] with the same id), same as before.
     *
     * [profileNames] is [NotificationGate.profilesToNotify]'s output, already filtered to
     * profiles that (a) genuinely finished refreshing, (b) have the master toggle on, and (c)
     * have their own per-profile toggle on. An empty list means nothing to say — this posts
     * nothing at all rather than an empty/blank notification.
     */
    fun notifyShortlistReady(context: Context, profileNames: List<String>) {
        if (profileNames.isEmpty()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Your family shortlist is ready 🍿")
            .setContentText("${joinPossessive(profileNames)} picks are ready.")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    /** "Kev" -> "Kev's"; ["Kev", "Family"] -> "Kev's and Family's"; ["Kev", "Sam", "Family"] -> "Kev's, Sam's and Family's". */
    private fun joinPossessive(names: List<String>): String = when (names.size) {
        1 -> "${names[0]}'s"
        else -> names.dropLast(1).joinToString(", ") { "$it's" } + " and ${names.last()}'s"
    }
}
