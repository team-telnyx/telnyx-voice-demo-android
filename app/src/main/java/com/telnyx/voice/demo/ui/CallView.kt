package com.telnyx.voice.demo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.telnyx.voice.demo.R
import com.telnyx.voice.demo.CallViewModel
import com.telnyx.voice.demo.models.CallUIState
import com.telnyx.voice.demo.models.Provider
import com.telnyx.voice.demo.models.SocketConnectionState
import com.telnyx.voice.demo.ui.components.CallActionButton
import com.telnyx.voice.demo.ui.components.CallControlButton
import com.telnyx.voice.demo.ui.components.CircularCallButton
import com.telnyx.voice.demo.ui.theme.AppColors
import com.telnyx.voice.demo.ui.theme.AppSpacing
import com.telnyx.voice.demo.ui.theme.AppTypography

@Composable
fun CallView(
    viewModel: CallViewModel,
    callState: CallUIState,
    socketState: SocketConnectionState,
    selectedProvider: Provider,
    isMuted: Boolean,
    isSpeakerOn: Boolean,
    onProviderChange: (Provider) -> Unit,
    onMakeCall: (String) -> Unit,
    onAnswerCall: () -> Unit,
    onHangupCall: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleHold: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.BackgroundPrimary)
            .padding(AppSpacing.Screen.horizontal),
        contentAlignment = Alignment.Center
    ) {
        when (callState) {
            is CallUIState.Idle -> IdleCallView(
                selectedProvider = selectedProvider,
                socketState = socketState,
                onProviderChange = onProviderChange,
                onMakeCall = onMakeCall
            )
            is CallUIState.Connecting -> ConnectingCallView(
                callInfo = null,
                onCancel = onHangupCall
            )
            is CallUIState.Ringing -> ConnectingCallView(
                callInfo = callState.callInfo,
                onCancel = onHangupCall
            )
            is CallUIState.Incoming -> IncomingCallView(
                callInfo = callState.callInfo,
                onAnswer = onAnswerCall,
                onReject = onHangupCall
            )
            is CallUIState.Active -> ActiveCallView(
                callInfo = callState.callInfo,
                isMuted = isMuted,
                isSpeakerOn = isSpeakerOn,
                isOnHold = false,
                onToggleMute = onToggleMute,
                onToggleSpeaker = onToggleSpeaker,
                onToggleHold = onToggleHold,
                onEndCall = onHangupCall
            )
            is CallUIState.Held -> ActiveCallView(
                callInfo = callState.callInfo,
                isMuted = isMuted,
                isSpeakerOn = isSpeakerOn,
                isOnHold = true,
                onToggleMute = onToggleMute,
                onToggleSpeaker = onToggleSpeaker,
                onToggleHold = onToggleHold,
                onEndCall = onHangupCall
            )
            is CallUIState.Ended -> EndedCallView()
        }
    }
}

@Composable
private fun IdleCallView(
    selectedProvider: Provider,
    socketState: SocketConnectionState,
    onProviderChange: (Provider) -> Unit,
    onMakeCall: (String) -> Unit
) {
    var destination by remember { mutableStateOf("") }
    val canMakeCall = socketState == SocketConnectionState.READY && destination.isNotBlank()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        Text(
            text = "Make a Call",
            style = AppTypography.HeadingMedium,
            color = AppColors.TextPrimary
        )

        Spacer(modifier = Modifier.height(AppSpacing.xl))

        // Provider Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            ProviderButton(
                provider = Provider.TELNYX,
                isSelected = selectedProvider == Provider.TELNYX,
                onClick = { onProviderChange(Provider.TELNYX) },
                modifier = Modifier.weight(1f)
            )
            ProviderButton(
                provider = Provider.TWILIO,
                isSelected = selectedProvider == Provider.TWILIO,
                onClick = { onProviderChange(Provider.TWILIO) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(AppSpacing.xl))

        // Phone Number Input
        OutlinedTextField(
            value = destination,
            onValueChange = { destination = it },
            placeholder = { Text("Enter a number", color = AppColors.TextTertiary) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AppColors.InputBorderFocused,
                unfocusedBorderColor = AppColors.InputBorder,
                focusedContainerColor = AppColors.InputBackground,
                unfocusedContainerColor = AppColors.InputBackground,
                focusedTextColor = AppColors.TextPrimary,
                unfocusedTextColor = AppColors.TextPrimary
            ),
            textStyle = AppTypography.BodyLarge
        )

        Spacer(modifier = Modifier.height(AppSpacing.xl))

        // Call Button
        IconButton(
            onClick = { onMakeCall(destination) },
            enabled = canMakeCall,
            modifier = Modifier
                .size(AppSpacing.CallControl.largeButtonSize)
                .clip(CircleShape)
                .background(
                    if (canMakeCall) {
                        if (selectedProvider == Provider.TELNYX) AppColors.Telnyx else AppColors.Twilio
                    } else {
                        AppColors.Inactive
                    }
                )
        ) {
            Icon(
                painter = painterResource(R.drawable.baseline_call_24),
                contentDescription = "Make Call",
                tint = Color.White,
                modifier = Modifier.size(AppSpacing.CallControl.largeButtonSize * 0.4f)
            )
        }
    }
}

