package com.telnyx.voice.demo.ui.theme

import androidx.compose.ui.graphics.Color

object AppColors {
    // Background Colors
    val BackgroundPrimary = Color(0xFF000000)
    val BackgroundSecondary = Color(0xFF1C1C1E)
    val BackgroundTertiary = Color(0xFF2C2C2E)

    // Text Colors
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFF8E8E93)
    val TextTertiary = Color(0xFF636366)
    val TextInverse = Color(0xFFFFFFFF)

    // Brand Colors
    val Telnyx = Color(0xFF0891B2) // Cyan/Teal
    val Twilio = Color(0xFFDC2626) // Red

    // Status Colors
    val Success = Color(0xFF10B981) // Green
    val Warning = Color(0xFFF59E0B) // Orange
    val Error = Color(0xFFEF4444) // Red
    val Info = Color(0xFF3B82F6) // Blue

    // Call Action Colors
    val AnswerGreen = Color(0xFF10B981)
    val EndRed = Color(0xFFEF4444)
    val MuteActive = Color(0xFF8B5CF6) // Purple
    val SpeakerActive = Color(0xFF3B82F6) // Blue
    val HoldActive = Color(0xFFF59E0B) // Orange/Gold
    val Inactive = Color(0xFF6B7280) // Gray

    // Input Colors
    val InputBackground = Color(0xFF2C2C2E)
    val InputBorder = Color(0xFF3C3C3E)
    val InputBorderFocused = Color(0xFF0891B2)

    // Helper function for opacity
    fun Color.withOpacity(opacity: Float): Color {
        return this.copy(alpha = opacity)
    }
}
