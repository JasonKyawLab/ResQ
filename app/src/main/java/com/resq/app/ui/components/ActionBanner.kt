package com.resq.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.resq.app.ui.theme.SignalRed
import com.resq.app.ui.theme.SignalRedDim
import com.resq.app.ui.theme.TextPrimary

/**
 * A warning that fixes itself, not one that just describes the fix.
 * Every place this app tells someone "Bluetooth is off" or "permission
 * missing" should hand them the control to fix it right there — in an
 * emergency flow, a banner that only describes the problem and expects
 * the person to go find Settings themselves is a wasted tap.
 */
@Composable
fun ActionBanner(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SignalRedDim.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = SignalRed, contentColor = TextPrimary),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(actionLabel)
            }
        }
    }
}
