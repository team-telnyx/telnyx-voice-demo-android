package com.telnyx.voice.demo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.telnyx.voice.demo.CallViewModel
import com.telnyx.voice.demo.models.CallInfo
import com.telnyx.voice.demo.models.CallState
import com.telnyx.voice.demo.models.Provider

@Composable
fun ActiveCallScreen(viewModel: CallViewModel, callState: CallState) {
    val callInfo = when (callState) {
        is CallState.IncomingCall -> callState.callInfo
        is CallState.Ringing -> callState.callInfo
        is CallState.Active -> callState.callInfo
        else -> return
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top section - Call info
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Spacer(modifier = Modifier.height(48.dp))

                // Provider badge
                ProviderBadge(callInfo.provider)

                Spacer(modifier = Modifier.height(32.dp))

                // Caller name/number
                Text(
                    text = callInfo.remoteName ?: callInfo.remoteNumber,
                    style = MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center
                )

                if (callInfo.remoteName != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = callInfo.remoteNumber,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Call direction
                Text(
                    text = if (callInfo.isIncoming) "↓ Incoming" else "↑ Outgoing",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            // Middle section - Status/Duration
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (callState) {
                    is CallState.Ringing -> {
                        Text(
                            text = "Ringing...",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    is CallState.IncomingCall -> {
                        Text(
                            text = "Incoming Call",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    is CallState.Active -> {
                        Text(
                            text = formatDuration(callState.durationSeconds),
                            style = MaterialTheme.typography.displayLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    else -> {}
                }
            }

            // Bottom section - Call controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Answer button (only for incoming calls)
                if (callState is CallState.IncomingCall) {
                    FloatingActionButton(
                        onClick = { viewModel.answerCall() },
                        containerColor = Color(0xFF4CAF50),
                        contentColor = Color.White,
                        modifier = Modifier.size(72.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Answer",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // Hangup button (always visible)
                FloatingActionButton(
                    onClick = { viewModel.hangupCall() },
                    containerColor = Color(0xFFE53935),
                    contentColor = Color.White,
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Hangup",
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun ProviderBadge(provider: Provider) {
    val backgroundColor = when (provider) {
        Provider.TELNYX -> Color(0xFF0066CC)
        Provider.TWILIO -> Color(0xFFF22F46)
    }

    Surface(
        shape = MaterialTheme.shapes.small,
        color = backgroundColor,
        modifier = Modifier.padding(8.dp)
    ) {
        Text(
            text = provider.name,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

fun formatDuration(seconds: Long): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return String.format("%02d:%02d", minutes, remainingSeconds)
}
