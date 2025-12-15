package com.telnyx.voice.demo

import android.content.Context
import com.telnyx.voice.demo.telnyx.TelnyxClient
import com.telnyx.voice.demo.telnyx.TelnyxCommon
import com.telnyx.voice.demo.telnyx.TelnyxSocketEvent
import com.telnyx.voice.demo.twilio.TwilioClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object CallManager {

    private var _twilioClient: TwilioClient? = null
    private var _telnyxClient: TelnyxClient? = null

    private val _status = MutableStateFlow("Idle")
    val status: StateFlow<String> = _status.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun init(context: Context) {
        if (_twilioClient == null) {
            _twilioClient = TwilioClient(context)
        }
        if (_telnyxClient == null) {
            _telnyxClient = TelnyxClient(context)
        }

        scope.launch {
            TelnyxCommon.getInstance().socketEvent.collect { event ->
                when (event) {
                    is TelnyxSocketEvent.OnClientReady -> _status.value = "Telnyx Client Ready"
                    is TelnyxSocketEvent.OnCallAnswered -> _status.value = "Telnyx Call Active"
                    is TelnyxSocketEvent.OnCallEnded -> _status.value = "Telnyx Call Ended"
                    else -> {}
                }
            }
        }
    }

    fun registerTwilio(accessToken: String, fcmToken: String) {
        _twilioClient?.register(accessToken, fcmToken)
        _status.value = "Registering Twilio..."
    }

    fun connectTelnyx(token: String, fcmToken: String) {
        _telnyxClient?.connect(token, fcmToken)
        _status.value = "Connecting Telnyx..."
    }

    fun connectTelnyxWithCredentials(username: String, password: String, fcmToken: String) {
        _telnyxClient?.connectWithCredentials(username, password, fcmToken)
        _status.value = "Connecting Telnyx (Creds)..."
    }

    fun inviteTelnyx(caller: String, dest: String) {
        _telnyxClient?.invite(caller, "1234567890", dest, "customState")
        _status.value = "Calling $dest..."
    }

    fun getTwilioClient(): TwilioClient? = _twilioClient
    fun getTelnyxClient(): TelnyxClient? = _telnyxClient
}
