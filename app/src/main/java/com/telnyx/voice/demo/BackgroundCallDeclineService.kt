package com.telnyx.voice.demo

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.*
import timber.log.Timber

/**
 * Background service for declining incoming calls without launching the main app.
 * Based on official Telnyx implementation pattern.
 */
class BackgroundCallDeclineService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var timeoutJob: Job? = null
    private var declineJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val callId = intent?.getStringExtra(EXTRA_CALL_ID)
        val provider = intent?.getStringExtra(EXTRA_PROVIDER)

        Timber.d("BackgroundCallDeclineService: Declining call - provider=$provider, callId=$callId")

        if (provider == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Set up timeout protection
        timeoutJob = serviceScope.launch {
            delay(CONNECTION_TIMEOUT_MS)
            Timber.w("BackgroundCallDeclineService: Timeout reached, stopping service")
            stopSelf()
        }

        // Decline the call
        declineJob = serviceScope.launch {
            try {
                val app = application as VoiceApplication

                when (provider) {
                    "TELNYX" -> {
                        if (app.telnyxService.hasPendingPushMetadata()) {
                            Timber.d("Declining Telnyx call from push")
                            app.telnyxService.declineFromPush()
                        } else {
                            Timber.d("Declining Telnyx call (socket connected)")
                            app.telnyxService.endCall()
                        }
                    }
                    "TWILIO" -> {
                        Timber.d("Declining Twilio call")
                        app.twilioService.rejectIncomingCall()
                    }
                }

                // Give a moment for the decline to process
                delay(500)

                Timber.d("Call declined successfully, stopping service")
                stopSelf()

            } catch (e: Exception) {
                Timber.e(e, "Error declining call in background")
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        timeoutJob?.cancel()
        declineJob?.cancel()
        serviceScope.cancel()
        Timber.d("BackgroundCallDeclineService destroyed")
    }

    companion object {
        private const val CONNECTION_TIMEOUT_MS = 10000L // 10 seconds
        private const val EXTRA_CALL_ID = "callId"
        private const val EXTRA_PROVIDER = "provider"

        fun start(context: Context, callId: String, provider: String) {
            val intent = Intent(context, BackgroundCallDeclineService::class.java).apply {
                putExtra(EXTRA_CALL_ID, callId)
                putExtra(EXTRA_PROVIDER, provider)
            }
            context.startService(intent)
        }
    }
}
