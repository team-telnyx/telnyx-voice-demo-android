package com.telnyx.voice.demo.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.telnyx.voice.demo.CallViewModel
import com.telnyx.voice.demo.models.CallState

@Composable
fun HomeScreen(viewModel: CallViewModel) {
        val callState by viewModel.callState.collectAsState()

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

        var twilioToken by remember { mutableStateOf("") }
        var telnyxToken by remember { mutableStateOf("") }
        var fcmToken by remember {
                mutableStateOf("dummy_fcm_token")
        } // In real app, get from Firebase

        Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
        ) {
                Text(
                        text = "Telnyx & Twilio Hybrid Demo",
                        style = MaterialTheme.typography.headlineMedium
                )
                Spacer(modifier = Modifier.height(24.dp))

                Text(text = "Status: ${getStatusText(callState)}", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(24.dp))

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
                ) { Text("Register Twilio") }

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

                Spacer(modifier = Modifier.height(24.dp))
                Text("Outbound Call", style = MaterialTheme.typography.titleMedium)

                var destNumber by remember { mutableStateOf("") }
                OutlinedTextField(
                        value = destNumber,
                        onValueChange = { destNumber = it },
                        label = { Text("Destination Number") },
                        modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                        onClick = {
                                viewModel.makeCall(
                                        com.telnyx.voice.demo.models.Provider.TELNYX,
                                        destNumber,
                                        mapOf(
                                                "name" to telnyxUser.ifEmpty { "TelnyxUser" },
                                                "number" to "1234567890"
                                        )
                                )
                        },
                        modifier = Modifier.fillMaxWidth()
                ) { Text("Call (Telnyx)") }
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
