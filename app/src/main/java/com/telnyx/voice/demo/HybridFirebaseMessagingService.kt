package com.telnyx.voice.demo

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.telnyx.voice.demo.util.SettingsStorage
import com.telnyx.webrtc.sdk.model.PushMetaData
import com.twilio.voice.CallInvite
import com.twilio.voice.CancelledCallInvite
import com.twilio.voice.MessageListener
import com.twilio.voice.Voice
import org.json.JSONObject
import timber.log.Timber

class HybridFirebaseMessagingService : FirebaseMessagingService() {

    private val notificationManager by lazy {
        getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Timber.tag(TAG).d("Refreshed token: $token")

        // Check if token has changed and Twilio needs re-registration
        val previousToken = SettingsStorage.getPushToken(this)
        val tokenChanged = previousToken != null && previousToken != token

        // Save new token
        SettingsStorage.savePushToken(this, token)

        // Log if Twilio needs re-registration
        if (tokenChanged && SettingsStorage.needsTwilioReregistration(this, token)) {
            Timber.tag(TAG).w("FCM token changed - Twilio re-registration required on next app launch")
        }

        // Update VoiceApplication
        (application as? VoiceApplication)?.let {
            it.fcmToken = token
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Timber.tag(TAG).d("From: ${remoteMessage.from}")

        // Check if message contains data payload
        if (remoteMessage.data.isNotEmpty()) {
            Timber.tag(TAG).d("Message data payload: ${remoteMessage.data}")

            // Try Twilio
            val handledByTwilio =
                    Voice.handleMessage(
                            this,
                            remoteMessage.data,
                            object : MessageListener {
                                override fun onCallInvite(callInvite: CallInvite) {
                                    Timber.tag(TAG)
                                        .d("Twilio Call Invite Received: ${callInvite.callSid}")
                                    handleTwilioCallInvite(callInvite)
                                }

                                override fun onCancelledCallInvite(
                                        cancelledCallInvite: CancelledCallInvite,
                                        callException: com.twilio.voice.CallException?
                                ) {
                                    Timber.tag(TAG)
                                        .d("Twilio Call Invite Cancelled: ${cancelledCallInvite.callSid}")
                                    handleTwilioCancelledCallInvite(cancelledCallInvite)
                                }
                            }
                    )

            if (handledByTwilio) {
                Timber.tag(TAG).d("Message handled by Twilio")
            } else {
                Timber.tag(TAG).d("Message not handled by Twilio, trying Telnyx")

                try {
                    // Check if this is a missed call notification
                    val objects = JSONObject(remoteMessage.data as Map<*, *>)
                    val message = objects.optString("message", "")
                    val isMissedCall = message == MISSED_CALL_MESSAGE

                    if (isMissedCall) {
                        Timber.tag(TAG).d("Missed call notification received")
                        val metadata = objects.optString("metadata", "")
                        if (metadata.isNotEmpty()) {
                            val telnyxPushMetadata = Gson().fromJson(metadata, PushMetaData::class.java)
                            telnyxPushMetadata?.let { pushData ->
                                handleTelnyxMissedCall(pushData)
                            }
                        }
                        return
                    }

                    // Handle incoming call
                    val metadata = remoteMessage.data["metadata"]
                    if (metadata != null) {
                        val telnyxPushMetadata = Gson().fromJson(metadata, PushMetaData::class.java)
                        telnyxPushMetadata?.let {
                            Timber.tag(TAG)
                                .d("Telnyx Push Received: callId=${it.callId}, caller=${it.callerName}")
                            handleTelnyxPush(it)
                            return
                        }
                    }
                } catch (e: JsonSyntaxException) {
                    Timber.tag(TAG).e(e, "Error parsing push metadata JSON")
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Error processing Telnyx push notification")
                }
            }
        }
    }

    private fun handleTwilioCallInvite(callInvite: CallInvite) {
        val app = application as VoiceApplication
        app.twilioService.handleCallInvite(callInvite)

        // Show notification
        showIncomingCallNotification(
            callId = callInvite.callSid,
            provider = "TWILIO",
            callerName = callInvite.from ?: "Unknown",
            callerNumber = callInvite.from ?: "Unknown"
        )
    }

    private fun handleTwilioCancelledCallInvite(cancelledCallInvite: CancelledCallInvite) {
        val app = application as VoiceApplication
        app.twilioService.handleCancelledCallInvite(cancelledCallInvite)

        // Dismiss notification
        notificationManager.cancel(cancelledCallInvite.callSid.hashCode())
    }

    private fun handleTelnyxPush(metadata: PushMetaData) {
        val app = application as VoiceApplication
        app.telnyxService.handlePushData(metadata)

        // Serialize metadata to JSON for passing through intent chain
        val metadataJson = Gson().toJson(metadata)

        // Show notification
        showIncomingCallNotification(
            callId = metadata.callId,
            provider = "TELNYX",
            callerName = metadata.callerName,
            callerNumber = metadata.callerNumber,
            pushMetadata = metadataJson
        )
    }

    private fun handleTelnyxMissedCall(metadata: PushMetaData) {
        // Dismiss any existing incoming call notification
        notificationManager.cancel(metadata.callId.hashCode())

        // Show missed call notification
        showMissedCallNotification(
            callId = metadata.callId,
            provider = "TELNYX",
            callerName = metadata.callerName,
            callerNumber = metadata.callerNumber
        )
    }

    private fun showIncomingCallNotification(
        callId: String,
        provider: String,
        callerName: String,
        callerNumber: String,
        pushMetadata: String? = null
    ) {
        createNotificationChannel()

        // Answer intent
        val answerIntent = Intent(this, CallActionReceiver::class.java).apply {
            action = CallActionReceiver.ACTION_ANSWER_CALL
            putExtra(CallActionReceiver.EXTRA_CALL_ID, callId)
            putExtra(CallActionReceiver.EXTRA_PROVIDER, provider)
            pushMetadata?.let { putExtra(EXTRA_PUSH_METADATA, it) }
        }
        val answerPendingIntent = PendingIntent.getBroadcast(
            this,
            callId.hashCode(),
            answerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Decline intent
        val declineIntent = Intent(this, CallActionReceiver::class.java).apply {
            action = CallActionReceiver.ACTION_DECLINE_CALL
            putExtra(CallActionReceiver.EXTRA_CALL_ID, callId)
            putExtra(CallActionReceiver.EXTRA_PROVIDER, provider)
        }
        val declinePendingIntent = PendingIntent.getBroadcast(
            this,
            callId.hashCode() + 1,
            declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Full screen intent
        val fullScreenIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(CallActionReceiver.EXTRA_FROM_NOTIFICATION, true)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            callId.hashCode() + 2,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("Incoming $provider Call")
            .setContentText("$callerName ($callerNumber)")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_call,
                "Answer",
                answerPendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Decline",
                declinePendingIntent
            )
            .setAutoCancel(true)
            .setOngoing(true)
            .build()

        notificationManager.notify(callId.hashCode(), notification)
    }

    private fun showMissedCallNotification(
        callId: String,
        provider: String,
        callerName: String,
        callerNumber: String
    ) {
        createMissedCallNotificationChannel()

        // Intent to open app
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            callId.hashCode(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val displayName = if (callerName.isNotEmpty()) {
            "$callerName ($callerNumber)"
        } else {
            callerNumber
        }

        val notification = NotificationCompat.Builder(this, MISSED_CALL_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_contact_phone)
            .setContentTitle("Missed $provider Call")
            .setContentText(displayName)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .setContentIntent(openAppPendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(callId.hashCode(), notification)
        Timber.tag(TAG).d("Missed call notification shown for $displayName")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Incoming Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for incoming voice calls"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createMissedCallNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                MISSED_CALL_CHANNEL_ID,
                "Missed Calls",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for missed voice calls"
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "HybridFCMService"
        private const val CHANNEL_ID = "IncomingCallChannel"
        private const val MISSED_CALL_CHANNEL_ID = "MissedCallChannel"
        private const val MISSED_CALL_MESSAGE = "Missed call!"
        const val EXTRA_PUSH_METADATA = "pushMetadata"
    }
}
