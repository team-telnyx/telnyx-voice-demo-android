package com.telnyx.voice.demo.notification

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import com.telnyx.voice.demo.CallActionReceiver
import com.telnyx.voice.demo.MainActivity
import com.telnyx.voice.demo.R
import com.telnyx.voice.demo.models.CallInfo
import com.telnyx.voice.demo.models.Provider
import timber.log.Timber

/**
 * Centralized notification service for creating and managing call notifications.
 * Supports modern CallStyle notifications on Android 12+ with fallback for older versions.
 */
class CallNotificationService(
    private val context: Context,
    private val receiverClass: Class<*>
) {

    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHANNEL_ID = "telnyx_call_notification_channel"
        const val CHANNEL_ONGOING_ID = "telnyx_call_ongoing_channel"
        const val NOTIFICATION_ID = 1234
        const val NOTIFICATION_ACTION = "NOTIFICATION_ACTION"

        private const val ANSWER_REQUEST_CODE = 0
        private const val REJECT_REQUEST_CODE = 1
        private const val END_CALL_REQUEST_CODE = 2
    }

    enum class NotificationState(val value: Int) {
        ANSWER(0),
        REJECT(1),
        CANCEL(2)
    }

    init {
        createNotificationChannels()
    }

    /**
     * Shows an incoming call notification
     */
    fun showIncomingCallNotification(callInfo: CallInfo, provider: Provider) {
        val notification = createIncomingCallNotification(callInfo, provider)
        notificationManager.notify(NOTIFICATION_ID, notification)
        Timber.d("Incoming call notification shown for ${callInfo.remoteName ?: callInfo.remoteNumber}")
    }

    /**
     * Creates an incoming call notification with CallStyle support on Android 12+
     */
    fun createIncomingCallNotification(callInfo: CallInfo, provider: Provider): Notification {
        // Create full-screen intent to launch app
        val fullScreenIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            action = CallActionReceiver.ACTION_ANSWER_CALL
            putExtra(CallActionReceiver.EXTRA_CALL_ID, callInfo.callId)
            putExtra(CallActionReceiver.EXTRA_PROVIDER, provider.name)
            putExtra(CallActionReceiver.EXTRA_FROM_NOTIFICATION, true)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            ANSWER_REQUEST_CODE,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Create answer action
        val answerIntent = Intent(context, receiverClass).apply {
            action = CallActionReceiver.ACTION_ANSWER_CALL
            putExtra(CallActionReceiver.EXTRA_CALL_ID, callInfo.callId)
            putExtra(CallActionReceiver.EXTRA_PROVIDER, provider.name)
        }
        val answerPendingIntent = PendingIntent.getBroadcast(
            context,
            ANSWER_REQUEST_CODE,
            answerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Create reject action
        val rejectIntent = Intent(context, receiverClass).apply {
            action = CallActionReceiver.ACTION_DECLINE_CALL
            putExtra(CallActionReceiver.EXTRA_CALL_ID, callInfo.callId)
            putExtra(CallActionReceiver.EXTRA_PROVIDER, provider.name)
        }
        val rejectPendingIntent = PendingIntent.getBroadcast(
            context,
            REJECT_REQUEST_CODE,
            rejectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val callerName = callInfo.remoteName ?: callInfo.remoteNumber
        val providerName = provider.name

        // Check if we should use CallStyle
        val useCallStyle = useCallStyleNotification()

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_contact_phone)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)

        if (useCallStyle && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Use modern CallStyle for Android 12+
            val person = Person.Builder()
                .setName(callerName)
                .setImportant(true)
                .build()

            builder.setStyle(
                NotificationCompat.CallStyle.forIncomingCall(
                    person,
                    rejectPendingIntent,
                    answerPendingIntent
                )
            )
            Timber.d("Using CallStyle for incoming call notification")
        } else {
            // Fallback to traditional notification
            builder
                .setContentTitle("Incoming $providerName Call")
                .setContentText(callerName)
                .addAction(
                    R.drawable.ic_call_white,
                    "Answer",
                    answerPendingIntent
                )
                .addAction(
                    R.drawable.ic_call_end_white,
                    "Decline",
                    rejectPendingIntent
                )
            Timber.d("Using traditional notification style")
        }

        return builder.build()
    }

    /**
     * Shows an ongoing call notification
     */
    fun showOngoingCallNotification(callInfo: CallInfo, provider: Provider) {
        val notification = createOngoingCallNotification(callInfo, provider)
        notificationManager.notify(NOTIFICATION_ID, notification)
        Timber.d("Ongoing call notification shown for ${callInfo.remoteName ?: callInfo.remoteNumber}")
    }

    /**
     * Creates an ongoing call notification with CallStyle support on Android 12+
     */
    fun createOngoingCallNotification(callInfo: CallInfo, provider: Provider): Notification {
        // Create intent to open app
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Create end call action
        val endCallIntent = Intent(context, receiverClass).apply {
            action = CallActionReceiver.ACTION_END_CALL
            putExtra(CallActionReceiver.EXTRA_CALL_ID, callInfo.callId)
            putExtra(CallActionReceiver.EXTRA_PROVIDER, provider.name)
        }
        val endCallPendingIntent = PendingIntent.getBroadcast(
            context,
            END_CALL_REQUEST_CODE,
            endCallIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val callerName = callInfo.remoteName ?: callInfo.remoteNumber

        // Check if we should use CallStyle
        val useCallStyle = useCallStyleNotification()

        val builder = NotificationCompat.Builder(context, CHANNEL_ONGOING_ID)
            .setSmallIcon(R.drawable.ic_stat_contact_phone)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openAppPendingIntent)
            .setAutoCancel(false)

        if (useCallStyle && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Use modern CallStyle for Android 12+
            val person = Person.Builder()
                .setName(callerName)
                .setImportant(true)
                .build()

            builder.setStyle(
                NotificationCompat.CallStyle.forOngoingCall(
                    person,
                    endCallPendingIntent
                )
            )
            Timber.d("Using CallStyle for ongoing call notification")
        } else {
            // Fallback to traditional notification
            builder
                .setContentTitle("Ongoing Call")
                .setContentText(callerName)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .addAction(
                    R.drawable.ic_call_end_white,
                    "End Call",
                    endCallPendingIntent
                )
            Timber.d("Using traditional notification style for ongoing call")
        }

        return builder.build()
    }

    /**
     * Cancels the notification
     */
    fun cancelNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
        Timber.d("Call notification cancelled")
    }

    /**
     * Creates notification channels for incoming and ongoing calls
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Incoming call channel (HIGH importance)
            val incomingChannel = NotificationChannel(
                CHANNEL_ID,
                "Incoming Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for incoming voice calls"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                    android.media.AudioAttributes.Builder()
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .build()
                )
                enableLights(true)
                enableVibration(true)
            }

            // Ongoing call channel (LOW importance)
            val ongoingChannel = NotificationChannel(
                CHANNEL_ONGOING_ID,
                "Active Calls",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows ongoing call status"
                setShowBadge(false)
                setSound(null, null)
            }

            notificationManager.createNotificationChannel(incomingChannel)
            notificationManager.createNotificationChannel(ongoingChannel)

            Timber.d("Notification channels created")
        }
    }

    /**
     * Determines if CallStyle notification should be used.
     * CallStyle is used on Android 12+ when the device is unlocked.
     */
    private fun useCallStyleNotification(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return false
        }

        // Check if device is locked
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val isLocked = keyguardManager?.isKeyguardLocked ?: false

        // Use CallStyle only when unlocked for better UX
        return !isLocked
    }
}
