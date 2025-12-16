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
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.gson.Gson
import com.telnyx.voice.demo.models.Provider
import com.telnyx.voice.demo.ui.MainScreen
import com.telnyx.voice.demo.ui.theme.TelnyxVoiceDemoAndroidTheme
import com.telnyx.webrtc.sdk.model.PushMetaData
import timber.log.Timber

class MainActivity : ComponentActivity(), DefaultLifecycleObserver {

    private val callViewModel: CallViewModel by viewModels()

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            // Handle permissions granted/rejected
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super<ComponentActivity>.onCreate(savedInstanceState)
        lifecycle.addObserver(this)

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
        setIntent(intent)  // Important: update the intent
        // Handle intent when activity is already running
        handleNotificationIntent(intent)
    }

    override fun onStop(owner: LifecycleOwner) {
        super<DefaultLifecycleObserver>.onStop(owner)
        // Note: Disconnection is now handled by telnyx_common's lifecycle management
        Timber.d("MainActivity: onStop")
    }

    private fun handleNotificationIntent(intent: Intent?) {
        val action = intent?.getStringExtra(ACTION_KEY)

        when (action) {
            ACT_ANSWER_CALL, ACT_OPEN_TO_REPLY -> {
                val callId = intent.getStringExtra(EXTRA_CALL_ID)
                val provider = intent.getStringExtra(EXTRA_PROVIDER)
                val pushMetadataJson = intent.getStringExtra(EXTRA_PUSH_METADATA)

                Timber.d("MainActivity: Handling $action - provider=$provider, callId=$callId, hasPushMetadata=${pushMetadataJson != null}")

                // Dismiss notification
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                callId?.let { notificationManager.cancel(it.hashCode()) }

                // Answer the call via ViewModel (only for ACT_ANSWER_CALL)
                if (action == ACT_ANSWER_CALL) {
                    when (provider) {
                        "TELNYX" -> {
                            // Note: telnyx_common handles push notification auto-answer
                            // Just call answerCall - the ViewModel will handle it
                            Timber.d("Answering Telnyx call")
                            callViewModel.answerCall()
                        }
                        "TWILIO" -> {
                            Timber.d("Answering Twilio call")
                            callViewModel.answerCall()
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_KEY = "ext_key_do_action"
        const val ACT_ANSWER_CALL = "answer"
        const val ACT_OPEN_TO_REPLY = "open_to_reply"
        const val EXTRA_CALL_ID = "callId"
        const val EXTRA_PROVIDER = "provider"
        const val EXTRA_PUSH_METADATA = "pushMetadata"
    }
}