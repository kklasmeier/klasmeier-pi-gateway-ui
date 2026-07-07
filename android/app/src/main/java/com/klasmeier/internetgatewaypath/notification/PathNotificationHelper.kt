package com.klasmeier.internetgatewaypath.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.klasmeier.internetgatewaypath.MainActivity
import com.klasmeier.internetgatewaypath.R
import com.klasmeier.internetgatewaypath.data.InternetPath
import java.text.DateFormat
import java.util.Date

class PathNotificationHelper(private val context: Context) {
    fun showPathChange(previous: InternetPath, current: InternetPath) {
        ensureChannel()
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("Now using ${label(current)}")
            .setContentText("Was: ${label(previous)} · $time")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Was: ${label(previous)} · $time\nTap to open",
                ),
            )
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun label(path: InternetPath): String = when (path) {
        InternetPath.OBSCURA -> context.getString(R.string.path_obscura)
        InternetPath.HOME -> context.getString(R.string.path_home)
        InternetPath.PHONE -> context.getString(R.string.path_phone)
        InternetPath.UNKNOWN, InternetPath.CHECK_FAILED -> context.getString(R.string.path_unknown)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_desc)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "path_changes"
        private const val NOTIFICATION_ID = 1001
    }
}
