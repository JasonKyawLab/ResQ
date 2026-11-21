package com.resq.app.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.resq.app.R
import com.resq.app.ui.receiver.ReceiverRoute
import com.resq.app.ui.sender.SenderRoute
import com.resq.app.ui.settings.SettingsRoute
import com.resq.app.ui.theme.OledBlack
import com.resq.app.ui.theme.SignalRed
import com.resq.app.ui.theme.TextSecondary

/**
 * The three tabs. Exactly one screen is on the screen at a time and a tab switch is immediate: a
 * navigation host cross-fades the old and new screens, which left the Send screen's message box showing
 * through on top of Scan for a moment. Each tab still keeps its scroll position and typed text through
 * the saveable state holder, and Back from Scan or Settings returns to Send.
 */
@Composable
fun ResQNavHost(
    onRequestPermissions: () -> Unit,
    onRequestBluetoothEnable: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenBatterySettings: () -> Unit,
) {
    var current by rememberSaveable { mutableStateOf(ResQDestination.SENDER) }
    val stateHolder = rememberSaveableStateHolder()
    BackHandler(enabled = current != ResQDestination.SENDER) { current = ResQDestination.SENDER }

    Scaffold(
        containerColor = OledBlack,
        bottomBar = {
            NavigationBar(containerColor = OledBlack) {
                NavItem(
                    selected = current == ResQDestination.SENDER,
                    label = stringResource(R.string.nav_sender),
                    icon = Icons.Filled.Warning,
                    onClick = { current = ResQDestination.SENDER },
                )
                NavItem(
                    selected = current == ResQDestination.RECEIVER,
                    label = stringResource(R.string.nav_receiver),
                    icon = Icons.AutoMirrored.Filled.List,
                    onClick = { current = ResQDestination.RECEIVER },
                )
                NavItem(
                    selected = current == ResQDestination.SETTINGS,
                    label = stringResource(R.string.nav_settings),
                    icon = Icons.Filled.Settings,
                    onClick = { current = ResQDestination.SETTINGS },
                )
            }
        },
    ) { innerPadding ->
        // consumeWindowInsets so a screen's own imePadding() only adds the
        // keyboard height that isn't already covered by this padding.
        val contentModifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)
        stateHolder.SaveableStateProvider(current.route) {
            when (current) {
                ResQDestination.SENDER -> SenderRoute(
                    onRequestPermissions = onRequestPermissions,
                    onRequestBluetoothEnable = onRequestBluetoothEnable,
                    modifier = contentModifier,
                )
                ResQDestination.RECEIVER -> ReceiverRoute(
                    onRequestPermissions = onRequestPermissions,
                    onRequestBluetoothEnable = onRequestBluetoothEnable,
                    modifier = contentModifier,
                )
                ResQDestination.SETTINGS -> SettingsRoute(
                    onRequestBluetoothEnable = onRequestBluetoothEnable,
                    onRequestRuntimePermissions = onRequestPermissions,
                    onOpenAppSettings = onOpenAppSettings,
                    onOpenLocationSettings = onOpenLocationSettings,
                    onOpenGuide = onOpenGuide,
                    onOpenBatterySettings = onOpenBatterySettings,
                    modifier = contentModifier,
                )
            }
        }
    }
}

@Composable
private fun RowScope.NavItem(
    selected: Boolean,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = SignalRed,
            selectedTextColor = SignalRed,
            unselectedIconColor = TextSecondary,
            unselectedTextColor = TextSecondary,
            indicatorColor = OledBlack,
        ),
    )
}
