package com.telnyx.voice.demo

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.telnyx.voice.demo.telnyx.notification.CallNotificationService
import com.telnyx.webrtc.sdk.model.PushMetaData
import com.twilio.voice.CallInvite
import com.twilio.voice.CancelledCallInvite
import com.twilio.voice.MessageListener
import com.twilio.voice.Voice

class HybridFirebaseMessagingService : FirebaseMessagingService() {

    private var callNotificationService: CallNotificationService? = null

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
                                    Log.d(TAG, "Twilio Call Invite Received")
                                }

                                override fun onCancelledCallInvite(
                                        cancelledCallInvite: CancelledCallInvite,
                                        callException: com.twilio.voice.CallException?
                                ) {
                                    Log.d(
                                            TAG,
                                            "Twilio Call Invite Cancelled with error: ${callException?.message}"
                                    )
                                }
                            }
                    )

            if (handledByTwilio) {
                Log.d(TAG, "Message handled by Twilio")
            } else {
                Log.d(TAG, "Message not handled by Twilio, trying Telnyx")

                // Initialize CallNotificationService if needed
                if (callNotificationService == null) {
                    callNotificationService = CallNotificationService(this)
                }

                try {
                    val metadata = remoteMessage.data["metadata"]
                    if (metadata != null) {
                        val telnyxPushMetadata = Gson().fromJson(metadata, PushMetaData::class.java)
                        telnyxPushMetadata?.let {
                            Log.d(TAG, "Telnyx Push Received: $it")
                            callNotificationService?.showIncomingCallNotification(it)
                            return
                        }
                    }
                } catch (e: JsonSyntaxException) {
                    Log.e(TAG, "Error parsing push metadata JSON", e)
                }

                // Fallback or other handling
                CallManager.getTelnyxClient()?.handlePush(remoteMessage.data)
            }
        }
    }

    companion object {
        private const val TAG = "HybridFCMService"
    }
}
