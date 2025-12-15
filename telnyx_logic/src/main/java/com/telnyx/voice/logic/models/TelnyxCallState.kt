package com.telnyx.voice.logic.models

sealed class TelnyxCallState {
    data object Disconnected : TelnyxCallState()
    data object Connecting : TelnyxCallState()
    data object Ready : TelnyxCallState()
    data class IncomingCall(val callInfo: CallInfo) : TelnyxCallState()
    data class Ringing(val callInfo: CallInfo) : TelnyxCallState()
    data class Active(val callInfo: CallInfo) : TelnyxCallState()
    data class Error(val message: String) : TelnyxCallState()
}
