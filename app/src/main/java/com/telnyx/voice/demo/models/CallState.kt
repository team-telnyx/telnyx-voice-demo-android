package com.telnyx.voice.demo.models

sealed class CallState {
    data object Idle : CallState()
    data object Registering : CallState()
    data class Ready(val providers: Set<Provider>) : CallState()
    data class IncomingCall(val callInfo: CallInfo) : CallState()
    data class Ringing(val callInfo: CallInfo) : CallState()
    data class Active(val callInfo: CallInfo, val durationSeconds: Long) : CallState()
    data class Error(val message: String, val provider: Provider?) : CallState()
}
