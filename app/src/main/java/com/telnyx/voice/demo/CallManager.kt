package com.telnyx.voice.demo

import android.content.Context
import com.telnyx.voice.demo.telnyx.TelnyxClient
import com.telnyx.voice.demo.twilio.TwilioClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object CallManager {

    private var _twilioClient: TwilioClient? = null
    private var _telnyxClient: TelnyxClient? = null

    private val _status = MutableStateFlow("Idle")
    val status: StateFlow<String> = _status.asStateFlow()

    fun init(context: Context) {
        if (_twilioClient == null) {
            _twilioClient = TwilioClient(context)
        }
        if (_telnyxClient == null) {
            _telnyxClient = TelnyxClient(context)
        }
    }

    fun registerTwilio(token: String, fcmToken: String) {
        _twilioClient?.register(token, fcmToken)
        _status.value = "Registering Twilio..."
    }

    fun connectTelnyx(token: String, fcmToken: String) {
        _telnyxClient?.connect(token, fcmToken)
        _status.value = "Connecting Telnyx..."
    }

    fun getTwilioClient(): TwilioClient? = _twilioClient
    fun getTelnyxClient(): TelnyxClient? = _telnyxClient
}
