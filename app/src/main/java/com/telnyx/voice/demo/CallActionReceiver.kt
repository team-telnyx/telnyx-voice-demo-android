package com.telnyx.voice.demo

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import timber.log.Timber

class CallActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val callId = intent.getStringExtra(EXTRA_CALL_ID) ?: return
        val provider = intent.getStringExtra(EXTRA_PROVIDER)

        Timber.d("CallActionReceiver: action=${intent.action}, provider=$provider, callId=$callId")

        when (intent.action) {
            ACTION_ANSWER_CALL -> {
                // Provider is required for answer action
                if (provider == null) {
                    Timber.e("Provider is null for ACTION_ANSWER_CALL, cannot proceed")
                    return
                }

                // Launch MainActivity with answer action
                // MainActivity will handle connecting and answering the call
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    action = ACTION_ANSWER_CALL
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(EXTRA_CALL_ID, callId)
                    putExtra(EXTRA_PROVIDER, provider)
                    putExtra(EXTRA_FROM_NOTIFICATION, true)
                }
                context.startActivity(launchIntent)
                Timber.d("Launching MainActivity to answer call")
            }
            ACTION_DECLINE_CALL -> {
                // Provider is required for decline action
                if (provider == null) {
                    Timber.e("Provider is null for ACTION_DECLINE_CALL, cannot proceed")
                    return
                }

                // Dismiss notification
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(callId.hashCode())

                // Use background service to decline without launching app
                BackgroundCallDeclineService.start(context, callId, provider)
                Timber.d("Started BackgroundCallDeclineService for call decline")
            }
            ACTION_END_CALL -> {
                // Handle end call from ongoing call notification
                // Provider is optional for end call - will try both if not specified
                Timber.d("End call action from ongoing notification: callId=$callId, provider=${provider ?: "not specified"}")

                try {
                    val app = context.applicationContext as? VoiceApplication
                    app?.let {
                        if (provider != null) {
                            // Use specified provider
                            when (provider) {
                                "TELNYX" -> {
                                    it.telnyxService.endCall()
                                    Timber.d("Call ended via Telnyx service")
                                }
                                "TWILIO" -> {
                                    it.twilioService.endCall()
                                    Timber.d("Call ended via Twilio service")
                                }
                                else -> {
                                    Timber.w("Unknown provider: $provider, trying both services")
                                    it.telnyxService.endCall()
                                    it.twilioService.endCall()
                                    Timber.d("Call ended via both services (unknown provider)")
                                }
                            }
                        } else {
                            // Fallback: try both services
                            Timber.d("Provider not specified, trying both services")
                            it.telnyxService.endCall()
                            it.twilioService.endCall()
                            Timber.d("Call ended via both services (no provider specified)")
                        }
                    } ?: run {
                        Timber.e("VoiceApplication not found, cannot end call")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error ending call from notification")
                }
            }
        }
    }

    companion object {
        const val ACTION_ANSWER_CALL = "com.telnyx.voice.demo.ACTION_ANSWER_CALL"
        const val ACTION_DECLINE_CALL = "com.telnyx.voice.demo.ACTION_DECLINE_CALL"
        const val ACTION_END_CALL = "com.telnyx.voice.demo.ACTION_END_CALL"
        const val EXTRA_CALL_ID = "callId"
        const val EXTRA_PROVIDER = "provider"
        const val EXTRA_FROM_NOTIFICATION = "fromNotification"
    }
}
