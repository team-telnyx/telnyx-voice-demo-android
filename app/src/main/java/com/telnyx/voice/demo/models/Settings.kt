package com.telnyx.voice.demo.models

data class TelnyxSettings(
    val sipUsername: String = "",
    val sipPassword: String = "",
    val reconnectClient: Boolean = true,
    val forceRelayCandidate: Boolean = false,
    val enableQualityMetrics: Boolean = true,
    val debug: Boolean = false,
    val ringtone: String = "incoming_call.mp3",
    val ringBackTone: String = "ringback_tone.mp3"
) {
    val hasValidCredentials: Boolean
        get() = sipUsername.trim().isNotEmpty() && sipPassword.trim().isNotEmpty()
}

data class TwilioSettings(
    val identity: String = "",
    val backendURL: String = "https://unmodern-mckinley-nonprobatory.ngrok-free.dev"
) {
    val hasValidCredentials: Boolean
        get() = identity.trim().isNotEmpty() && backendURL.trim().isNotEmpty()
}

enum class SocketConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    READY
}

sealed class CallUIState {
    object Idle : CallUIState()
    object Connecting : CallUIState()
    data class Ringing(val callInfo: CallInfo) : CallUIState()
    data class Incoming(val callInfo: CallInfo) : CallUIState()
    data class Active(val callInfo: CallInfo) : CallUIState()
    data class Held(val callInfo: CallInfo) : CallUIState()
    object Ended : CallUIState()

    val isCallActive: Boolean
        get() = this is Active || this is Held
}
