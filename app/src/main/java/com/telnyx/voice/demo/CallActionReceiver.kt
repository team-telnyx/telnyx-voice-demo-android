package com.telnyx.voice.demo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import timber.log.Timber

class CallActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val callId = intent.getStringExtra(EXTRA_CALL_ID) ?: return
        val provider = intent.getStringExtra(EXTRA_PROVIDER) ?: return

        val app = context.applicationContext as VoiceApplication

        when (intent.action) {
            ACTION_ANSWER_CALL -> {
                Timber.d("Answer call action: provider=$provider, callId=$callId")
                // Answer call via service
                when (provider) {
                    "TELNYX" -> app.telnyxService.answerCall()
                    "TWILIO" -> app.twilioService.acceptIncomingCall()
                }

                // Launch app to foreground
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(EXTRA_FROM_NOTIFICATION, true)
                }
                context.startActivity(launchIntent)
            }
            ACTION_DECLINE_CALL -> {
                Timber.d("Decline call action: provider=$provider, callId=$callId")
                when (provider) {
                    "TELNYX" -> app.telnyxService.endCall()
                    "TWILIO" -> app.twilioService.rejectIncomingCall()
                }
            }
        }
    }

    companion object {
        const val ACTION_ANSWER_CALL = "com.telnyx.voice.demo.ACTION_ANSWER_CALL"
        const val ACTION_DECLINE_CALL = "com.telnyx.voice.demo.ACTION_DECLINE_CALL"
        const val EXTRA_CALL_ID = "callId"
        const val EXTRA_PROVIDER = "provider"
        const val EXTRA_FROM_NOTIFICATION = "fromNotification"
    }
}
