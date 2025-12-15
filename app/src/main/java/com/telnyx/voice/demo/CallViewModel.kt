package com.telnyx.voice.demo

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.telnyx.voice.demo.models.CallInfo
import com.telnyx.voice.demo.models.CallState
import com.telnyx.voice.demo.models.Provider
import com.telnyx.voice.demo.models.CallUIState
import com.telnyx.voice.demo.models.SocketConnectionState
import com.telnyx.voice.demo.notification.CallNotificationService
import com.telnyx.voice.logic.models.TelnyxCallState
import com.telnyx.voice.logic.service.TelnyxService
import com.telnyx.voice.demo.network.RetrofitClient
import com.telnyx.voice.demo.network.TokenRequest
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

    private val _tokenFetchState = MutableStateFlow<TokenFetchState>(TokenFetchState.Idle)
    val tokenFetchState: StateFlow<TokenFetchState> = _tokenFetchState.asStateFlow()

    // New iOS-aligned states
    private val _callUIState = MutableStateFlow<CallUIState>(CallUIState.Idle)
    val callUIState: StateFlow<CallUIState> = _callUIState.asStateFlow()

    private val _socketState = MutableStateFlow(SocketConnectionState.DISCONNECTED)
    val socketState: StateFlow<SocketConnectionState> = _socketState.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isSpeakerOn = MutableStateFlow(false)
    val isSpeakerOn: StateFlow<Boolean> = _isSpeakerOn.asStateFlow()

    private val _isOnHold = MutableStateFlow(false)
    val isOnHold: StateFlow<Boolean> = _isOnHold.asStateFlow()

    private var callDurationJob: Job? = null
    private var currentProvider: Provider? = null
    private var incomingCallId: String? = null

    // Centralized notification service
    private val callNotificationService = CallNotificationService(
        context = application,
        receiverClass = CallActionReceiver::class.java
    )

    sealed class TokenFetchState {
        object Idle : TokenFetchState()
        object Loading : TokenFetchState()
        data class Success(val token: String, val identity: String) : TokenFetchState()
        data class Error(val message: String) : TokenFetchState()
    }

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

    fun fetchTwilioToken(identity: String, fcmToken: String) {
        viewModelScope.launch {
            _tokenFetchState.value = TokenFetchState.Loading
            try {
                val response = RetrofitClient.twilioApi.getAccessToken(
                    TokenRequest(identity = identity, deviceToken = fcmToken)
                )

                if (response.isSuccessful) {
                    val tokenResponse = response.body()
                    if (tokenResponse != null) {
                        _tokenFetchState.value = TokenFetchState.Success(
                            token = tokenResponse.token,
                            identity = tokenResponse.identity
                        )
                        Timber.d("Token fetched successfully for identity: ${tokenResponse.identity}")
                    } else {
                        _tokenFetchState.value = TokenFetchState.Error("Empty response from server")
                        Timber.e("Token fetch failed: empty response body")
                    }
                } else {
                    _tokenFetchState.value = TokenFetchState.Error(
                        "Failed to fetch token: ${response.code()} ${response.message()}"
                    )
                    Timber.e("Token fetch failed: ${response.code()} ${response.message()}")
                }
            } catch (e: Exception) {
                _tokenFetchState.value = TokenFetchState.Error("Network error: ${e.message}")
                Timber.e(e, "Token fetch error")
            }
        }
    }

    fun clearTokenFetchState() {
        _tokenFetchState.value = TokenFetchState.Idle
    }

    // New iOS-aligned methods
    fun connectAll() {
        // Both services connect automatically for incoming push notifications
        // Active provider is determined by user selection
        Timber.d("Connecting all services")
        _socketState.value = SocketConnectionState.CONNECTING
    }

    fun disconnectAll() {
        telnyxService.disconnect()
        twilioService.unregister()
        _socketState.value = SocketConnectionState.DISCONNECTED
        _callUIState.value = CallUIState.Idle
    }

    fun toggleMute() {
        _isMuted.value = !_isMuted.value
        // TODO: Implement actual mute toggle in services
        Timber.d("Mute toggled: ${_isMuted.value}")
    }

    fun toggleSpeaker() {
        _isSpeakerOn.value = !_isSpeakerOn.value
        // TODO: Implement actual speaker toggle in services
        Timber.d("Speaker toggled: ${_isSpeakerOn.value}")
    }

    fun toggleHold() {
        _isOnHold.value = !_isOnHold.value
        if (_isOnHold.value) {
            val currentState = _callUIState.value
            if (currentState is CallUIState.Active) {
                _callUIState.value = CallUIState.Held(currentState.callInfo)
            }
        } else {
            val currentState = _callUIState.value
            if (currentState is CallUIState.Held) {
                _callUIState.value = CallUIState.Active(currentState.callInfo)
            }
        }
        // TODO: Implement actual hold toggle in services
        Timber.d("Hold toggled: ${_isOnHold.value}")
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
        // Dismiss notification if there's an incoming call
        dismissIncomingCallNotification()
    }

    fun hangupCall() {
        // Dismiss notification if there's an incoming call (when rejecting)
        dismissIncomingCallNotification()

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
            _callUIState.value = CallUIState.Active(appCallInfo)
            _socketState.value = SocketConnectionState.READY
            Timber.d("✅ CallViewModel: Telnyx call is ACTIVE, callId=${appCallInfo.callId}, remote=${appCallInfo.remoteNumber}")
            startCallDurationTimer(appCallInfo)
            return
        }

        if (twilioState is TwilioCallState.Active) {
            val appCallInfo = mapTwilioCallInfo(twilioState.callInfo)
            currentProvider = Provider.TWILIO
            _callState.value = CallState.Active(appCallInfo, 0)
            _callUIState.value = CallUIState.Active(appCallInfo)
            _socketState.value = SocketConnectionState.READY
            startCallDurationTimer(appCallInfo)
            return
        }

        // Check for incoming calls
        if (telnyxState is TelnyxCallState.IncomingCall) {
            val appCallInfo = mapTelnyxCallInfo(telnyxState.callInfo)
            currentProvider = Provider.TELNYX
            incomingCallId = appCallInfo.callId
            _callState.value = CallState.IncomingCall(appCallInfo)
            _callUIState.value = CallUIState.Incoming(appCallInfo)
            _socketState.value = SocketConnectionState.READY
            // Show notification for foreground incoming call
            showIncomingCallNotification(appCallInfo, Provider.TELNYX)
            return
        }

        if (twilioState is TwilioCallState.IncomingCall) {
            val appCallInfo = mapTwilioCallInfo(twilioState.callInfo)
            currentProvider = Provider.TWILIO
            incomingCallId = appCallInfo.callId
            _callState.value = CallState.IncomingCall(appCallInfo)
            _callUIState.value = CallUIState.Incoming(appCallInfo)
            _socketState.value = SocketConnectionState.READY
            // Show notification for foreground incoming call
            showIncomingCallNotification(appCallInfo, Provider.TWILIO)
            return
        }

        // Check for ringing (outbound)
        if (telnyxState is TelnyxCallState.Ringing) {
            val appCallInfo = mapTelnyxCallInfo(telnyxState.callInfo)
            currentProvider = Provider.TELNYX
            _callState.value = CallState.Ringing(appCallInfo)
            _callUIState.value = CallUIState.Ringing(appCallInfo)
            _socketState.value = SocketConnectionState.READY
            return
        }

        if (twilioState is TwilioCallState.Ringing) {
            val appCallInfo = mapTwilioCallInfo(twilioState.callInfo)
            currentProvider = Provider.TWILIO
            _callState.value = CallState.Ringing(appCallInfo)
            _callUIState.value = CallUIState.Ringing(appCallInfo)
            _socketState.value = SocketConnectionState.READY
            return
        }

        // Check for errors
        if (telnyxState is TelnyxCallState.Error) {
            _callState.value = CallState.Error(telnyxState.message, Provider.TELNYX)
            _callUIState.value = CallUIState.Idle
            return
        }

        if (twilioState is TwilioCallState.Error) {
            _callState.value = CallState.Error(twilioState.message, Provider.TWILIO)
            _callUIState.value = CallUIState.Idle
            return
        }

        // Check for ready state
        val readyProviders = mutableSetOf<Provider>()
        if (telnyxState is TelnyxCallState.Ready) {
            readyProviders.add(Provider.TELNYX)
        }
        if (twilioState is TwilioCallState.Registered) {
            readyProviders.add(Provider.TWILIO)
            // Save registration timestamp for Twilio best practices
            com.telnyx.voice.demo.util.SettingsStorage.saveTwilioRegistration(
                getApplication(),
                twilioState.fcmToken
            )
            Timber.d("Twilio registration saved: token=${twilioState.fcmToken.take(20)}...")
        }

        if (readyProviders.isNotEmpty()) {
            _callState.value = CallState.Ready(readyProviders)
            _callUIState.value = CallUIState.Idle
            _socketState.value = SocketConnectionState.READY
            return
        }

        // Check for connecting/registering
        if (telnyxState is TelnyxCallState.Connecting ||
            twilioState is TwilioCallState.Registering) {
            _callState.value = CallState.Registering
            _callUIState.value = CallUIState.Idle
            _socketState.value = SocketConnectionState.CONNECTING
            return
        }

        // Default to idle
        _callState.value = CallState.Idle
        _callUIState.value = CallUIState.Idle
        _socketState.value = SocketConnectionState.DISCONNECTED
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

    private fun dismissIncomingCallNotification() {
        incomingCallId?.let { callId ->
            callNotificationService.cancelNotification()
            Timber.d("Notification dismissed for call: $callId")
            incomingCallId = null
        }
    }

    private fun showIncomingCallNotification(callInfo: CallInfo, provider: Provider) {
        // Delegate to CallNotificationService
        callNotificationService.showIncomingCallNotification(callInfo, provider)

        // Track for dismissal
        incomingCallId = callInfo.callId
    }

    override fun onCleared() {
        super.onCleared()
        stopCallDurationTimer()
    }
}
