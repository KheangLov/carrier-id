package com.carrierid.app.call

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.carrierid.app.R

/**
 * Short-lived foreground service that keeps the process alive while the
 * overlay is visible (One UI is aggressive about killing background apps).
 * It stops itself as soon as the overlay is hidden.
 */
class CallWatcherService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.channel_name),
                    NotificationManager.IMPORTANCE_MIN)
            )
        }
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notif_title))
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "caller_id"
        private const val NOTIF_ID = 42
        private const val ACTION_STOP = "stop"

        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, CallWatcherService::class.java))
            } catch (e: Exception) {
                // Android 12+ can refuse background FGS starts. The overlay still
                // works; it just relies on its own timeout to disappear.
                Log.w("CallWatcher", "Could not start foreground service", e)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CallWatcherService::class.java))
        }
    }
}
