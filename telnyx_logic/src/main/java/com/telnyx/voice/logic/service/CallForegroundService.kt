package com.telnyx.voice.logic.service

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import timber.log.Timber

class CallForegroundService : Service() {

    private var callId: String? = null
    private var callerInfo: String? = null

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        Timber.d("CallForegroundService onCreate - service running flag set to true")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START_SERVICE) {
            callId = intent.getStringExtra(EXTRA_CALL_ID)
            callerInfo = intent.getStringExtra(EXTRA_CALLER_INFO)
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

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        Timber.d("CallForegroundService onDestroy - service running flag set to false")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel =
                    NotificationChannel(
                            CHANNEL_ID,
                            "Active Call",
                            NotificationManager.IMPORTANCE_LOW // Low importance for ongoing call
                    ).apply {
                        description = "Shows ongoing call status"
                        setShowBadge(false)
                    }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        // Create intent to open app when tapping notification
        val openAppIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPendingIntent = openAppIntent?.let {
            PendingIntent.getActivity(
                this,
                0,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        // Create end call action (will be handled by broadcast receiver in app module)
        val endCallIntent = Intent(ACTION_END_CALL).apply {
            setPackage(packageName)
            putExtra(EXTRA_CALL_ID, callId)
            putExtra(EXTRA_PROVIDER, "TELNYX")
        }
        val endCallPendingIntent = PendingIntent.getBroadcast(
            this,
            1,
            endCallIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Ongoing Call")
                .setContentText(callerInfo ?: "In call")
                .setSmallIcon(com.telnyx.voice.logic.R.drawable.ic_stat_contact_phone)
                .setOngoing(true) // Cannot be swiped away
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(openAppPendingIntent)
                .addAction(
                    com.telnyx.voice.logic.R.drawable.ic_call_end_white,
                    "End Call",
                    endCallPendingIntent
                )
                .setAutoCancel(false)
                .build()
    }

    companion object {
        private const val CHANNEL_ID = "CallForegroundServiceChannel"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_START_SERVICE = "ACTION_START_SERVICE"
        private const val ACTION_STOP_SERVICE = "ACTION_STOP_SERVICE"
        private const val ACTION_END_CALL = "com.telnyx.voice.demo.ACTION_END_CALL"
        private const val EXTRA_CALL_ID = "callId"
        private const val EXTRA_CALLER_INFO = "callerInfo"
        private const val EXTRA_PROVIDER = "provider"

        @Volatile
        private var isServiceRunning = false

        fun startService(context: Context, callId: String, callerInfo: String? = null) {
            // Check if already running
            if (isServiceRunning) {
                Timber.d("CallForegroundService already running, skipping start")
                return
            }

            // Double-check with system
            if (isServiceRunningInForeground(context)) {
                isServiceRunning = true
                Timber.d("CallForegroundService detected running in system, updating flag")
                return
            }

            val intent =
                    Intent(context, CallForegroundService::class.java).apply {
                        action = ACTION_START_SERVICE
                        putExtra(EXTRA_CALL_ID, callId)
                        putExtra(EXTRA_CALLER_INFO, callerInfo)
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
            isServiceRunning = false
        }

        private fun isServiceRunningInForeground(context: Context): Boolean {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            return manager.getRunningServices(Integer.MAX_VALUE).any { serviceInfo ->
                CallForegroundService::class.java.name == serviceInfo.service.className &&
                serviceInfo.foreground
            }
        }
    }
}
