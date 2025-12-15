package com.telnyx.voice.demo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.telnyx.voice.demo.models.TelnyxSettings
import com.telnyx.voice.demo.models.TwilioSettings
import com.telnyx.voice.demo.ui.theme.AppColors
import com.telnyx.voice.demo.ui.theme.AppSpacing
import com.telnyx.voice.demo.ui.theme.AppTypography
import com.telnyx.voice.demo.util.SettingsStorage

enum class SettingsTab {
    TELNYX, TWILIO
}

@Composable
fun SettingsScreen(
    onDismiss: () -> Unit,
    onDisconnect: () -> Unit,
    onSaveAndReconnect: (isTelnyx: Boolean) -> Unit
) {
    var selectedTab by remember { mutableStateOf(SettingsTab.TELNYX) }
    val context = LocalContext.current

    // Telnyx state
    var telnyxSettings by remember { mutableStateOf(SettingsStorage.getTelnyxSettings(context)) }

    // Twilio state
    var twilioSettings by remember { mutableStateOf(SettingsStorage.getTwilioSettings(context)) }

    // Push token
    val pushToken = remember { SettingsStorage.getPushToken(context) ?: "Not available" }
    var tokenCopied by remember { mutableStateOf(false) }

    // Reset tokenCopied after 2 seconds
    LaunchedEffect(tokenCopied) {
        if (tokenCopied) {
            kotlinx.coroutines.delay(2000)
            tokenCopied = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.BackgroundPrimary)
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppColors.BackgroundSecondary)
                .padding(horizontal = AppSpacing.md, vertical = AppSpacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = {
                onDisconnect()
                onDismiss()
            }) {
                Text("Disconnect", color = AppColors.TextPrimary, style = AppTypography.BodyMedium)
            }

            Text(
                "Settings",
                style = AppTypography.HeadingSmall,
                color = AppColors.TextPrimary
            )

            TextButton(onClick = onDismiss) {
                Text("Done", color = AppColors.TextPrimary, style = AppTypography.BodyMedium)
            }
        }

        // Tab Selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppColors.BackgroundSecondary)
                .padding(horizontal = AppSpacing.md)
        ) {
            TabButton(
                text = "Telnyx",
                isSelected = selectedTab == SettingsTab.TELNYX,
                color = AppColors.Telnyx,
                onClick = { selectedTab = SettingsTab.TELNYX },
                modifier = Modifier.weight(1f)
            )
            TabButton(
                text = "Twilio",
                isSelected = selectedTab == SettingsTab.TWILIO,
                color = AppColors.Twilio,
                onClick = { selectedTab = SettingsTab.TWILIO },
                modifier = Modifier.weight(1f)
            )
        }

        // Tab Content
        when (selectedTab) {
            SettingsTab.TELNYX -> TelnyxTab(
                settings = telnyxSettings,
                onSettingsChange = { telnyxSettings = it },
                pushToken = pushToken,
                tokenCopied = tokenCopied,
                onCopyToken = { tokenCopied = true },
                onSave = {
                    SettingsStorage.saveTelnyxSettings(context, telnyxSettings)
                    onSaveAndReconnect(true)
                }
            )
            SettingsTab.TWILIO -> TwilioTab(
                settings = twilioSettings,
                onSettingsChange = { twilioSettings = it },
                pushToken = pushToken,
                tokenCopied = tokenCopied,
                onCopyToken = { tokenCopied = true },
                onSave = {
                    SettingsStorage.saveTwilioSettings(context, twilioSettings)
                    onSaveAndReconnect(false)
                }
            )
        }
    }
}

