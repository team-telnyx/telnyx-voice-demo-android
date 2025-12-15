package com.telnyx.voice.demo

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.telnyx.webrtc.sdk.model.PushMetaData
import com.twilio.voice.CallInvite
import com.twilio.voice.CancelledCallInvite
import com.twilio.voice.MessageListener
import com.twilio.voice.Voice

class HybridFirebaseMessagingService : FirebaseMessagingService() {

    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed token: $token")
        // Notify CallManager or save token
        // For now we just log it, as registration happens in UI/CallManager
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "From: ${remoteMessage.from}")

        // Check if message contains data payload
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")

            // Try Twilio
            val handledByTwilio =
                    Voice.handleMessage(
                            this,
                            remoteMessage.data,
                            object : MessageListener {
                                override fun onCallInvite(callInvite: CallInvite) {
                                    Log.d(TAG, "Twilio Call Invite Received: ${callInvite.callSid}")
                                    handleTwilioCallInvite(callInvite)
                                }

                                override fun onCancelledCallInvite(
                                        cancelledCallInvite: CancelledCallInvite,
                                        callException: com.twilio.voice.CallException?
                                ) {
                                    Log.d(TAG, "Twilio Call Invite Cancelled: ${cancelledCallInvite.callSid}")
                                    handleTwilioCancelledCallInvite(cancelledCallInvite)
                                }
                            }
                    )

            if (handledByTwilio) {
                Log.d(TAG, "Message handled by Twilio")
            } else {
                Log.d(TAG, "Message not handled by Twilio, trying Telnyx")

                try {
                    val metadata = remoteMessage.data["metadata"]
                    if (metadata != null) {
                        val telnyxPushMetadata = Gson().fromJson(metadata, PushMetaData::class.java)
                        telnyxPushMetadata?.let {
                            Log.d(TAG, "Telnyx Push Received: callId=${it.callId}, caller=${it.callerName}")
                            handleTelnyxPush(it)
                            return
                        }
                    }
                } catch (e: JsonSyntaxException) {
                    Log.e(TAG, "Error parsing push metadata JSON", e)
                }
            }
        }
    }

    private fun handleTwilioCallInvite(callInvite: CallInvite) {
        val app = application as VoiceApplication
        app.twilioService.handleCallInvite(callInvite)

        // Show notification
        showIncomingCallNotification(
            callId = callInvite.callSid ?: "unknown",
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

        // Show notification
        showIncomingCallNotification(
            callId = metadata.callId?.toString() ?: "unknown",
            provider = "TELNYX",
            callerName = metadata.callerName ?: "Unknown",
            callerNumber = metadata.callerNumber ?: "Unknown"
        )
    }

    private fun showIncomingCallNotification(
        callId: String,
        provider: String,
        callerName: String,
        callerNumber: String
    ) {
        createNotificationChannel()

        // Answer intent
        val answerIntent = Intent(this, CallActionReceiver::class.java).apply {
            action = CallActionReceiver.ACTION_ANSWER_CALL
            putExtra(CallActionReceiver.EXTRA_CALL_ID, callId)
            putExtra(CallActionReceiver.EXTRA_PROVIDER, provider)
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

    companion object {
        private const val TAG = "HybridFCMService"
        private const val CHANNEL_ID = "IncomingCallChannel"
    }
}
