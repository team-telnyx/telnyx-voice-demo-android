package com.telnyx.voice.demo

import android.app.Application
import com.telnyx.voice.logic.service.TelnyxService
import com.twilio.voice.logic.service.TwilioService
import timber.log.Timber

class VoiceApplication : Application() {

    lateinit var telnyxService: TelnyxService
        private set

    lateinit var twilioService: TwilioService
        private set

    override fun onCreate() {
        super.onCreate()

        // Initialize Timber for logging
        Timber.plant(Timber.DebugTree())

        // Initialize services
        telnyxService = TelnyxService(this)
        twilioService = TwilioService(this)

        Timber.d("VoiceApplication initialized")
    }
}
