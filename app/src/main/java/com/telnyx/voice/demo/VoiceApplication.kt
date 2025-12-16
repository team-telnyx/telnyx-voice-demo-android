package com.telnyx.voice.demo

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import com.google.firebase.messaging.FirebaseMessaging
import com.telnyx.voice.demo.util.SettingsStorage
import com.twilio.voice.logic.service.TwilioService
import timber.log.Timber

class VoiceApplication : Application() {

    // Note: TelnyxViewModel is now managed by CallViewModel, not here

    lateinit var twilioService: TwilioService
        private set

    var fcmToken: String? = null
        internal set

    override fun onCreate() {
        super.onCreate()

        // Initialize Timber for logging
        Timber.plant(Timber.DebugTree())

        // Initialize Twilio service (Telnyx is managed by CallViewModel)
        twilioService = TwilioService(this)

        // Only fetch FCM token in main process, not in :call_service process
        // Firebase is not initialized in the separate :call_service process where
        // CallForegroundService runs
        if (isMainProcess()) {
            fetchFcmToken()
        }

        Timber.d("VoiceApplication initialized in process: ${getCurrentProcessName()}")
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

    /**
     * Checks if the current process is the main application process.
     * Returns true for main process (e.g., "com.telnyx.voice.demo"),
     * false for service processes (e.g., "com.telnyx.voice.demo:call_service")
     */
    private fun isMainProcess(): Boolean {
        val processName = getCurrentProcessName()
        return processName == packageName
    }

    /**
     * Gets the name of the current process.
     * Uses Application.getProcessName() on Android P+ (API 28+),
     * falls back to ActivityManager for older versions.
     */
    private fun getCurrentProcessName(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            // Fallback for older Android versions
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            activityManager.runningAppProcesses?.find {
                it.pid == android.os.Process.myPid()
            }?.processName ?: packageName
        }
    }
}
