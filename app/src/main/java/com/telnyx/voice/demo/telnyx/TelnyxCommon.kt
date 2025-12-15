package com.telnyx.voice.demo.telnyx

import android.content.Context
import com.telnyx.voice.demo.telnyx.service.CallForegroundService
import com.telnyx.webrtc.sdk.Call
import com.telnyx.webrtc.sdk.TelnyxClient
import com.telnyx.webrtc.sdk.model.SocketMethod
import com.telnyx.webrtc.sdk.model.SocketStatus
import com.telnyx.webrtc.sdk.verto.receive.ReceivedMessageBody
import com.telnyx.webrtc.sdk.verto.receive.SocketResponse
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

class TelnyxCommon private constructor() {

    private var _telnyxClient: TelnyxClient? = null
    val telnyxClient: TelnyxClient?
        get() = _telnyxClient

    private var _currentCall: Call? = null
    val currentCall: Call?
        get() = _currentCall

    private val _heldCalls = MutableStateFlow<List<Call>>(emptyList())
    val heldCalls: StateFlow<List<Call>>
        get() = _heldCalls

    private val _socketEvent = MutableStateFlow<TelnyxSocketEvent>(TelnyxSocketEvent.InitState)
    val socketEvent: StateFlow<TelnyxSocketEvent> = _socketEvent.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun init(context: Context) {
        if (_telnyxClient == null) {
            _telnyxClient = TelnyxClient(context)
            observeSocketResponses()
        }
    }

    private fun observeSocketResponses() {
        scope.launch {
            _telnyxClient?.socketResponseFlow?.collect { response ->
                handleSocketResponse(response)
            }
        }
    }

    private fun handleSocketResponse(response: SocketResponse<ReceivedMessageBody>) {
        when (response.status) {
            SocketStatus.ESTABLISHED -> Timber.d("Socket connection established")
            SocketStatus.MESSAGERECEIVED -> handleMessageReceived(response)
            SocketStatus.DISCONNECT -> Timber.d("Socket disconnected")
            else -> Timber.d("Socket status: ${response.status}")
        }
    }

    private fun handleMessageReceived(response: SocketResponse<ReceivedMessageBody>) {
        val data = response.data
        if (data != null) {
            when (data.method) {
                SocketMethod.CLIENT_READY.methodName ->
                        _socketEvent.value = TelnyxSocketEvent.OnClientReady
                SocketMethod.LOGIN.methodName -> Timber.d("Login successful")
                SocketMethod.INVITE.methodName -> {
                    // Handle incoming invite
                    // The SDK might create a Call object automatically or we need to query it
                    // For now, we assume the SDK handles the Call object creation and we just get
                    // the event
                    // We need to find the call and set it as current if it's a new incoming call
                    // But typically we wait for the Call object to be available or use
                    // getActiveCalls()
                    Timber.d("Incoming Invite Received")
                }
                SocketMethod.ANSWER.methodName -> {
                    Timber.d("Call Answered")
                    _currentCall?.let {
                        _socketEvent.value = TelnyxSocketEvent.OnCallAnswered(it.callId)
                    }
                }
                SocketMethod.BYE.methodName -> {
                    Timber.d("Call Ended")
                    _socketEvent.value = TelnyxSocketEvent.OnCallEnded
                    setCurrentCall(null)
                }
            }
        }
    }

    fun setCurrentCall(call: Call?) {
        if (call != null) {
            _currentCall = call
            // Start observing call state
            // observeCallState(call)
            // Start service
            // CallForegroundService.startService(context, call.callId.toString()) // Context needed
        } else {
            _currentCall = null
            // Stop service
            // CallForegroundService.stopService(context) // Context needed
        }
    }

    // Helper to pass context for service
    fun updateCallService(context: Context, call: Call?) {
        if (call != null) {
            CallForegroundService.startService(context, call.callId.toString())
        } else {
            CallForegroundService.stopService(context)
        }
    }

    // private fun observeCallState(call: Call) {
    //    // Implementation removed due to unresolved reference. Relying on Socket Events.
    // }

    companion object {
        @Volatile private var instance: TelnyxCommon? = null

        fun getInstance(): TelnyxCommon {
            return instance
                    ?: synchronized(this) { instance ?: TelnyxCommon().also { instance = it } }
        }
    }
}

sealed class TelnyxSocketEvent {
    data object InitState : TelnyxSocketEvent()
    data object OnClientReady : TelnyxSocketEvent()
    data class OnCallAnswered(val callId: UUID) : TelnyxSocketEvent()
    data object OnCallEnded : TelnyxSocketEvent()
}
