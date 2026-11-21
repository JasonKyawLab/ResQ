package com.resq.app.ui.components

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.resq.app.core.data.model.SignalStrength
import com.resq.app.ui.theme.BeaconAmber
import com.resq.app.ui.theme.BeaconGreen
import com.resq.app.ui.theme.SignalRed

@Composable
fun SignalBadge(strength: SignalStrength, modifier: Modifier = Modifier) {
    val color = when (strength) {
        SignalStrength.STRONG -> BeaconGreen
        SignalStrength.MEDIUM -> BeaconAmber
        SignalStrength.WEAK -> SignalRed
    }
    Text(
        text = stringResource(strength.labelRes),
        color = color,
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
