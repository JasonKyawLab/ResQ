package com.resq.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.resq.app.ui.theme.TextPrimary

/** Side margin and top margin shared by the three main screens, so their titles start in the same place. */
val ScreenHorizontalPadding = 20.dp
val ScreenVerticalPadding = 16.dp

/**
 * The title row at the top of Send, Scan and Settings. One minimum height and one text style, so the three
 * titles line up when switching tabs even when one row has a button and another does not.
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            leading?.invoke()
            Text(title, style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
        }
        trailing?.invoke()
    }
}
