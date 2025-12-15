package com.telnyx.voice.logic.service

import android.content.Context
import com.telnyx.voice.logic.models.CallInfo
import com.telnyx.voice.logic.models.Provider
import com.telnyx.voice.logic.models.TelnyxCallState
import com.telnyx.webrtc.sdk.Call
import com.telnyx.webrtc.sdk.CredentialConfig
import com.telnyx.webrtc.sdk.TelnyxClient
import com.telnyx.webrtc.sdk.TokenConfig
import com.telnyx.webrtc.sdk.model.PushMetaData
import com.telnyx.webrtc.sdk.model.SocketMethod
import com.telnyx.webrtc.sdk.model.SocketStatus
import com.telnyx.webrtc.sdk.verto.receive.ReceivedMessageBody
import com.telnyx.webrtc.sdk.verto.receive.SocketResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

class TelnyxService(private val context: Context) {

    private val _state = MutableStateFlow<TelnyxCallState>(TelnyxCallState.Disconnected)
    val state: StateFlow<TelnyxCallState> = _state.asStateFlow()

    private var telnyxClient: TelnyxClient? = null
    private var currentCall: Call? = null

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        initializeClient()
    }

    private fun initializeClient() {
        if (telnyxClient == null) {
            telnyxClient = TelnyxClient(context)
            observeSocketResponses()
        }
    }

    fun connectWithToken(token: String, fcmToken: String) {
        _state.value = TelnyxCallState.Connecting
        val config = TokenConfig(
            sipToken = token,
            sipCallerIDName = "TelnyxUser",
            sipCallerIDNumber = "1234567890",
            fcmToken = fcmToken,
            ringtone = null,
            ringBackTone = null
        )
        telnyxClient?.tokenLogin(config)
    }

    fun connectWithCredentials(username: String, password: String, fcmToken: String) {
        _state.value = TelnyxCallState.Connecting
        val config = CredentialConfig(
            sipUser = username,
            sipPassword = password,
            sipCallerIDName = "TelnyxUser",
            sipCallerIDNumber = "1234567890",
            fcmToken = fcmToken,
            ringtone = null,
            ringBackTone = null
        )
        telnyxClient?.credentialLogin(config)
    }

    fun makeCall(callerName: String, callerNumber: String, destinationNumber: String) {
        val call = telnyxClient?.newInvite(
            callerName,
            callerNumber,
            destinationNumber,
            "demo-state"
        )

        if (call != null) {
            currentCall = call
            val callInfo = CallInfo(
                callId = call.callId.toString(),
                provider = Provider.TELNYX,
                remoteNumber = destinationNumber,
                remoteName = null,
                isIncoming = false
            )
            _state.value = TelnyxCallState.Ringing(callInfo)
        } else {
            _state.value = TelnyxCallState.Error("Failed to create call")
        }
    }

    fun answerCall() {
        // Note: The actual answer method may vary depending on Telnyx SDK version
        // For now, log that answer was requested - the SDK may handle this automatically
        // when the call invite is received
        Timber.d("Answering call: ${currentCall?.callId}")
        // TODO: Verify correct answer method from Telnyx SDK
    }

    fun endCall() {
        currentCall?.let { call ->
            call.endCall(call.callId)
        }
        currentCall = null
        CallForegroundService.stopService(context)
        Timber.d("Call ended")
    }

    fun handlePushData(metadata: PushMetaData) {
        Timber.d("Telnyx push received: callId=${metadata.callId}, caller=${metadata.callerName}")

        // Create call info from push metadata
        val callInfo = CallInfo(
            callId = metadata.callId?.toString() ?: "unknown",
            provider = Provider.TELNYX,
            remoteNumber = metadata.callerNumber ?: "Unknown",
            remoteName = metadata.callerName,
            isIncoming = true
        )

        _state.value = TelnyxCallState.IncomingCall(callInfo)
    }

    fun disconnect() {
        telnyxClient?.disconnect()
        currentCall = null
        _state.value = TelnyxCallState.Disconnected
        CallForegroundService.stopService(context)
    }

    private fun observeSocketResponses() {
        scope.launch {
            telnyxClient?.socketResponseFlow?.collect { response ->
                handleSocketResponse(response)
            }
        }
    }

    private fun handleSocketResponse(response: SocketResponse<ReceivedMessageBody>) {
        when (response.status) {
            SocketStatus.ESTABLISHED -> {
                Timber.d("Socket connection established")
            }
            SocketStatus.MESSAGERECEIVED -> {
                handleMessageReceived(response)
            }
            SocketStatus.DISCONNECT -> {
                Timber.d("Socket disconnected")
                _state.value = TelnyxCallState.Disconnected
                CallForegroundService.stopService(context)
            }
            else -> Timber.d("Socket status: ${response.status}")
        }
    }

    private fun handleMessageReceived(response: SocketResponse<ReceivedMessageBody>) {
        val data = response.data ?: return

        when (data.method) {
            SocketMethod.CLIENT_READY.methodName -> {
                Timber.d("Client ready")
                _state.value = TelnyxCallState.Ready
            }
            SocketMethod.LOGIN.methodName -> {
                Timber.d("Login successful")
                _state.value = TelnyxCallState.Ready
            }
            SocketMethod.INVITE.methodName -> {
                Timber.d("Incoming invite received")
                // The SDK should provide the Call object
                // For now we handle this via push notifications
                // When a real invite comes through (not from push), we'd get the Call here
            }
            SocketMethod.ANSWER.methodName -> {
                Timber.d("Call answered")
                currentCall?.let { call ->
                    // Extract call info from current state if available
                    val existingCallInfo = when (val currentState = _state.value) {
                        is TelnyxCallState.Ringing -> currentState.callInfo
                        is TelnyxCallState.IncomingCall -> currentState.callInfo
                        else -> null
                    }

                    val callInfo = existingCallInfo?.copy(startTime = System.currentTimeMillis()) ?: CallInfo(
                        callId = call.callId.toString(),
                        provider = Provider.TELNYX,
                        remoteNumber = "Unknown",
                        remoteName = null,
                        isIncoming = false,
                        startTime = System.currentTimeMillis()
                    )
                    _state.value = TelnyxCallState.Active(callInfo)
                    CallForegroundService.startService(context, call.callId.toString())
                }
            }
            SocketMethod.BYE.methodName -> {
                Timber.d("Call ended (BYE)")
                _state.value = TelnyxCallState.Ready
                currentCall = null
                CallForegroundService.stopService(context)
            }
            else -> Timber.d("Unhandled socket method: ${data.method}")
        }
    }
}
