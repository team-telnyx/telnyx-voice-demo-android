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
import com.telnyx.webrtc.common.TelnyxViewModel
import com.telnyx.webrtc.common.TelnyxSocketEvent
import com.telnyx.webrtc.common.model.Profile
import androidx.lifecycle.ViewModelProvider
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
import java.util.UUID

class CallViewModel(application: Application) : AndroidViewModel(application) {

    // Use TelnyxViewModel from telnyx_common instead of custom TelnyxService
    private val telnyxViewModel: TelnyxViewModel by lazy {
        ViewModelProvider.AndroidViewModelFactory.getInstance(application)
            .create(TelnyxViewModel::class.java)
    }
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
    // Store incoming call details for answering (fixes "Cannot answer call: no current call" error)
    private var pendingIncomingCall: CallInfo? = null
    // Store outbound call destination to show during ringing
    private var outboundDestination: String? = null

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
        val profile = Profile(
            sipToken = token,
            fcmToken = fcmToken
        )
        telnyxViewModel.tokenLogin(
            viewContext = getApplication(),
            profile = profile,
            txPushMetaData = null,
            autoLogin = true
        )
    }

    fun connectTelnyxWithCredentials(username: String, password: String, fcmToken: String) {
        val profile = Profile(
            sipUsername = username,
            sipPass = password,
            fcmToken = fcmToken
        )
        telnyxViewModel.credentialLogin(
            viewContext = getApplication(),
            profile = profile,
            txPushMetaData = null,
            autoLogin = true
        )
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
        telnyxViewModel.disconnect(getApplication())
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
        // Store destination for display during ringing
        outboundDestination = destination

        when (provider) {
            Provider.TELNYX -> {
                val callerName = callerInfo["name"] ?: "TelnyxUser"
                val callerNumber = callerInfo["number"] ?: "1234567890"
                telnyxViewModel.sendInvite(
                    viewContext = getApplication(),
                    destinationNumber = destination,
                    debug = false
                )
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
            Provider.TELNYX -> {
                // Debug: check state before answering
                Timber.d("🔍 answerCall() called - pendingIncomingCall: ${pendingIncomingCall?.callId ?: "null"}, telnyxViewModel.currentCall: ${telnyxViewModel.currentCall?.callId ?: "null"}")

                // Use pending incoming call data instead of relying on currentCall
                val incomingCall = pendingIncomingCall
                if (incomingCall != null) {
                    Timber.d("📞 Answering Telnyx call: callId=${incomingCall.callId}, caller=${incomingCall.remoteNumber}")
                    telnyxViewModel.answerCall(
                        viewContext = getApplication(),
                        callId = UUID.fromString(incomingCall.callId),
                        callerIdNumber = incomingCall.remoteNumber,
                        debug = false
                    )
                } else {
                    Timber.e("❌ Cannot answer call: no pending incoming call data")
                }
            }
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
            Provider.TELNYX -> telnyxViewModel.endCall(getApplication())
            Provider.TWILIO -> twilioService.endCall()
            null -> Timber.e("Cannot hangup: no active provider")
        }
        stopCallDurationTimer()
        currentProvider = null
        // Clear pending incoming call data and outbound destination
        pendingIncomingCall = null
        outboundDestination = null
    }

    // State observation
    private fun observeTelnyxState() {
        viewModelScope.launch {
            telnyxViewModel.uiState.collect { telnyxEvent ->
                Timber.d("Telnyx event: $telnyxEvent")
                mergeStates(telnyxEvent, twilioService.state.value)
            }
        }
    }

    private fun observeTwilioState() {
        viewModelScope.launch {
            twilioService.state.collect { twilioState ->
                Timber.d("Twilio state changed: $twilioState")
                mergeStates(telnyxViewModel.uiState.value, twilioState)
            }
        }
    }

    private fun mergeStates(telnyxEvent: TelnyxSocketEvent, twilioState: TwilioCallState) {
        // Priority: Active call > Incoming call > Ringing > Ready > Connecting > Idle
        // Only one call can be active at a time

        // Check for active calls first
        if (telnyxEvent is TelnyxSocketEvent.OnCallAnswered) {
            val currentCall = telnyxViewModel.currentCall
            if (currentCall != null) {
                val isIncomingCall = _callState.value is CallState.IncomingCall

                // For outgoing calls, use stored destination. For incoming calls, use inviteResponse (caller info)
                val remoteNumber = if (isIncomingCall) {
                    currentCall.inviteResponse?.callerIdNumber ?: "Unknown"
                } else {
                    outboundDestination ?: "Unknown"
                }
                val remoteName = if (isIncomingCall) {
                    currentCall.inviteResponse?.callerIdName ?: "Unknown"
                } else {
                    outboundDestination ?: "Unknown"
                }

                val appCallInfo = CallInfo(
                    callId = currentCall.callId.toString(),
                    provider = Provider.TELNYX,
                    remoteNumber = remoteNumber,
                    remoteName = remoteName,
                    isIncoming = isIncomingCall,
                    startTime = System.currentTimeMillis()
                )
                currentProvider = Provider.TELNYX
                _callState.value = CallState.Active(appCallInfo, 0)
                _callUIState.value = CallUIState.Active(appCallInfo)
                _socketState.value = SocketConnectionState.READY
                Timber.d("✅ CallViewModel: Telnyx call is ACTIVE (${if (isIncomingCall) "incoming" else "outgoing"}), callId=${appCallInfo.callId}, remote=${appCallInfo.remoteNumber}")
                startCallDurationTimer(appCallInfo)
                // Clear pending incoming call data and outbound destination once call is active
                pendingIncomingCall = null
                outboundDestination = null
                return
            }
        }

        if (telnyxEvent is TelnyxSocketEvent.OnMedia) {
            val currentCall = telnyxViewModel.currentCall
            if (currentCall != null) {
                // Determine call direction from previous state
                val isIncomingCall = _callState.value is CallState.IncomingCall
                val isRinging = _callState.value is CallState.Ringing

                // For outgoing calls, use stored destination. For incoming calls, use inviteResponse (caller info)
                val remoteNumber = if (isIncomingCall) {
                    currentCall.inviteResponse?.callerIdNumber ?: "Unknown"
                } else if (isRinging) {
                    // Was ringing (outbound), use destination
                    outboundDestination ?: "Unknown"
                } else {
                    currentCall.inviteResponse?.callerIdNumber ?: "Unknown"
                }
                val remoteName = if (isIncomingCall) {
                    currentCall.inviteResponse?.callerIdName ?: "Unknown"
                } else if (isRinging) {
                    outboundDestination ?: "Unknown"
                } else {
                    currentCall.inviteResponse?.callerIdName ?: "Unknown"
                }

                val appCallInfo = CallInfo(
                    callId = currentCall.callId.toString(),
                    provider = Provider.TELNYX,
                    remoteNumber = remoteNumber,
                    remoteName = remoteName,
                    isIncoming = isIncomingCall,
                    startTime = System.currentTimeMillis()
                )
                currentProvider = Provider.TELNYX
                _callState.value = CallState.Active(appCallInfo, 0)
                _callUIState.value = CallUIState.Active(appCallInfo)
                _socketState.value = SocketConnectionState.READY
                Timber.d("✅ CallViewModel: Telnyx call media connected (ACTIVE)")
                startCallDurationTimer(appCallInfo)
                // Clear pending incoming call data and outbound destination once call is active
                pendingIncomingCall = null
                outboundDestination = null
                return
            }
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
        if (telnyxEvent is TelnyxSocketEvent.OnIncomingCall) {
            val message = telnyxEvent.message
            Timber.d("📥 OnIncomingCall received: callId=${message.callId}, caller=${message.callerIdNumber}")
            val appCallInfo = CallInfo(
                callId = message.callId.toString(),
                provider = Provider.TELNYX,
                remoteNumber = message.callerIdNumber ?: "Unknown",
                remoteName = message.callerIdName ?: "Unknown",
                isIncoming = true,
                startTime = System.currentTimeMillis()
            )
            currentProvider = Provider.TELNYX
            incomingCallId = appCallInfo.callId
            pendingIncomingCall = appCallInfo  // Store for answering call
            _callState.value = CallState.IncomingCall(appCallInfo)
            _callUIState.value = CallUIState.Incoming(appCallInfo)
            _socketState.value = SocketConnectionState.READY
            // Show notification for foreground incoming call
            showIncomingCallNotification(appCallInfo, Provider.TELNYX)
            Timber.d("🔔 Incoming call notification shown for callId=${appCallInfo.callId}")
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
        if (telnyxEvent is TelnyxSocketEvent.OnRinging) {
            val currentCall = telnyxViewModel.currentCall
            if (currentCall != null) {
                // Use the stored outbound destination instead of inviteResponse (which contains caller info, not destination)
                val destination = outboundDestination ?: "Unknown"
                val appCallInfo = CallInfo(
                    callId = currentCall.callId.toString(),
                    provider = Provider.TELNYX,
                    remoteNumber = destination,
                    remoteName = destination,
                    isIncoming = false,
                    startTime = System.currentTimeMillis()
                )
                currentProvider = Provider.TELNYX
                _callState.value = CallState.Ringing(appCallInfo)
                _callUIState.value = CallUIState.Ringing(appCallInfo)
                _socketState.value = SocketConnectionState.READY
                return
            }
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
        if (twilioState is TwilioCallState.Error) {
            _callState.value = CallState.Error(twilioState.message, Provider.TWILIO)
            _callUIState.value = CallUIState.Idle
            return
        }

        // Check for ready state
        val readyProviders = mutableSetOf<Provider>()
        if (telnyxEvent is TelnyxSocketEvent.OnClientReady) {
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
        if (twilioState is TwilioCallState.Registering) {
            _callState.value = CallState.Registering
            _callUIState.value = CallUIState.Idle
            _socketState.value = SocketConnectionState.CONNECTING
            return
        }

        // Default to idle
        _callState.value = CallState.Idle
        _callUIState.value = CallUIState.Idle
        _socketState.value = SocketConnectionState.DISCONNECTED
        // Clear pending incoming call data and outbound destination when returning to idle
        pendingIncomingCall = null
        outboundDestination = null
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
