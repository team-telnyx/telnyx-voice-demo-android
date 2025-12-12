package com.telnyx.voice.demo.telnyx

import android.content.Context
import android.util.Log
import com.telnyx.webrtc.sdk.TelnyxClient
import com.telnyx.webrtc.sdk.TokenConfig

class TelnyxClient(private val context: Context) {

    private val client: TelnyxClient by lazy { TelnyxClient(context) }

    fun connect(token: String, fcmToken: String) {
        val config = TokenConfig(
            sipToken = token,
            sipCallerIDName = "TelnyxUser",
            sipCallerIDNumber = "1234567890",
            fcmToken = fcmToken,
            ringtone = null,
            ringBackTone = null
        )
        client.tokenLogin(config)
    }

    fun handlePush(data: Map<String, String>) {
        Log.d("TelnyxClient", "Push received: $data")
        // TODO: Implement proper push handling using TxPushMetaData if available
    }

    fun disconnect() {
        client.disconnect()
    }

    companion object {
        private const val TAG = "TelnyxClient"
    }
}
