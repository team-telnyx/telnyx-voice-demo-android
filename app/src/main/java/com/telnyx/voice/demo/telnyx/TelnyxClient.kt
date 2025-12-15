package com.telnyx.voice.demo.telnyx

import android.content.Context
import android.util.Log
import com.telnyx.webrtc.sdk.CredentialConfig
import com.telnyx.webrtc.sdk.TokenConfig

class TelnyxClient(private val context: Context) {

    private val telnyxCommon = TelnyxCommon.getInstance()

    init {
        telnyxCommon.init(context)
    }

    fun connect(token: String, fcmToken: String) {
        val config =
                TokenConfig(
                        sipToken = token,
                        sipCallerIDName = "TelnyxUser",
                        sipCallerIDNumber = "1234567890",
                        fcmToken = fcmToken,
                        ringtone = null,
                        ringBackTone = null
                )
        telnyxCommon.telnyxClient?.tokenLogin(config)
    }

    fun connectWithCredentials(username: String, password: String, fcmToken: String) {
        val config =
                CredentialConfig(
                        sipUser = username,
                        sipPassword = password,
                        sipCallerIDName = "TelnyxUser",
                        sipCallerIDNumber = "1234567890",
                        fcmToken = fcmToken,
                        ringtone = null,
                        ringBackTone = null
                )
        telnyxCommon.telnyxClient?.credentialLogin(config)
    }

    fun invite(
            callerName: String,
            callerNumber: String,
            destinationNumber: String,
            clientState: String
    ) {
        telnyxCommon.telnyxClient?.newInvite(
                callerName,
                callerNumber,
                destinationNumber,
                clientState
        )
    }

    fun handlePush(data: Map<String, String>) {
        Log.d("TelnyxClient", "Push received: $data")
        // Push handling is now primarily done in HybridFirebaseMessagingService ->
        // CallNotificationService
        // But we might need to handle answering from push here if needed
    }

    fun disconnect() {
        telnyxCommon.telnyxClient?.disconnect()
    }

    companion object {
        private const val TAG = "TelnyxClient"
    }
}
