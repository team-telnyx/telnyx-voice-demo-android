package com.telnyx.voice.logic.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class CallForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START_SERVICE) {
            startForegroundService()
        } else if (intent?.action == ACTION_STOP_SERVICE) {
            stopForegroundService()
        }
        return START_NOT_STICKY
    }

    private fun startForegroundService() {
        createNotificationChannel()
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun stopForegroundService() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel =
                    NotificationChannel(
                            CHANNEL_ID,
                            "Call Foreground Service Channel",
                            NotificationManager.IMPORTANCE_DEFAULT
                    )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Active Call")
                .setContentText("You have an active call.")
                .setSmallIcon(android.R.drawable.ic_menu_call)
                .build()
    }

    companion object {
        private const val CHANNEL_ID = "CallForegroundServiceChannel"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_START_SERVICE = "ACTION_START_SERVICE"
        private const val ACTION_STOP_SERVICE = "ACTION_STOP_SERVICE"

        fun startService(context: Context, callId: String) {
            val intent =
                    Intent(context, CallForegroundService::class.java).apply {
                        action = ACTION_START_SERVICE
                        putExtra("callId", callId)
                    }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent =
                    Intent(context, CallForegroundService::class.java).apply {
                        action = ACTION_STOP_SERVICE
                    }
            context.stopService(intent)
        }
    }
}
