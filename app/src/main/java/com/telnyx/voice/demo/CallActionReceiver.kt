package com.telnyx.voice.demo

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModelProvider
import com.telnyx.webrtc.common.TelnyxCommon
import timber.log.Timber
import java.util.UUID

class CallActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val callId = intent.getStringExtra(EXTRA_CALL_ID) ?: return
        val provider = intent.getStringExtra(EXTRA_PROVIDER)
        val pushMetadata = intent.getStringExtra(HybridFirebaseMessagingService.EXTRA_PUSH_METADATA)

        Timber.d("CallActionReceiver: action=${intent.action}, provider=$provider, callId=$callId, hasPushMetadata=${pushMetadata != null}")

        when (intent.action) {
            ACTION_ANSWER_CALL -> {
                // REMOVED: Answer action is now handled directly by MainActivity
                // to avoid Android 12+ trampoline restriction.
                // The notification now uses PendingIntent.getActivity() to launch MainActivity directly.
                Timber.w("Answer action received in BroadcastReceiver - this should not happen on Android 12+")
                return
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

                // Handle decline based on provider
                when (provider) {
                    "TELNYX" -> {
                        // Use telnyx_common's BackgroundCallDeclineService for Telnyx
                        // Note: telnyx_common handles this via its own BackgroundCallDeclineService
                        val app = context.applicationContext as? VoiceApplication
                        app?.let {
                            // For now, directly reject via TelnyxCommon since we're in foreground
                            // The proper telnyx_common flow will handle background decline
                            try {
                                TelnyxCommon.getInstance().currentCall?.endCall(UUID.fromString(callId))
                                Timber.d("Telnyx call declined via TelnyxCommon")
                            } catch (e: Exception) {
                                Timber.e(e, "Error declining Telnyx call")
                            }
                        }
                    }
                    "TWILIO" -> {
                        // Handle Twilio decline directly (no background service needed)
                        val app = context.applicationContext as? VoiceApplication
                        app?.twilioService?.rejectIncomingCall()
                        Timber.d("Twilio call declined")
                    }
                }
            }
            ACTION_END_CALL -> {
                // Handle end call from ongoing call notification
                Timber.d("End call action from ongoing notification: callId=$callId, provider=${provider ?: "not specified"}")

                try {
                    val app = context.applicationContext as? VoiceApplication
                    app?.let {
                        when (provider) {
                            "TELNYX" -> {
                                // Use TelnyxCommon to end the call
                                try {
                                    TelnyxCommon.getInstance().currentCall?.endCall(UUID.fromString(callId))
                                    Timber.d("Call ended via TelnyxCommon")
                                } catch (e: IllegalArgumentException) {
                                    Timber.e(e, "Invalid UUID format for callId: $callId")
                                }
                            }
                            "TWILIO" -> {
                                it.twilioService.endCall()
                                Timber.d("Call ended via Twilio service")
                            }
                            else -> {
                                // Fallback: try both
                                Timber.w("Unknown provider: $provider, trying both services")
                                try {
                                    TelnyxCommon.getInstance().currentCall?.endCall(UUID.fromString(callId))
                                } catch (e: IllegalArgumentException) {
                                    Timber.e(e, "Invalid UUID format for callId: $callId")
                                }
                                it.twilioService.endCall()
                                Timber.d("Call ended via both services")
                            }
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
