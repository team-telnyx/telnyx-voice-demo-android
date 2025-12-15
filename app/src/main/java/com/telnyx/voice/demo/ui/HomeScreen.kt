package com.telnyx.voice.demo.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.telnyx.voice.demo.CallViewModel
import com.telnyx.voice.demo.VoiceApplication
import com.telnyx.voice.demo.models.CallState
import com.telnyx.voice.demo.models.Provider
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(viewModel: CallViewModel) {
        val callState by viewModel.callState.collectAsState()
        val tokenFetchState by viewModel.tokenFetchState.collectAsState()

        // Navigate to active call screen when call is active/ringing/incoming
        when (callState) {
                is CallState.IncomingCall,
                is CallState.Ringing,
                is CallState.Active -> {
                        ActiveCallScreen(viewModel, callState)
                        return
                }
                else -> {
                        // Show registration screen
                }
        }

        var twilioIdentity by remember { mutableStateOf("") }
        var twilioToken by remember { mutableStateOf("") }
        var telnyxToken by remember { mutableStateOf("") }

        // Get actual FCM token from VoiceApplication
        val context = LocalContext.current
        val app = context.applicationContext as VoiceApplication
        var fcmToken by remember { mutableStateOf(app.fcmToken ?: "fetching...") }

        // Update FCM token when it becomes available
        LaunchedEffect(Unit) {
                delay(1000) // Wait for token to be fetched
                app.fcmToken?.let { token ->
                        fcmToken = token
                }
        }

        // Auto-register when token is fetched successfully
        LaunchedEffect(tokenFetchState) {
                if (tokenFetchState is CallViewModel.TokenFetchState.Success) {
                        val successState = tokenFetchState as CallViewModel.TokenFetchState.Success
                        viewModel.registerTwilio(successState.token, fcmToken)
                        viewModel.clearTokenFetchState()
                }
        }

        // Show call screen if authenticated
        if (callState is CallState.Ready) {
                CallScreen(viewModel, callState as CallState.Ready)
                return
        }

        // Show registration screen
        Column(
                modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
        ) {
                Text(
                        text = "Telnyx & Twilio Hybrid Demo",
                        style = MaterialTheme.typography.headlineMedium
                )
                Spacer(modifier = Modifier.height(24.dp))

                Text(text = "Status: ${getStatusText(callState)}", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(8.dp))

                // Display FCM Token for copying
                Text(
                        text = "FCM Token:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                )
                Text(
                        text = fcmToken,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(24.dp))

                // Twilio Identity & Auto-fetch
                OutlinedTextField(
                        value = twilioIdentity,
                        onValueChange = { twilioIdentity = it },
                        label = { Text("Twilio Identity") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = tokenFetchState !is CallViewModel.TokenFetchState.Loading
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                        onClick = { viewModel.fetchTwilioToken(twilioIdentity, fcmToken) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = twilioIdentity.isNotBlank() && tokenFetchState !is CallViewModel.TokenFetchState.Loading
                ) {
                        when (tokenFetchState) {
                                is CallViewModel.TokenFetchState.Loading -> Text("Fetching Token...")
                                else -> Text("Fetch & Register Twilio")
                        }
                }

                // Show error if token fetch failed
                if (tokenFetchState is CallViewModel.TokenFetchState.Error) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                                text = (tokenFetchState as CallViewModel.TokenFetchState.Error).message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                        )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("OR manually enter token:", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                        value = twilioToken,
                        onValueChange = { twilioToken = it },
                        label = { Text("Twilio Access Token") },
                        modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                        onClick = { viewModel.registerTwilio(twilioToken, fcmToken) },
                        modifier = Modifier.fillMaxWidth()
                ) { Text("Register Twilio (Manual)") }

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                        value = telnyxToken,
                        onValueChange = { telnyxToken = it },
                        label = { Text("Telnyx SIP Token") },
                        modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                        onClick = { viewModel.connectTelnyx(telnyxToken, fcmToken) },
                        modifier = Modifier.fillMaxWidth()
                ) { Text("Connect Telnyx (Token)") }

                Spacer(modifier = Modifier.height(16.dp))
                Text("OR", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(16.dp))

                var telnyxUser by remember { mutableStateOf("") }
                var telnyxPassword by remember { mutableStateOf("") }

                OutlinedTextField(
                        value = telnyxUser,
                        onValueChange = { telnyxUser = it },
                        label = { Text("Telnyx SIP Username") },
                        modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                        value = telnyxPassword,
                        onValueChange = { telnyxPassword = it },
                        label = { Text("Telnyx SIP Password") },
                        modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                        onClick = {
                                viewModel.connectTelnyxWithCredentials(
                                        telnyxUser,
                                        telnyxPassword,
                                        fcmToken
                                )
                        },
                        modifier = Modifier.fillMaxWidth()
                ) { Text("Connect Telnyx (Credentials)") }
        }
}

@Composable
fun CallScreen(viewModel: CallViewModel, readyState: CallState.Ready) {
        var selectedProvider by remember { mutableStateOf(readyState.providers.first()) }
        var destinationNumber by remember { mutableStateOf("") }

        Column(
                modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
        ) {
                Text(
                        text = "Make a Call",
                        style = MaterialTheme.typography.headlineMedium
                )
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                        text = "Authenticated providers: ${readyState.providers.joinToString(", ")}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Provider selector
                Text(
                        text = "Select Provider",
                        style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Provider toggle buttons
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                        readyState.providers.forEach { provider ->
                                FilterChip(
                                        selected = selectedProvider == provider,
                                        onClick = { selectedProvider = provider },
                                        label = { Text(provider.name) },
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                )
                        }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Destination number input
                OutlinedTextField(
                        value = destinationNumber,
                        onValueChange = { destinationNumber = it },
                        label = { Text("Destination Number") },
                        placeholder = { Text("+1234567890") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Call button
                Button(
                        onClick = {
                                viewModel.makeCall(
                                        selectedProvider,
                                        destinationNumber,
                                        mapOf(
                                                "name" to "DemoUser",
                                                "number" to "1234567890"
                                        )
                                )
                        },
                        modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                        enabled = destinationNumber.isNotBlank()
                ) {
                        Text(
                                text = "Call via ${selectedProvider.name}",
                                style = MaterialTheme.typography.titleMedium
                        )
                }
        }
}

private fun getStatusText(callState: CallState): String {
        return when (callState) {
                is CallState.Idle -> "Idle"
                is CallState.Registering -> "Registering..."
                is CallState.Ready -> "Ready (${callState.providers.joinToString(", ")})"
                is CallState.IncomingCall -> "Incoming call from ${callState.callInfo.remoteNumber}"
                is CallState.Ringing -> "Ringing ${callState.callInfo.remoteNumber}"
                is CallState.Active -> "Active call (${callState.durationSeconds}s)"
                is CallState.Error -> "Error: ${callState.message}"
        }
}
