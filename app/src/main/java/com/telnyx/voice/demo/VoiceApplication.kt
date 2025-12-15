package com.telnyx.voice.demo

import android.app.Application
import com.google.firebase.messaging.FirebaseMessaging
import com.telnyx.voice.demo.util.SettingsStorage
import com.telnyx.voice.logic.service.TelnyxService
import com.twilio.voice.logic.service.TwilioService
import timber.log.Timber

class VoiceApplication : Application() {

    lateinit var telnyxService: TelnyxService
        private set

    lateinit var twilioService: TwilioService
        private set

    var fcmToken: String? = null
        internal set

    override fun onCreate() {
        super.onCreate()

        // Initialize Timber for logging
        Timber.plant(Timber.DebugTree())

        // Initialize services
        telnyxService = TelnyxService(this)
        twilioService = TwilioService(this)

        // Fetch and log FCM token at launch
        fetchFcmToken()

        Timber.d("VoiceApplication initialized")
    }

    /**
     * Checks if Twilio needs registration based on:
     * - Valid credentials exist
     * - FCM token available
     * - Token has changed OR registration is > 6 months old
     */
    fun checkAndRegisterTwilioIfNeeded() {
        val twilioSettings = SettingsStorage.getTwilioSettings(this)
        val fcmToken = this.fcmToken

        // Check if Twilio credentials are configured
        if (!twilioSettings.hasValidCredentials) {
            Timber.d("Twilio credentials not configured, skipping auto-registration")
            return
        }

        // Check if FCM token is available
        if (fcmToken == null) {
            Timber.w("FCM token not available yet, skipping Twilio auto-registration")
            return
        }

        // Check if re-registration is needed
        if (SettingsStorage.needsTwilioReregistration(this, fcmToken)) {
            val reason = when {
                SettingsStorage.getTwilioRegisteredToken(this) == null -> "never registered"
                SettingsStorage.getTwilioRegisteredToken(this) != fcmToken -> "token changed"
                else -> "registration expired (>6 months)"
            }
            Timber.i("Twilio auto-registration triggered: $reason")

            // Note: We can't fetch token here directly as it requires backend call
            // This will be handled by MainScreen when it observes the token fetch state
        } else {
            Timber.d("Twilio registration is up to date, no re-registration needed")
        }
    }

    private fun fetchFcmToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Timber.w(task.exception, "Fetching FCM registration token failed")
                return@addOnCompleteListener
            }

            // Get new FCM registration token
            val token = task.result
            fcmToken = token

            // Save to SettingsStorage so it's available in Settings screen
            SettingsStorage.savePushToken(this@VoiceApplication, token)

            // Log the token prominently so it can be copied
            Timber.d("═════════════════════════════════════════════════════════")
            Timber.d("FCM TOKEN (copy this for push notifications):")
            Timber.d(token)
            Timber.d("═════════════════════════════════════════════════════════")
        }
    }
}
