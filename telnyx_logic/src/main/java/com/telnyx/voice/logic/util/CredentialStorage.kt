package com.telnyx.voice.logic.util

import android.content.Context
import com.telnyx.webrtc.sdk.CredentialConfig
import com.telnyx.webrtc.sdk.TelnyxConfig
import com.telnyx.webrtc.sdk.TokenConfig

object CredentialStorage {
    private const val PREFS_NAME = "telnyx_credentials"
    private const val KEY_LOGIN_TYPE = "login_type"
    private const val KEY_SIP_TOKEN = "sip_token"
    private const val KEY_SIP_USERNAME = "sip_username"
    private const val KEY_SIP_PASSWORD = "sip_password"
    private const val KEY_CALLER_ID_NAME = "caller_id_name"
    private const val KEY_CALLER_ID_NUMBER = "caller_id_number"
    private const val KEY_FCM_TOKEN = "fcm_token"

    private enum class LoginType { TOKEN, CREDENTIAL, NONE }

    fun saveTokenLogin(context: Context, config: TokenConfig) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_LOGIN_TYPE, LoginType.TOKEN.name)
            .putString(KEY_SIP_TOKEN, config.sipToken)
            .putString(KEY_CALLER_ID_NAME, config.sipCallerIDName)
            .putString(KEY_CALLER_ID_NUMBER, config.sipCallerIDNumber)
            .putString(KEY_FCM_TOKEN, config.fcmToken)
            .apply()
    }

    fun saveCredentialLogin(context: Context, config: CredentialConfig) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_LOGIN_TYPE, LoginType.CREDENTIAL.name)
            .putString(KEY_SIP_USERNAME, config.sipUser)
            .putString(KEY_SIP_PASSWORD, config.sipPassword)
            .putString(KEY_CALLER_ID_NAME, config.sipCallerIDName)
            .putString(KEY_CALLER_ID_NUMBER, config.sipCallerIDNumber)
            .putString(KEY_FCM_TOKEN, config.fcmToken)
            .apply()
    }

    fun getStoredConfig(context: Context): TelnyxConfig? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val loginType = prefs.getString(KEY_LOGIN_TYPE, null) ?: return null

        return when (LoginType.valueOf(loginType)) {
            LoginType.TOKEN -> {
                val token = prefs.getString(KEY_SIP_TOKEN, null) ?: return null
                TokenConfig(
                    sipToken = token,
                    sipCallerIDName = prefs.getString(KEY_CALLER_ID_NAME, "Demo") ?: "Demo",
                    sipCallerIDNumber = prefs.getString(KEY_CALLER_ID_NUMBER, "1234") ?: "1234",
                    fcmToken = prefs.getString(KEY_FCM_TOKEN, "") ?: "",
                    ringtone = null,
                    ringBackTone = null
                )
            }
            LoginType.CREDENTIAL -> {
                val username = prefs.getString(KEY_SIP_USERNAME, null) ?: return null
                val password = prefs.getString(KEY_SIP_PASSWORD, null) ?: return null
                CredentialConfig(
                    sipUser = username,
                    sipPassword = password,
                    sipCallerIDName = prefs.getString(KEY_CALLER_ID_NAME, "Demo") ?: "Demo",
                    sipCallerIDNumber = prefs.getString(KEY_CALLER_ID_NUMBER, "1234") ?: "1234",
                    fcmToken = prefs.getString(KEY_FCM_TOKEN, "") ?: "",
                    ringtone = null,
                    ringBackTone = null
                )
            }
            LoginType.NONE -> null
        }
    }

    fun clearCredentials(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }
}
