package com.resq.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.resq.app.BuildConfig
import com.resq.app.ui.theme.BeaconAmber
import com.resq.app.ui.theme.TextPrimary
import com.resq.app.ui.theme.TextSecondary

/** One label/value line in a [DebugPanel]. */
data class DebugRow(val label: String, val value: String)

/**
 * Builds debug rows only in debug builds. In a release build the lambda is never run, so the
 * values are not even computed. Use it where a screen state is built:
 * `debugRows = debugRows { listOf(DebugRow("Counter", n.toString())) }`.
 */
inline fun debugRows(build: () -> List<DebugRow>): List<DebugRow> =
    if (BuildConfig.DEBUG) build() else emptyList()

/**
 * A small testing panel for any screen. It draws nothing in a release build, even if rows are
 * passed in, and nothing when [rows] is empty. To add testing info to another screen, give its
 * UI state a `debugRows` list and put a `DebugPanel` where it should appear.
 */
@Composable
fun DebugPanel(title: String, rows: List<DebugRow>, modifier: Modifier = Modifier) {
    if (!BuildConfig.DEBUG || rows.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("DEBUG · $title", style = MaterialTheme.typography.labelSmall, color = BeaconAmber)
        rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(row.label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Text(row.value, style = MaterialTheme.typography.labelSmall, color = TextPrimary)
            }
        }
    }
}