@Composable
private fun TabButton(
    text: String,
    isSelected: Boolean,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TextButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text,
                style = if (isSelected) AppTypography.LabelLarge else AppTypography.BodyMedium,
                color = if (isSelected) color else AppColors.TextSecondary,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
        if (isSelected) {
            Divider(
                color = color,
                thickness = 2.dp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun TelnyxTab(
    settings: TelnyxSettings,
    onSettingsChange: (TelnyxSettings) -> Unit,
    pushToken: String,
    tokenCopied: Boolean,
    onCopyToken: () -> Unit,
    onSave: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(AppSpacing.md)
    ) {
        // Credentials Section
        SectionHeader("Credentials")
        SettingsTextField(
            label = "SIP Username",
            value = settings.sipUsername,
            onValueChange = { onSettingsChange(settings.copy(sipUsername = it)) }
        )
        Spacer(modifier = Modifier.height(AppSpacing.sm))
        SettingsTextField(
            label = "SIP Password",
            value = settings.sipPassword,
            onValueChange = { onSettingsChange(settings.copy(sipPassword = it)) },
            isPassword = true
        )

        Spacer(modifier = Modifier.height(AppSpacing.lg))

        // Configuration Section
        SectionHeader("Configuration")
        SettingsToggle(
            title = "Reconnect Client",
            checked = settings.reconnectClient,
            onCheckedChange = { onSettingsChange(settings.copy(reconnectClient = it)) }
        )
        SettingsToggle(
            title = "Force Relay Candidate",
            checked = settings.forceRelayCandidate,
            onCheckedChange = { onSettingsChange(settings.copy(forceRelayCandidate = it)) }
        )
        SettingsToggle(
            title = "Enable Quality Metrics",
            checked = settings.enableQualityMetrics,
            onCheckedChange = { onSettingsChange(settings.copy(enableQualityMetrics = it)) }
        )
        SettingsToggle(
            title = "Debug Mode",
            checked = settings.debug,
            onCheckedChange = { onSettingsChange(settings.copy(debug = it)) }
        )

        Spacer(modifier = Modifier.height(AppSpacing.lg))

        // Push Token Section
        PushTokenSection(
            pushToken = pushToken,
            tokenCopied = tokenCopied,
            onCopyToken = {
                clipboardManager.setText(AnnotatedString(pushToken))
                onCopyToken()
            }
        )

        Spacer(modifier = Modifier.height(AppSpacing.xl))

        // Save Button
        Button(
            onClick = onSave,
            enabled = settings.hasValidCredentials,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = AppColors.Telnyx,
                disabledContainerColor = AppColors.Inactive
            )
        ) {
            Text("Save and Reconnect", style = AppTypography.LabelMedium)
        }
    }
}

@Composable
private fun TwilioTab(
    settings: TwilioSettings,
    onSettingsChange: (TwilioSettings) -> Unit,
    pushToken: String,
    tokenCopied: Boolean,
    onCopyToken: () -> Unit,
    onSave: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(AppSpacing.md)
    ) {
        // Credentials Section
        SectionHeader("Credentials")
        SettingsTextField(
            label = "Twilio Identity",
            value = settings.identity,
            onValueChange = { onSettingsChange(settings.copy(identity = it)) }
        )

        Spacer(modifier = Modifier.height(AppSpacing.lg))

        // Backend Configuration Section
        SectionHeader("Backend Configuration")
        SettingsTextField(
            label = "Backend URL",
            value = settings.backendURL,
            onValueChange = { onSettingsChange(settings.copy(backendURL = it)) },
            placeholder = "https://your-server.ngrok-free.dev"
        )

        Spacer(modifier = Modifier.height(AppSpacing.lg))

        // Push Token Section
        PushTokenSection(
            pushToken = pushToken,
            tokenCopied = tokenCopied,
            onCopyToken = {
                clipboardManager.setText(AnnotatedString(pushToken))
                onCopyToken()
            }
        )

        Spacer(modifier = Modifier.height(AppSpacing.xl))

        // Save Button
        Button(
            onClick = onSave,
            enabled = settings.hasValidCredentials,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = AppColors.Twilio,
                disabledContainerColor = AppColors.Inactive
            )
        ) {
            Text("Save and Reconnect", style = AppTypography.LabelMedium)
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = AppTypography.LabelSmall,
        color = AppColors.TextSecondary,
        modifier = Modifier.padding(bottom = AppSpacing.sm)
    )
}

@Composable
private fun SettingsTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    isPassword: Boolean = false
) {
    Column {
        Text(
            text = label,
            style = AppTypography.InputLabel,
            color = AppColors.TextSecondary
        )
        Spacer(modifier = Modifier.height(AppSpacing.Input.labelSpacing))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = if (placeholder.isNotEmpty()) {
                { Text(placeholder, color = AppColors.TextTertiary) }
            } else null,
            visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AppColors.InputBorderFocused,
                unfocusedBorderColor = AppColors.InputBorder,
                focusedContainerColor = AppColors.InputBackground,
                unfocusedContainerColor = AppColors.InputBackground,
                focusedTextColor = AppColors.TextPrimary,
                unfocusedTextColor = AppColors.TextPrimary
            ),
            textStyle = AppTypography.InputText
        )
    }
}

@Composable
private fun SettingsToggle(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppSpacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = AppTypography.BodyMedium,
            color = AppColors.TextPrimary
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = AppColors.Telnyx,
                checkedTrackColor = AppColors.Telnyx.copy(alpha = 0.5f)
            )
        )
    }
}

@Composable
private fun PushTokenSection(
    pushToken: String,
    tokenCopied: Boolean,
    onCopyToken: () -> Unit
) {
    SectionHeader("Push Token")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.BackgroundSecondary, shape = MaterialTheme.shapes.medium)
            .padding(AppSpacing.Input.padding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (pushToken.length > 40) {
                "${pushToken.take(20)}...${pushToken.takeLast(20)}"
            } else {
                pushToken
            },
            style = AppTypography.Caption,
            color = AppColors.TextSecondary,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onCopyToken) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Copy",
                tint = if (tokenCopied) AppColors.Success else AppColors.TextSecondary
            )
        }
    }
}
