package com.telnyx.voice.demo

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.telnyx.voice.demo.models.Provider
import com.telnyx.voice.demo.ui.MainScreen
import com.telnyx.voice.demo.ui.theme.TelnyxVoiceDemoAndroidTheme
import timber.log.Timber

class MainActivity : ComponentActivity() {

    private val callViewModel: CallViewModel by viewModels()

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            // Handle permissions granted/rejected
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.POST_NOTIFICATIONS
            )
        )

        setContent {
            TelnyxVoiceDemoAndroidTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(callViewModel)
                }
            }
        }

        // Handle intent if launched from notification
        handleNotificationIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle intent when activity is already running
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        if (intent?.action == CallActionReceiver.ACTION_ANSWER_CALL) {
            val callId = intent.getStringExtra(CallActionReceiver.EXTRA_CALL_ID)
            val provider = intent.getStringExtra(CallActionReceiver.EXTRA_PROVIDER)

            Timber.d("MainActivity: Handling answer intent - provider=$provider, callId=$callId")

            // Dismiss notification
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            callId?.let { notificationManager.cancel(it.hashCode()) }

            // Answer the call via ViewModel
            val app = application as VoiceApplication
            when (provider) {
                "TELNYX" -> {
                    if (app.telnyxService.hasPendingPushMetadata()) {
                        Timber.d("Answering Telnyx call from push")
                        app.telnyxService.answerFromPush()
                    } else {
                        Timber.d("Answering Telnyx call (socket already connected)")
                        callViewModel.answerCall()
                    }
                }
                "TWILIO" -> {
                    Timber.d("Answering Twilio call")
                    callViewModel.answerCall()
                }
            }
        }
    }
}