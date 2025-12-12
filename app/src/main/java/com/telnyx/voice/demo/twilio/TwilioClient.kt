package com.telnyx.voice.demo.twilio

import android.content.Context
import android.util.Log
import com.twilio.voice.Call
import com.twilio.voice.CallException
import com.twilio.voice.ConnectOptions
import com.twilio.voice.RegistrationException
import com.twilio.voice.RegistrationListener
import com.twilio.voice.Voice

class TwilioClient(private val context: Context) {

    private var activeCall: Call? = null
    private var accessToken: String? = null

    fun register(token: String, fcmToken: String) {
        accessToken = token
        Voice.register(accessToken!!, Voice.RegistrationChannel.FCM, fcmToken, object : RegistrationListener {
            override fun onRegistered(accessToken: String, fcmToken: String) {
                Log.d(TAG, "Twilio Registered")
            }

            override fun onError(exception: RegistrationException, accessToken: String, fcmToken: String) {
                Log.e(TAG, "Twilio Registration Error: ${exception.message}")
            }
        })
    }

    fun makeCall(to: String) {
        accessToken?.let { token ->
            val connectOptions = ConnectOptions.Builder(token)
                .params(mapOf("to" to to))
                .build()
            activeCall = Voice.connect(context, connectOptions, callListener)
        }
    }

    private val callListener = object : Call.Listener {
        override fun onConnected(call: Call) {
            Log.d(TAG, "Twilio Call Connected")
            activeCall = call
        }

        override fun onDisconnected(call: Call, exception: CallException?) {
            Log.d(TAG, "Twilio Call Disconnected")
            activeCall = null
        }

        override fun onConnectFailure(call: Call, exception: CallException) {
            Log.e(TAG, "Twilio Call Connect Failure: ${exception.message}")
            activeCall = null
        }

        override fun onRinging(call: Call) {
            Log.d(TAG, "Twilio Call Ringing")
        }

        override fun onReconnecting(call: Call, exception: CallException) {
            Log.d(TAG, "Twilio Call Reconnecting: ${exception.message}")
        }

        override fun onReconnected(call: Call) {
            Log.d(TAG, "Twilio Call Reconnected")
        }
    }

    companion object {
        private const val TAG = "TwilioClient"
    }
}
