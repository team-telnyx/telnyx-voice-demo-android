package com.telnyx.voice.demo.models

data class CallInfo(
    val callId: String,
    val provider: Provider,
    val remoteNumber: String,
    val remoteName: String?,
    val isIncoming: Boolean,
    val startTime: Long? = null
)
