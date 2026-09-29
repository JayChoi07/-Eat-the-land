package com.jaychoi.eattheland.tracking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.jaychoi.eattheland.MainActivity
import com.jaychoi.eattheland.R

/** minSdk 26 이라 채널 생성자가 있는 플랫폼 Notification.Builder 를 그대로 쓴다(compat 불필요). */
internal object TrackingNotification {
    const val ID = 1
    private const val CHANNEL_ID = "tracking"
    private const val REQUEST_OPEN = 0
    private const val REQUEST_STOP = 1

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.tracking_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    fun build(context: Context, capturedCount: Int): Notification {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = PendingIntent.getActivity(
            context,
            REQUEST_OPEN,
            Intent(context, MainActivity::class.java),
            flags,
        )
        val stop = PendingIntent.getService(
            context,
            REQUEST_STOP,
            LocationTrackingService.stopIntent(context),
            flags,
        )
        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_walk)
            .setContentTitle(context.getString(R.string.tracking_notification_title))
            .setContentText(context.getString(R.string.tracking_notification_text, capturedCount))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                Notification.Action.Builder(
                    null,
                    context.getString(R.string.tracking_notification_stop),
                    stop,
                ).build(),
            )
            .build()
    }

    fun update(context: Context, capturedCount: Int) {
        context.getSystemService(NotificationManager::class.java)
            .notify(ID, build(context, capturedCount))
    }
}
