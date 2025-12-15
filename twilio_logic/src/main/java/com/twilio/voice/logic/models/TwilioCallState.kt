package com.twilio.voice.logic.models

import com.twilio.voice.CallInvite

sealed class TwilioCallState {
    data object Unregistered : TwilioCallState()
    data object Registering : TwilioCallState()
    data object Registered : TwilioCallState()
    data class IncomingCall(val callInfo: CallInfo, val callInvite: CallInvite) : TwilioCallState()
    data class Ringing(val callInfo: CallInfo) : TwilioCallState()
    data class Active(val callInfo: CallInfo) : TwilioCallState()
    data class Error(val message: String) : TwilioCallState()
}
