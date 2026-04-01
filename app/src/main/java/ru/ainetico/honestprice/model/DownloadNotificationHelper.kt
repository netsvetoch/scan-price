package ru.ainetico.honestprice.model

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import ru.ainetico.honestprice.R

class DownloadNotificationHelper(private val context: Context) {

    companion object {
        private const val CHANNEL_SILENT = "model_download"
        private const val CHANNEL_ALERT = "model_ready"
        private const val NOTIF_SILENT = 1001
        private const val NOTIF_ALERT = 1002
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(CHANNEL_SILENT, context.getString(R.string.download_channel_silent), NotificationManager.IMPORTANCE_LOW)
            )
            notificationManager.createNotificationChannel(
                NotificationChannel(CHANNEL_ALERT, context.getString(R.string.download_channel_alert), NotificationManager.IMPORTANCE_HIGH)
            )
        }
    }

    fun showProgress(title: String, text: String, progress: Int) {
        val builder = NotificationCompat.Builder(context, CHANNEL_SILENT)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setProgress(100, progress.coerceIn(0, 100), false)
        notificationManager.notify(NOTIF_SILENT, builder.build())
    }

    fun cancelProgress() {
        notificationManager.cancel(NOTIF_SILENT)
    }

    fun showReadyNotification() {
        notificationManager.notify(NOTIF_ALERT,
            NotificationCompat.Builder(context, CHANNEL_ALERT)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(context.getString(R.string.download_ready_title))
                .setContentText(context.getString(R.string.download_ready_text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
        )
    }
}
