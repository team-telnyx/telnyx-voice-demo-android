package com.telnyx.voice.demo.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.telnyx.voice.demo.CallManager

@Composable
fun HomeScreen() {
    val status by CallManager.status.collectAsState()
    var twilioToken by remember { mutableStateOf("") }
    var telnyxToken by remember { mutableStateOf("") }
    var fcmToken by remember { mutableStateOf("dummy_fcm_token") } // In real app, get from Firebase

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Telnyx & Twilio Hybrid Demo", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(24.dp))

        Text(text = "Status: $status", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = twilioToken,
            onValueChange = { twilioToken = it },
            label = { Text("Twilio Access Token") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { CallManager.registerTwilio(twilioToken, fcmToken) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Register Twilio")
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = telnyxToken,
            onValueChange = { telnyxToken = it },
            label = { Text("Telnyx SIP Token") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { CallManager.connectTelnyx(telnyxToken, fcmToken) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Connect Telnyx")
        }
    }
}
