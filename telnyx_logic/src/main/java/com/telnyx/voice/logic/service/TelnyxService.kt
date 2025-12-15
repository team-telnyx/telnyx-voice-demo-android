package com.telnyx.voice.logic.service

import android.content.Context
import com.telnyx.voice.logic.models.CallInfo
import com.telnyx.voice.logic.models.Provider
import com.telnyx.voice.logic.models.TelnyxCallState
import com.telnyx.voice.logic.util.CredentialStorage
import com.telnyx.webrtc.sdk.Call
import com.telnyx.webrtc.sdk.CredentialConfig
import com.telnyx.webrtc.sdk.TelnyxClient
import com.telnyx.webrtc.sdk.TokenConfig
import com.telnyx.webrtc.sdk.model.PushMetaData
import com.telnyx.webrtc.sdk.model.TxServerConfiguration
import com.telnyx.webrtc.sdk.model.SocketMethod
import com.telnyx.webrtc.sdk.model.SocketStatus
import com.telnyx.webrtc.sdk.verto.receive.InviteResponse
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
import java.util.UUID

class TelnyxService(private val context: Context) {

    private val _state = MutableStateFlow<TelnyxCallState>(TelnyxCallState.Disconnected)
    val state: StateFlow<TelnyxCallState> = _state.asStateFlow()

    private var telnyxClient: TelnyxClient? = null
    private var currentCall: Call? = null
    private var pendingInviteResponse: InviteResponse? = null
    private var pendingPushMetadata: PushMetaData? = null
    private var isReconnectingForPush = false

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

        // SAVE CREDENTIALS for push reconnection
        CredentialStorage.saveTokenLogin(context, config)

        telnyxClient?.connect(TxServerConfiguration(), tokenConfig = config, autoLogin = true)
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

        // SAVE CREDENTIALS for push reconnection
        CredentialStorage.saveCredentialLogin(context, config)

        telnyxClient?.connect(TxServerConfiguration(), credentialConfig = config, autoLogin = true)
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
        val invite = pendingInviteResponse
        if (invite == null) {
            Timber.e("Cannot answer: no pending invite")
            _state.value = TelnyxCallState.Error("No incoming call to answer")
            return
        }

        Timber.d("Answering call: ${invite.callId}")

