package com.telnyx.voice.demo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.telnyx.voice.demo.models.CallInfo
import com.telnyx.voice.demo.models.CallState
import com.telnyx.voice.demo.models.Provider
import com.telnyx.voice.logic.models.TelnyxCallState
import com.telnyx.voice.logic.service.TelnyxService
import com.twilio.voice.logic.models.TwilioCallState
import com.twilio.voice.logic.service.TwilioService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

class CallViewModel(application: Application) : AndroidViewModel(application) {

    private val telnyxService: TelnyxService = (application as VoiceApplication).telnyxService
    private val twilioService: TwilioService = (application as VoiceApplication).twilioService

    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    private var callDurationJob: Job? = null
    private var currentProvider: Provider? = null

    init {
        observeTelnyxState()
        observeTwilioState()
    }

    // Registration methods
    fun connectTelnyx(token: String, fcmToken: String) {
        telnyxService.connectWithToken(token, fcmToken)
    }

    fun connectTelnyxWithCredentials(username: String, password: String, fcmToken: String) {
        telnyxService.connectWithCredentials(username, password, fcmToken)
    }

    fun registerTwilio(accessToken: String, fcmToken: String) {
        twilioService.register(accessToken, fcmToken)
    }

    // Call control methods
    fun makeCall(provider: Provider, destination: String, callerInfo: Map<String, String>) {
        when (provider) {
            Provider.TELNYX -> {
                val callerName = callerInfo["name"] ?: "TelnyxUser"
                val callerNumber = callerInfo["number"] ?: "1234567890"
                telnyxService.makeCall(callerName, callerNumber, destination)
                currentProvider = Provider.TELNYX
            }
            Provider.TWILIO -> {
                twilioService.makeCall(destination)
                currentProvider = Provider.TWILIO
            }
        }
    }

    fun answerCall() {
        when (currentProvider) {
            Provider.TELNYX -> telnyxService.answerCall()
            Provider.TWILIO -> twilioService.acceptIncomingCall()
            null -> Timber.e("Cannot answer call: no active provider")
        }
    }

    fun hangupCall() {
        when (currentProvider) {
            Provider.TELNYX -> telnyxService.endCall()
            Provider.TWILIO -> twilioService.endCall()
            null -> Timber.e("Cannot hangup: no active provider")
        }
        stopCallDurationTimer()
        currentProvider = null
    }

    // State observation
    private fun observeTelnyxState() {
        viewModelScope.launch {
            telnyxService.state.collect { telnyxState ->
                Timber.d("Telnyx state changed: $telnyxState")
                mergeStates(telnyxState, twilioService.state.value)
            }
        }
    }

    private fun observeTwilioState() {
        viewModelScope.launch {
            twilioService.state.collect { twilioState ->
                Timber.d("Twilio state changed: $twilioState")
                mergeStates(telnyxService.state.value, twilioState)
            }
        }
    }

    private fun mergeStates(telnyxState: TelnyxCallState, twilioState: TwilioCallState) {
        // Priority: Active call > Incoming call > Ringing > Ready > Connecting > Idle
        // Only one call can be active at a time

        // Check for active calls first
        if (telnyxState is TelnyxCallState.Active) {
            val appCallInfo = mapTelnyxCallInfo(telnyxState.callInfo)
            currentProvider = Provider.TELNYX
            _callState.value = CallState.Active(appCallInfo, 0)
            startCallDurationTimer(appCallInfo)
            return
        }

        if (twilioState is TwilioCallState.Active) {
            val appCallInfo = mapTwilioCallInfo(twilioState.callInfo)
            currentProvider = Provider.TWILIO
            _callState.value = CallState.Active(appCallInfo, 0)
            startCallDurationTimer(appCallInfo)
            return
        }

        // Check for incoming calls
        if (telnyxState is TelnyxCallState.IncomingCall) {
            val appCallInfo = mapTelnyxCallInfo(telnyxState.callInfo)
            currentProvider = Provider.TELNYX
            _callState.value = CallState.IncomingCall(appCallInfo)
            return
        }

        if (twilioState is TwilioCallState.IncomingCall) {
            val appCallInfo = mapTwilioCallInfo(twilioState.callInfo)
            currentProvider = Provider.TWILIO
            _callState.value = CallState.IncomingCall(appCallInfo)
            return
        }

        // Check for ringing (outbound)
        if (telnyxState is TelnyxCallState.Ringing) {
            val appCallInfo = mapTelnyxCallInfo(telnyxState.callInfo)
            currentProvider = Provider.TELNYX
            _callState.value = CallState.Ringing(appCallInfo)
            return
        }

        if (twilioState is TwilioCallState.Ringing) {
            val appCallInfo = mapTwilioCallInfo(twilioState.callInfo)
            currentProvider = Provider.TWILIO
            _callState.value = CallState.Ringing(appCallInfo)
            return
        }

        // Check for errors
        if (telnyxState is TelnyxCallState.Error) {
            _callState.value = CallState.Error(telnyxState.message, Provider.TELNYX)
            return
        }

        if (twilioState is TwilioCallState.Error) {
            _callState.value = CallState.Error(twilioState.message, Provider.TWILIO)
            return
        }

        // Check for ready state
        val readyProviders = mutableSetOf<Provider>()
        if (telnyxState is TelnyxCallState.Ready) {
            readyProviders.add(Provider.TELNYX)
        }
        if (twilioState is TwilioCallState.Registered) {
            readyProviders.add(Provider.TWILIO)
        }

        if (readyProviders.isNotEmpty()) {
            _callState.value = CallState.Ready(readyProviders)
            return
        }

        // Check for connecting/registering
        if (telnyxState is TelnyxCallState.Connecting ||
            twilioState is TwilioCallState.Registering) {
            _callState.value = CallState.Registering
            return
        }

        // Default to idle
        _callState.value = CallState.Idle
    }

    private fun mapTelnyxCallInfo(telnyxCallInfo: com.telnyx.voice.logic.models.CallInfo): CallInfo {
        return CallInfo(
            callId = telnyxCallInfo.callId,
            provider = Provider.TELNYX,
            remoteNumber = telnyxCallInfo.remoteNumber,
            remoteName = telnyxCallInfo.remoteName,
            isIncoming = telnyxCallInfo.isIncoming,
            startTime = telnyxCallInfo.startTime
        )
    }

    private fun mapTwilioCallInfo(twilioCallInfo: com.twilio.voice.logic.models.CallInfo): CallInfo {
        return CallInfo(
            callId = twilioCallInfo.callId,
            provider = Provider.TWILIO,
            remoteNumber = twilioCallInfo.remoteNumber,
            remoteName = twilioCallInfo.remoteName,
            isIncoming = twilioCallInfo.isIncoming,
            startTime = twilioCallInfo.startTime
        )
    }

    private fun startCallDurationTimer(callInfo: CallInfo) {
        callDurationJob?.cancel()
        callDurationJob = viewModelScope.launch {
            var duration = 0L
            while (isActive) {
                delay(1000)
                duration++
                _callState.value = CallState.Active(callInfo, duration)
            }
        }
    }

    private fun stopCallDurationTimer() {
        callDurationJob?.cancel()
        callDurationJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopCallDurationTimer()
    }
}
