package com.twilio.voice.logic.service

import android.content.Context
import com.twilio.voice.logic.models.CallInfo
import com.twilio.voice.logic.models.Provider
import com.twilio.voice.logic.models.TwilioCallState
import com.twilio.voice.AcceptOptions
import com.twilio.voice.Call
import com.twilio.voice.CallException
import com.twilio.voice.CallInvite
import com.twilio.voice.CancelledCallInvite
import com.twilio.voice.ConnectOptions
import com.twilio.voice.RegistrationException
import com.twilio.voice.RegistrationListener
import com.twilio.voice.Voice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

class TwilioService(private val context: Context) {

    private val _state = MutableStateFlow<TwilioCallState>(TwilioCallState.Unregistered)
    val state: StateFlow<TwilioCallState> = _state.asStateFlow()

    private var accessToken: String? = null
    private var activeCall: Call? = null
    private var pendingCallInvite: CallInvite? = null

    fun register(token: String, fcmToken: String) {
        accessToken = token
        _state.value = TwilioCallState.Registering

        Voice.register(
            token,
            Voice.RegistrationChannel.FCM,
            fcmToken,
            object : RegistrationListener {
                override fun onRegistered(accessToken: String, fcmToken: String) {
                    Timber.d("Twilio registered successfully")
                    _state.value = TwilioCallState.Registered
                }

                override fun onError(
                    exception: RegistrationException,
                    accessToken: String,
                    fcmToken: String
                ) {
                    Timber.e("Twilio registration failed: ${exception.message}")
                    _state.value = TwilioCallState.Error("Registration failed: ${exception.message}")
                }
            }
        )
    }

    fun makeCall(to: String) {
        val token = accessToken ?: run {
            _state.value = TwilioCallState.Error("Not registered")
            return
        }

        val connectOptions = ConnectOptions.Builder(token)
            .params(mapOf("to" to to))
            .build()

        activeCall = Voice.connect(context, connectOptions, callListener)

        val callInfo = CallInfo(
            callId = activeCall?.sid ?: "unknown",
            provider = Provider.TWILIO,
            remoteNumber = to,
            remoteName = null,
            isIncoming = false
        )

        _state.value = TwilioCallState.Ringing(callInfo)
    }

    fun handleCallInvite(callInvite: CallInvite) {
        pendingCallInvite = callInvite

        val callInfo = CallInfo(
            callId = callInvite.callSid ?: "unknown",
            provider = Provider.TWILIO,
            remoteNumber = callInvite.from ?: "Unknown",
            remoteName = callInvite.from,
            isIncoming = true
        )

        _state.value = TwilioCallState.IncomingCall(callInfo, callInvite)
        Timber.d("Twilio call invite handled: ${callInvite.callSid}")
    }

    fun handleCancelledCallInvite(cancelledCallInvite: CancelledCallInvite) {
        if (pendingCallInvite?.callSid == cancelledCallInvite.callSid) {
            pendingCallInvite = null
            _state.value = TwilioCallState.Registered
            Timber.d("Twilio call invite cancelled: ${cancelledCallInvite.callSid}")
        }
    }

    fun acceptIncomingCall() {
        val callInvite = pendingCallInvite ?: run {
            Timber.e("No pending call invite to accept")
            return
        }

        val acceptOptions = AcceptOptions.Builder()
            .build()

        activeCall = callInvite.accept(context, acceptOptions, callListener)
        pendingCallInvite = null

        val callInfo = CallInfo(
            callId = callInvite.callSid ?: "unknown",
            provider = Provider.TWILIO,
            remoteNumber = callInvite.from ?: "Unknown",
            remoteName = callInvite.from,
            isIncoming = true,
            startTime = System.currentTimeMillis()
        )

        _state.value = TwilioCallState.Active(callInfo)
        Timber.d("Twilio call accepted: ${callInvite.callSid}")
    }

    fun rejectIncomingCall() {
        val callInvite = pendingCallInvite ?: run {
            Timber.e("No pending call invite to reject")
            return
        }

        callInvite.reject(context)
        pendingCallInvite = null
        _state.value = TwilioCallState.Registered
        Timber.d("Twilio call rejected: ${callInvite.callSid}")
    }

    fun endCall() {
        activeCall?.disconnect()
        activeCall = null
        _state.value = TwilioCallState.Registered
        Timber.d("Twilio call ended")
    }

    fun unregister() {
        accessToken = null
        _state.value = TwilioCallState.Unregistered
        // Note: Twilio Voice SDK may not require explicit unregister in newer versions
        // The registration will expire based on the TTL in the access token
    }

    private val callListener = object : Call.Listener {
        override fun onConnected(call: Call) {
            Timber.d("Twilio call connected: ${call.sid}")
            activeCall = call

            // Update state to Active with start time
            val currentState = _state.value
            if (currentState is TwilioCallState.Ringing) {
                val updatedCallInfo = currentState.callInfo.copy(
                    startTime = System.currentTimeMillis()
                )
                _state.value = TwilioCallState.Active(updatedCallInfo)
            }
        }

        override fun onDisconnected(call: Call, exception: CallException?) {
            Timber.d("Twilio call disconnected: ${call.sid}")
            if (exception != null) {
                Timber.e("Disconnect reason: ${exception.message}")
            }
            activeCall = null
            _state.value = TwilioCallState.Registered
        }

        override fun onConnectFailure(call: Call, exception: CallException) {
            Timber.e("Twilio call connect failure: ${exception.message}")
            activeCall = null
            _state.value = TwilioCallState.Error("Call failed: ${exception.message}")
        }

        override fun onRinging(call: Call) {
            Timber.d("Twilio call ringing: ${call.sid}")
            // State already set in makeCall()
        }

        override fun onReconnecting(call: Call, exception: CallException) {
            Timber.d("Twilio call reconnecting: ${exception.message}")
        }

        override fun onReconnected(call: Call) {
            Timber.d("Twilio call reconnected: ${call.sid}")
        }
    }
}