        try {
            // Get current call info to preserve it
            val currentCallInfo = when (val state = _state.value) {
                is TelnyxCallState.IncomingCall -> state.callInfo
                else -> null
            }

            // Accept the call using Telnyx SDK
            telnyxClient?.acceptCall(invite.callId, invite.callerIdNumber)

            // Optimistically transition to Active immediately for better UX
            // The ANSWER socket event will confirm this transition
            currentCallInfo?.let { info ->
                val activeCallInfo = info.copy(startTime = System.currentTimeMillis())
                _state.value = TelnyxCallState.Active(activeCallInfo)
                val callerInfo = info.remoteName ?: info.remoteNumber
                CallForegroundService.startService(context, info.callId, callerInfo)
                Timber.d("Optimistically transitioned to Active: ${info.callId}")
            }

            pendingInviteResponse = null

        } catch (e: Exception) {
            Timber.e(e, "Error accepting call")
            _state.value = TelnyxCallState.Error("Failed to answer call: ${e.message}")
            pendingInviteResponse = null
        }
    }

    /**
     * Answers an incoming call from push notification.
     * This reconnects the socket with push metadata, waits for INVITE, then auto-answers.
     */
    fun answerFromPush() {
        val pushMeta = pendingPushMetadata
        if (pushMeta == null) {
            Timber.e("Cannot answer from push: no pending push metadata")
            _state.value = TelnyxCallState.Error("No push data available")
            return
        }

        val storedConfig = CredentialStorage.getStoredConfig(context)
        if (storedConfig == null) {
            Timber.e("Cannot answer from push: no stored credentials")
            _state.value = TelnyxCallState.Error("No stored credentials")
            return
        }

        Timber.d("Answering call from push: reconnecting socket")
        isReconnectingForPush = true

        try {
            // Reconnect socket WITH push metadata
            // The SDK will deliver the INVITE after connection
            when (storedConfig) {
                is TokenConfig -> {
                    telnyxClient?.connect(
                        providedServerConfig = TxServerConfiguration(),
                        tokenConfig = storedConfig,
                        txPushMetaData = pushMeta.toJson(),
                        autoLogin = false
                    )
                }
                is CredentialConfig -> {
                    telnyxClient?.connect(
                        providedServerConfig = TxServerConfiguration(),
                        credentialConfig = storedConfig,
                        txPushMetaData = pushMeta.toJson(),
                        autoLogin = false
                    )
                }
            }

            // Socket response handler will detect INVITE and auto-answer
            // (See INVITE handler update)

        } catch (e: Exception) {
            Timber.e(e, "Error answering from push")
            _state.value = TelnyxCallState.Error("Failed to reconnect: ${e.message}")
            isReconnectingForPush = false
            pendingPushMetadata = null
        }
    }

    fun endCall() {
        currentCall?.let { call ->
            // For outbound calls, we have the Call object
            call.endCall(call.callId)
        } ?: run {
            // For incoming calls, try to end using callId from state
            val currentState = _state.value
            val callId = when (currentState) {
                is TelnyxCallState.Active -> currentState.callInfo.callId
                is TelnyxCallState.Ringing -> currentState.callInfo.callId
                is TelnyxCallState.IncomingCall -> currentState.callInfo.callId
                else -> null
            }

            callId?.let { id ->
                try {
                    val uuid = UUID.fromString(id)
                    // Try to create a minimal Call operation to end the call
                    // Note: This may not work if SDK requires full Call object
                    telnyxClient?.endCall(uuid)
                    Timber.d("Attempted to end call: $id")
                } catch (e: Exception) {
                    Timber.e(e, "Error ending call")
                }
            }
        }
        currentCall = null
        CallForegroundService.stopService(context)
        Timber.d("Call ended")
    }

    /**
     * Declines an incoming call from push notification.
     * Uses SDK's connectWithDeclinePush() to decline without fully connecting.
     */
    fun declineFromPush() {
        val pushMeta = pendingPushMetadata
        if (pushMeta == null) {
            Timber.e("Cannot decline from push: no pending push metadata")
            return
        }

        val storedConfig = CredentialStorage.getStoredConfig(context)
        if (storedConfig == null) {
            Timber.e("Cannot decline from push: no stored credentials")
            return
        }

        Timber.d("Declining call from push")

        try {
            // Use SDK's special decline method
            when (storedConfig) {
                is TokenConfig -> {
                    telnyxClient?.connectWithDeclinePush(
                        providedServerConfig = TxServerConfiguration(),
                        config = storedConfig,
                        txPushMetaData = pushMeta.toJson()
                    )
                }
                is CredentialConfig -> {
                    telnyxClient?.connectWithDeclinePush(
                        providedServerConfig = TxServerConfiguration(),
                        config = storedConfig,
                        txPushMetaData = pushMeta.toJson()
                    )
                }
            }

            // Clear push state
            pendingPushMetadata = null
            _state.value = TelnyxCallState.Ready

            Timber.d("Call declined from push")

        } catch (e: Exception) {
            Timber.e(e, "Error declining from push")
        }
    }

    fun hasPendingPushMetadata(): Boolean = pendingPushMetadata != null

    fun handlePushData(metadata: PushMetaData) {
        Timber.d("Telnyx push received: callId=${metadata.callId}, caller=${metadata.callerName}")

        // STORE push metadata for later use when answering/declining
        pendingPushMetadata = metadata

        // Create call info from push metadata
        val callInfo = CallInfo(
            callId = metadata.callId?.toString() ?: "unknown",
            provider = Provider.TELNYX,
            remoteNumber = metadata.callerNumber ?: "Unknown",
            remoteName = metadata.callerName,
            isIncoming = true
        )

        _state.value = TelnyxCallState.IncomingCall(callInfo)

        // NOTE: Do NOT reconnect here - wait for user action (answer/decline)
        // This allows notification to display without auto-answering
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
                try {
                    val inviteResponse = data.result as? InviteResponse
                    inviteResponse?.let { invite ->
                        pendingInviteResponse = invite

                        val callInfo = CallInfo(
                            callId = invite.callId.toString(),
                            provider = Provider.TELNYX,
                            remoteNumber = invite.callerIdNumber ?: "Unknown",
                            remoteName = invite.callerIdName ?: invite.callerIdNumber,
                            isIncoming = true
                        )

                        // CHECK: Are we reconnecting for push answer?
                        if (isReconnectingForPush) {
                            Timber.d("Auto-answering call from push")

                            // Auto-answer immediately
                            telnyxClient?.acceptCall(invite.callId, invite.callerIdNumber)

                            // Clear push state
                            isReconnectingForPush = false
                            pendingPushMetadata = null

                            // State will be updated to Active when ANSWER event arrives
                        } else {
                            // Normal incoming call (socket was already connected)
                            _state.value = TelnyxCallState.IncomingCall(callInfo)
                            Timber.d("Incoming call from ${invite.callerIdName} (${invite.callerIdNumber})")
                        }
                    } ?: run {
                        Timber.e("Failed to parse InviteResponse")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error handling INVITE")
                    isReconnectingForPush = false
                }
            }
            SocketMethod.ANSWER.methodName -> {
                Timber.d("Call answered - ANSWER event received")

                // Get the current call info from state
                val currentState = _state.value
                Timber.d("Current state when ANSWER received: ${currentState::class.simpleName}")

                val existingCallInfo = when (currentState) {
                    is TelnyxCallState.Ringing -> currentState.callInfo
                    is TelnyxCallState.IncomingCall -> currentState.callInfo
                    else -> null
                }

                existingCallInfo?.let { info ->
                    val activeCallInfo = info.copy(startTime = System.currentTimeMillis())
                    _state.value = TelnyxCallState.Active(activeCallInfo)
                    val callerInfo = info.remoteName ?: info.remoteNumber
                    CallForegroundService.startService(context, info.callId, callerInfo)

                    Timber.d("✅ Call is now ACTIVE: callId=${info.callId}, remote=${info.remoteNumber}")
                } ?: run {
                    Timber.e("❌ No call info available when ANSWER received, currentState=$currentState")
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
