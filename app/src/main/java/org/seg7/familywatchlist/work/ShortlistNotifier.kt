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
     */
    fun notifyShortlistReady(context: Context) {
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
            .setContentText("Fresh personalised picks are waiting on Home.")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}
