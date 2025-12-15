package com.telnyx.voice.demo.ui.theme

import androidx.compose.ui.unit.dp

object AppSpacing {
    // Base Units (8pt grid)
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp

    // Button Spacing
    object Button {
        val paddingHorizontal = 16.dp
        val paddingVertical = 12.dp
    }

    // Card Spacing
    object Card {
        val padding = 16.dp
        val cornerRadius = 12.dp
    }

    // Input Spacing
    object Input {
        val padding = 12.dp
        val cornerRadius = 10.dp
        val labelSpacing = 6.dp
    }

    // Screen Spacing
    object Screen {
        val horizontal = 20.dp
        val top = 16.dp
        val bottom = 24.dp
    }

    // Call Control Spacing
    object CallControl {
        val buttonSize = 60.dp
        val largeButtonSize = 70.dp
        val spacing = 40.dp
        val iconSize = 100.dp
    }
}
