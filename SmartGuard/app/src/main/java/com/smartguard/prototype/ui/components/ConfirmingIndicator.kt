package com.smartguard.prototype.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Pulsing "Confirming identity…" indicator shown during the 2-detection confirmation window.
 *
 * Uses a subtle scale + alpha pulse rather than a spinner so it feels calm rather than urgent.
 * Confirmation dots show progress toward [requiredCount].
 */
@Composable
fun ConfirmingIndicator(
    message: String = "Confirming identity…",
    confirmationCount: Int = 0,
    requiredCount: Int = 2,
    modifier: Modifier = Modifier,
    tintColor: Color = MaterialTheme.colorScheme.primary
) {
    val infiniteTransition = rememberInfiniteTransition(label = "confirm_pulse")

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "alpha"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.93f, targetValue = 1.07f,
        animationSpec = infiniteRepeatable(tween(900, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "scale"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Pulsing ring
        Box(
            modifier = Modifier
                .size(72.dp)
                .scale(pulseScale)
                .background(tintColor.copy(alpha = 0.12f * pulseAlpha), RoundedCornerShape(36.dp)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(tintColor.copy(alpha = 0.25f * pulseAlpha), RoundedCornerShape(20.dp))
            )
        }

        Text(
            message,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = pulseAlpha),
            modifier = Modifier.alpha(pulseAlpha)
        )

        // Confirmation progress dots
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(requiredCount) { index ->
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            color = if (index < confirmationCount) tintColor else tintColor.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(5.dp)
                        )
                )
            }
        }

        Text(
            "$confirmationCount / $requiredCount",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
        )
    }
}
