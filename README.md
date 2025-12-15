# Telnyx Voice Demo - Android

A demonstration Android application showcasing voice calling capabilities using both Telnyx WebRTC and Twilio Voice SDKs.

## Overview

This app demonstrates a dual-provider voice calling system that works with both Telnyx and Twilio platforms. Users can make and receive voice calls, handle incoming call notifications, and manage active calls with features like mute, speaker, and hold.

### Key Features

- **Dual Provider Support**: Choose between Telnyx or Twilio for voice calls
- **Push Notifications**: Receive incoming call alerts via Firebase Cloud Messaging
- **Modern UI**: Built with Jetpack Compose for a native Android experience
- **Persistent Call Notifications**: Keep calls active in the background with foreground service notifications
- **Call Controls**: Mute, speaker, and hold functionality during active calls
- **Background Call Handling**: Answer or decline calls from notification actions

## Architecture

The app is organized into three main modules:

- **app**: Main application with UI, settings, and call management
- **telnyx_logic**: Telnyx WebRTC SDK integration and call handling
- **twilio_logic**: Twilio Voice SDK integration and call handling

## Prerequisites

Before running the app, you'll need:

1. **Android Studio** (Arctic Fox or later)
2. **A Telnyx account** (for Telnyx calling)
3. **A Twilio account** (for Twilio calling)
4. **A backend server** for token generation and call routing
5. **Firebase project** for push notifications

## Setup Instructions

### 1. Configure Your Backend URL

Update the backend server URL in the Constants file:

**File**: `app/src/main/java/com/telnyx/voice/demo/Constants.kt`

```kotlin
object Constants {
    const val BACKEND_URL = "https://your-backend-server.com"
}
```

Replace `"https://your-backend-server.com"` with your actual backend server URL. This server should handle:
- Twilio access token generation
- Call routing and management
- Push notification coordination

### 2. Add Firebase Configuration

Place your Firebase configuration file in the app module:

**File**: `app/google-services.json`

To get this file:
1. Go to your [Firebase Console](https://console.firebase.google.com/)
2. Select your project (or create a new one)
3. Go to Project Settings
4. Download the `google-services.json` file
5. Place it in the `app/` directory

**Important**: Make sure Firebase Cloud Messaging (FCM) is enabled in your Firebase project.

### 3. Configure SIP Credentials (Telnyx)

For Telnyx calling, you'll need:
- SIP username
- SIP password
- Or a valid Telnyx token

These are entered in the app's Settings screen.

### 4. Configure Access Token (Twilio)

For Twilio calling, the app automatically fetches access tokens from your backend server using the identity you provide in the Settings screen.

## Building and Running

1. Open the project in Android Studio
2. Sync Gradle dependencies
3. Connect an Android device or start an emulator (Android 7.0+)
4. Build and run the app

```bash
./gradlew build
./gradlew installDebug
```

## Using the App

### First Launch

1. Open the app and navigate to **Settings** (gear icon)
2. Choose your provider (Telnyx or Twilio)
3. Enter your credentials:
   - **Telnyx**: SIP username and password
   - **Twilio**: Identity (username)
4. The app will automatically register with the selected provider

### Making a Call

1. Return to the home screen
2. Enter a phone number in the format required by your provider
3. Tap the **Call** button
4. The call will connect and show the active call screen

### Receiving a Call

1. When a call comes in, you'll see a notification
2. Tap **Answer** to accept the call
3. Tap **Decline** to reject the call
4. The notification works even when the app is in the background or closed

### During a Call

- **Mute**: Toggle your microphone on/off
- **Speaker**: Switch between earpiece and speakerphone
- **Hold**: Put the call on hold
- **End Call**: Hang up the call

The call will show a persistent notification that allows you to return to the app or end the call directly from the notification.

## Project Structure

```
telnyx-voice-demo-android/
├── app/                        # Main application module
│   ├── MainActivity.kt         # Main UI and navigation
│   ├── CallViewModel.kt        # Call state management
│   ├── SettingsScreen.kt       # Provider configuration
│   └── notification/           # Notification handling
├── telnyx_logic/              # Telnyx SDK integration
│   └── service/               # Telnyx call services
└── twilio_logic/              # Twilio SDK integration
    └── service/               # Twilio call services
```

## Troubleshooting

### Push Notifications Not Working

- Verify `google-services.json` is in the correct location
- Check that FCM is enabled in Firebase Console
- Ensure your backend is configured to send push notifications
- Check device notification permissions

### Calls Not Connecting

- Verify your backend URL in `Constants.kt` is correct
- Check that your credentials are valid in Settings
- Ensure your device has internet connectivity
- Check backend server logs for errors

### Build Errors

- Make sure `google-services.json` exists in the `app/` directory
- Run `./gradlew clean build` to clean and rebuild
- Sync Gradle files in Android Studio
- Check that all SDK versions match requirements

## Technical Details

- **Minimum SDK**: Android 7.0 (API 24)
- **Target SDK**: Android 14 (API 34)
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Architecture**: MVVM with StateFlow
- **Push Notifications**: Firebase Cloud Messaging
- **Voice SDKs**: Telnyx WebRTC SDK, Twilio Voice SDK

## License

This is a demonstration project. Please check the individual SDK licenses for Telnyx and Twilio.

## Support

For issues with:
- **Telnyx SDK**: Visit [Telnyx Support](https://telnyx.com/support)
- **Twilio SDK**: Visit [Twilio Support](https://support.twilio.com/)
- **This demo app**: Open an issue in the repository

---

**Note**: This is a demo application intended for development and testing purposes. For production use, additional security measures, error handling, and compliance with platform guidelines should be implemented.