@Composable
private fun ProviderButton(
    provider: Provider,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when {
        isSelected && provider == Provider.TELNYX -> AppColors.Telnyx.copy(alpha = 0.2f)
        isSelected && provider == Provider.TWILIO -> AppColors.Twilio.copy(alpha = 0.2f)
        else -> AppColors.BackgroundTertiary
    }

    val textColor = when {
        isSelected && provider == Provider.TELNYX -> AppColors.Telnyx
        isSelected && provider == Provider.TWILIO -> AppColors.Twilio
        else -> AppColors.TextSecondary
    }

    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            contentColor = textColor
        )
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(textColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                provider.name,
                style = AppTypography.BodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun ConnectingCallView(
    callInfo: com.telnyx.voice.demo.models.CallInfo?,
    onCancel: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(60.dp),
            color = AppColors.Info
        )

        Spacer(modifier = Modifier.height(AppSpacing.xl))

        if (callInfo != null) {
            Text(
                text = callInfo.remoteName ?: callInfo.remoteNumber,
                style = AppTypography.CallerName,
                color = AppColors.TextPrimary
            )
            Spacer(modifier = Modifier.height(AppSpacing.sm))
            Text(
                text = "Ringing...",
                style = AppTypography.CallStatus,
                color = AppColors.TextSecondary
            )
        } else {
            Text(
                text = "Calling...",
                style = AppTypography.CallStatus,
                color = AppColors.TextSecondary
            )
        }

        Spacer(modifier = Modifier.height(AppSpacing.xxl))

        CallActionButton(
            icon = painterResource(R.drawable.baseline_call_end_24),
            label = "Cancel",
            backgroundColor = AppColors.EndRed,
            onClick = onCancel
        )
    }
}

@Composable
private fun IncomingCallView(
    callInfo: com.telnyx.voice.demo.models.CallInfo,
    onAnswer: () -> Unit,
    onReject: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = null,
            modifier = Modifier.size(AppSpacing.CallControl.iconSize),
            tint = AppColors.TextSecondary
        )

        Spacer(modifier = Modifier.height(AppSpacing.lg))

        Text(
            text = "Incoming Call",
            style = AppTypography.BodyMedium,
            color = AppColors.TextSecondary
        )

        Spacer(modifier = Modifier.height(AppSpacing.sm))

        Text(
            text = callInfo.remoteName ?: callInfo.remoteNumber,
            style = AppTypography.CallerName,
            color = AppColors.TextPrimary
        )

        Spacer(modifier = Modifier.height(AppSpacing.xxl))

        Row(
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.CallControl.spacing)
        ) {
            CallActionButton(
                icon = painterResource(R.drawable.baseline_call_end_24),
                label = "Reject",
                backgroundColor = AppColors.EndRed,
                onClick = onReject
            )
            CallActionButton(
                icon = painterResource(R.drawable.baseline_call_24),
                label = "Answer",
                backgroundColor = AppColors.AnswerGreen,
                onClick = onAnswer
            )
        }
    }
}

@Composable
private fun ActiveCallView(
    callInfo: com.telnyx.voice.demo.models.CallInfo,
    isMuted: Boolean,
    isSpeakerOn: Boolean,
    isOnHold: Boolean,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleHold: () -> Unit,
    onEndCall: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        Text(
            text = callInfo.remoteName ?: callInfo.remoteNumber,
            style = AppTypography.CallerName,
            color = AppColors.TextPrimary
        )

        Spacer(modifier = Modifier.height(AppSpacing.sm))

        Text(
            text = if (isOnHold) "On Hold" else "In Call",
            style = AppTypography.CallStatus,
            color = AppColors.TextSecondary
        )

        Spacer(modifier = Modifier.height(AppSpacing.xxl))

        // Control Buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.CallControl.spacing)
        ) {
            CallControlButton(
                icon = painterResource(if (isMuted) R.drawable.mute_off_24 else R.drawable.mute_24),
                label = "Mute",
                isActive = isMuted,
                activeColor = AppColors.MuteActive,
                onClick = onToggleMute
            )
            CallControlButton(
                icon = painterResource(if (isSpeakerOn) R.drawable.speaker_24 else R.drawable.speaker_off_24),
                label = "Speaker",
                isActive = isSpeakerOn,
                activeColor = AppColors.SpeakerActive,
                onClick = onToggleSpeaker
            )
            CallControlButton(
                icon = painterResource(if (isOnHold) R.drawable.play_24 else R.drawable.pause_24),
                label = "Hold",
                isActive = isOnHold,
                activeColor = AppColors.HoldActive,
                onClick = onToggleHold
            )
        }

        Spacer(modifier = Modifier.height(AppSpacing.xxl))

        // End Call Button
        CallActionButton(
            icon = painterResource(R.drawable.baseline_call_end_24),
            label = "End",
            backgroundColor = AppColors.EndRed,
            onClick = onEndCall
        )
    }
}

@Composable
private fun EndedCallView() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        Icon(
            painter = painterResource(R.drawable.baseline_call_end_24),
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = AppColors.EndRed
        )

        Spacer(modifier = Modifier.height(AppSpacing.lg))

        Text(
            text = "Call Ended",
            style = AppTypography.HeadingSmall,
            color = AppColors.TextPrimary
        )
    }

    // Auto-dismiss after a delay
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(2000)
        // Transition back to idle will be handled by state management
    }
}
