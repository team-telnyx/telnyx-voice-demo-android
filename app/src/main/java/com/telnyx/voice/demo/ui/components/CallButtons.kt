package com.telnyx.voice.demo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.telnyx.voice.demo.ui.theme.AppColors
import com.telnyx.voice.demo.ui.theme.AppSpacing
import com.telnyx.voice.demo.ui.theme.AppTypography

@Composable
fun CallControlButton(
    icon: Painter,
    label: String,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = AppSpacing.CallControl.buttonSize
) {
    val backgroundColor = if (isActive) activeColor else AppColors.Inactive
    val iconColor = Color.White

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(backgroundColor)
        ) {
            Icon(
                painter = icon,
                contentDescription = label,
                tint = iconColor,
                modifier = Modifier.size(size * 0.4f)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = AppTypography.ControlLabel,
            color = AppColors.TextPrimary
        )
    }
}

@Composable
fun CallActionButton(
    icon: Painter,
    label: String,
    backgroundColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = AppSpacing.CallControl.largeButtonSize
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(backgroundColor)
        ) {
            Icon(
                painter = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(size * 0.4f)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = AppTypography.LabelMedium,
            color = AppColors.TextPrimary
        )
    }
}

@Composable
fun CircularCallButton(
    icon: Painter,
    backgroundColor: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
    size: Dp = AppSpacing.CallControl.largeButtonSize
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (enabled) backgroundColor else AppColors.Inactive)
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size * 0.4f)
        )
    }
}
