package com.telnyx.voice.demo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.telnyx.voice.demo.CallViewModel
import com.telnyx.voice.demo.models.CallUIState
import com.telnyx.voice.demo.models.Provider
import com.telnyx.voice.demo.models.SocketConnectionState
import com.telnyx.voice.demo.ui.theme.AppColors
import com.telnyx.voice.demo.ui.theme.AppSpacing
import com.telnyx.voice.demo.ui.theme.AppTypography
import com.telnyx.voice.demo.util.SettingsStorage

@Composable
fun MainScreen(viewModel: CallViewModel) {
    val context = LocalContext.current

    // Observe states
    val callState by viewModel.callUIState.collectAsState()
    val socketState by viewModel.socketState.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()
    val isSpeakerOn by viewModel.isSpeakerOn.collectAsState()
    val tokenFetchState by viewModel.tokenFetchState.collectAsState()

    // Provider selection
    var selectedProvider by remember {
        mutableStateOf(SettingsStorage.getActiveProvider(context))
    }

    // Settings dialog
    var showSettings by remember { mutableStateOf(false) }

    // Auto-connect on launch
    LaunchedEffect(Unit) {
        viewModel.connectAll()

        // Check if Twilio needs auto-registration (first launch or token changed or expired)
        val twilioSettings = SettingsStorage.getTwilioSettings(context)
        val fcmToken = SettingsStorage.getPushToken(context)

        if (twilioSettings.hasValidCredentials && fcmToken != null) {
            if (SettingsStorage.needsTwilioReregistration(context, fcmToken)) {
                val reason = when {
                    SettingsStorage.getTwilioRegisteredToken(context) == null -> "first launch"
                    SettingsStorage.getTwilioRegisteredToken(context) != fcmToken -> "token changed"
                    else -> "registration expired"
                }
                timber.log.Timber.i("Triggering Twilio auto-registration: $reason")
                viewModel.fetchTwilioToken(twilioSettings.identity, fcmToken)
            }
        }
    }

    // Auto-register Twilio when token is fetched
    LaunchedEffect(tokenFetchState) {
        if (tokenFetchState is CallViewModel.TokenFetchState.Success) {
            val successState = tokenFetchState as CallViewModel.TokenFetchState.Success
            viewModel.registerTwilio(
                successState.token,
                SettingsStorage.getPushToken(context) ?: ""
            )
            // Update selected provider to Twilio since we just registered
            selectedProvider = Provider.TWILIO
            SettingsStorage.saveActiveProvider(context, Provider.TWILIO)
            viewModel.clearTokenFetchState()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.BackgroundPrimary)
    ) {
        // Top Bar
        TopBar(
            socketState = socketState,
            onSettingsClick = { showSettings = true }
        )

        // Call View
        CallView(
            viewModel = viewModel,
            callState = callState,
            socketState = socketState,
            selectedProvider = selectedProvider,
            isMuted = isMuted,
            isSpeakerOn = isSpeakerOn,
            onProviderChange = { provider ->
                if (callState is CallUIState.Idle) {
                    selectedProvider = provider
                    SettingsStorage.saveActiveProvider(context, provider)
                }
            },
            onMakeCall = { destination ->
                SettingsStorage.saveLastDestination(context, destination)
                viewModel.makeCall(selectedProvider, destination, mapOf(
                    "name" to "DemoUser",
                    "number" to "1234567890"
                ))
            },
            onAnswerCall = viewModel::answerCall,
            onHangupCall = viewModel::hangupCall,
            onToggleMute = viewModel::toggleMute,
            onToggleSpeaker = viewModel::toggleSpeaker,
            onToggleHold = viewModel::toggleHold
        )
    }

    // Settings Dialog
    if (showSettings) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showSettings = false }
        ) {
            SettingsScreen(
                onDismiss = { showSettings = false },
                onDisconnect = {
                    viewModel.disconnectAll()
                },
                onSaveAndReconnect = { isTelnyx ->
                    if (isTelnyx) {
                        val telnyxSettings = SettingsStorage.getTelnyxSettings(context)
                        viewModel.connectTelnyxWithCredentials(
                            telnyxSettings.sipUsername,
                            telnyxSettings.sipPassword,
                            SettingsStorage.getPushToken(context) ?: ""
                        )
                    } else {
                        val twilioSettings = SettingsStorage.getTwilioSettings(context)
                        viewModel.fetchTwilioToken(
                            twilioSettings.identity,
                            SettingsStorage.getPushToken(context) ?: ""
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun TopBar(
    socketState: SocketConnectionState,
    onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.BackgroundSecondary)
            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.xxl),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Connection Status
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(getStatusColor(socketState))
            )
            Text(
                text = getStatusText(socketState),
                style = AppTypography.Caption,
                color = AppColors.TextSecondary
            )
        }

        // Settings Button
        IconButton(onClick = onSettingsClick) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = AppColors.TextPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private fun getStatusColor(state: SocketConnectionState): androidx.compose.ui.graphics.Color {
    return when (state) {
        SocketConnectionState.DISCONNECTED -> AppColors.Error
        SocketConnectionState.CONNECTING -> AppColors.Warning
        SocketConnectionState.CONNECTED -> AppColors.Info
        SocketConnectionState.READY -> AppColors.Success
    }
}

private fun getStatusText(state: SocketConnectionState): String {
    return when (state) {
        SocketConnectionState.DISCONNECTED -> "Disconnected"
        SocketConnectionState.CONNECTING -> "Connecting..."
        SocketConnectionState.CONNECTED -> "Connected"
        SocketConnectionState.READY -> "Ready"
    }
}
