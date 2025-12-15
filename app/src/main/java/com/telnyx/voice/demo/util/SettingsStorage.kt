package com.telnyx.voice.demo.util

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.telnyx.voice.demo.models.Provider
import com.telnyx.voice.demo.models.TelnyxSettings
import com.telnyx.voice.demo.models.TwilioSettings
import androidx.core.content.edit

object SettingsStorage {
    private const val PREFS_NAME = "voice_demo_settings"
    private const val KEY_TELNYX_SETTINGS = "telnyx_settings"
    private const val KEY_TWILIO_SETTINGS = "twilio_settings"
    private const val KEY_ACTIVE_PROVIDER = "active_provider"
    private const val KEY_LAST_DESTINATION = "last_destination"
    private const val KEY_PUSH_TOKEN = "push_token"
    private const val KEY_TWILIO_REGISTERED_TOKEN = "twilio_registered_token"
    private const val KEY_TWILIO_REGISTRATION_TIMESTAMP = "twilio_registration_timestamp"

    // 6 months in milliseconds
    private const val REGISTRATION_EXPIRY_MILLIS = 6L * 30L * 24L * 60L * 60L * 1000L

    private val gson = Gson()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // Telnyx Settings
    fun saveTelnyxSettings(context: Context, settings: TelnyxSettings) {
        val json = gson.toJson(settings)
        getPrefs(context).edit { putString(KEY_TELNYX_SETTINGS, json) }
    }

    fun getTelnyxSettings(context: Context): TelnyxSettings {
        val json = getPrefs(context).getString(KEY_TELNYX_SETTINGS, null)
        return if (json != null) {
            gson.fromJson(json, TelnyxSettings::class.java)
        } else {
            TelnyxSettings()
        }
    }

    // Twilio Settings
    fun saveTwilioSettings(context: Context, settings: TwilioSettings) {
        val json = gson.toJson(settings)
        getPrefs(context).edit { putString(KEY_TWILIO_SETTINGS, json) }
    }

    fun getTwilioSettings(context: Context): TwilioSettings {
        val json = getPrefs(context).getString(KEY_TWILIO_SETTINGS, null)
        return if (json != null) {
            gson.fromJson(json, TwilioSettings::class.java)
        } else {
            TwilioSettings()
        }
    }

    // Active Provider
    fun saveActiveProvider(context: Context, provider: Provider) {
        getPrefs(context).edit { putString(KEY_ACTIVE_PROVIDER, provider.name) }
    }

    fun getActiveProvider(context: Context): Provider {
        val providerName = getPrefs(context).getString(KEY_ACTIVE_PROVIDER, Provider.TELNYX.name)
        return try {
            Provider.valueOf(providerName!!)
        } catch (_: Exception) {
            Provider.TELNYX
        }
    }

    // Last Destination
    fun saveLastDestination(context: Context, destination: String) {
        getPrefs(context).edit { putString(KEY_LAST_DESTINATION, destination) }
    }

    fun getLastDestination(context: Context): String {
        return getPrefs(context).getString(KEY_LAST_DESTINATION, "") ?: ""
    }

    // Push Token
    fun savePushToken(context: Context, token: String) {
        getPrefs(context).edit { putString(KEY_PUSH_TOKEN, token) }
    }

    fun getPushToken(context: Context): String? {
        return getPrefs(context).getString(KEY_PUSH_TOKEN, null)
    }

    // Twilio Registration Tracking
    fun saveTwilioRegistration(context: Context, token: String) {
        getPrefs(context).edit {
            putString(KEY_TWILIO_REGISTERED_TOKEN, token)
                .putLong(KEY_TWILIO_REGISTRATION_TIMESTAMP, System.currentTimeMillis())
        }
    }

    fun getTwilioRegisteredToken(context: Context): String? {
        return getPrefs(context).getString(KEY_TWILIO_REGISTERED_TOKEN, null)
    }

    fun getTwilioRegistrationTimestamp(context: Context): Long {
        return getPrefs(context).getLong(KEY_TWILIO_REGISTRATION_TIMESTAMP, 0L)
    }

    /**
     * Checks if Twilio needs to be re-registered based on:
     * 1. Token has changed
     * 2. Registration is older than 6 months
     * 3. Never been registered
     */
    fun needsTwilioReregistration(context: Context, currentToken: String): Boolean {
        val registeredToken = getTwilioRegisteredToken(context)
        val registrationTimestamp = getTwilioRegistrationTimestamp(context)

        // Never registered
        if (registeredToken == null || registrationTimestamp == 0L) {
            return true
        }

        // Token has changed
        if (registeredToken != currentToken) {
            return true
        }

        // Registration is older than 6 months
        val age = System.currentTimeMillis() - registrationTimestamp
        return age > REGISTRATION_EXPIRY_MILLIS
    }
}
