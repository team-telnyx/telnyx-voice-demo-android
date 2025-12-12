package com.telnyx.voice.demo

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.twilio.voice.Voice
import com.twilio.voice.MessageListener
import com.twilio.voice.CallInvite
import com.twilio.voice.CancelledCallInvite

class HybridFirebaseMessagingService : FirebaseMessagingService() {

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
            val handledByTwilio = Voice.handleMessage(this, remoteMessage.data, object : MessageListener {
                override fun onCallInvite(callInvite: CallInvite) {
                    Log.d(TAG, "Twilio Call Invite Received")
                    // TwilioClient should handle this via its listener if registered?
                    // Actually, Voice.handleMessage triggers the listener passed to it.
                    // We might need to notify TwilioClient or show UI.
                    // For this demo, we'll just log.
                }

                override fun onCancelledCallInvite(cancelledCallInvite: CancelledCallInvite, callException: com.twilio.voice.CallException?) {
                    Log.d(TAG, "Twilio Call Invite Cancelled with error: ${callException?.message}")
                }
            })

            if (handledByTwilio) {
                Log.d(TAG, "Message handled by Twilio")
            } else {
                Log.d(TAG, "Message not handled by Twilio, trying Telnyx")
                // Try to parse as Telnyx
                // val pushMetaData = TxPushMetaData.fromJson(remoteMessage.data)
                // if (pushMetaData != null) {
                //     Log.d(TAG, "Telnyx Push Received: $pushMetaData")
                //     // Show notification or notify CallManager
                // }
                CallManager.getTelnyxClient()?.handlePush(remoteMessage.data)
            }
        }
    }

    companion object {
        private const val TAG = "HybridFCMService"
    }
}
