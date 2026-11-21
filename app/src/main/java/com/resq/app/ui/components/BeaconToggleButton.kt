package com.resq.app.ui.components

import com.resq.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.resq.app.ui.theme.SignalRed
import com.resq.app.ui.theme.SignalRedDim
import com.resq.app.ui.theme.SurfaceCard
import com.resq.app.ui.theme.TextPrimary
import com.resq.app.ui.theme.TextSecondary

/**
 * The sender screen's single most important control. Deliberately huge
 * and deliberately a single tap target — in an emergency this is not a
 * moment for a settings-style switch buried in a row of text.
 *
 * The pulse animation only exists while [isActive] and only while this
 * composable is on screen (Compose tears down [rememberInfiniteTransition]
 * when the sender tab isn't visible), so it costs nothing while idle or
 * backgrounded — it is not a general "keep animating" background timer.
 */
@Composable
fun BeaconToggleButton(
    isActive: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "beacon-pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isActive) 1.08f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scale",
    )

    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        if (isActive) {
            Box(
                modifier = Modifier
                    .size(216.dp)
                    .scale(pulseScale)
                    .background(SignalRedDim.copy(alpha = 0.35f), CircleShape)
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(184.dp)
                .background(if (isActive) SignalRed else SurfaceCard, CircleShape)
                .border(
                    width = if (isActive) 0.dp else 2.dp,
                    color = if (isActive) Color.Transparent else TextSecondary.copy(alpha = 0.4f),
                    shape = CircleShape,
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggle,
                ),
        ) {
            Text(
                text = stringResource(if (isActive) R.string.button_stop else R.string.button_start),
                color = if (isActive) TextPrimary else TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
}
