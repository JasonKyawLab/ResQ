package com.resq.app.ui.navigation

import com.resq.app.R
import androidx.compose.ui.res.stringResource
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
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.composable
import com.resq.app.ui.receiver.ReceiverRoute
import com.resq.app.ui.sender.SenderRoute
import com.resq.app.ui.settings.SettingsRoute
import com.resq.app.ui.theme.OledBlack
import com.resq.app.ui.theme.SignalRed
import com.resq.app.ui.theme.TextSecondary

@Composable
fun ResQNavHost(
    onRequestPermissions: () -> Unit,
    onRequestBluetoothEnable: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onOpenGuide: () -> Unit,
) {
    val navController = rememberNavController()

    Scaffold(
        containerColor = OledBlack,
        bottomBar = {
            NavigationBar(containerColor = OledBlack) {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = backStackEntry?.destination

                NavItem(
                    destination = ResQDestination.SENDER,
                    label = stringResource(R.string.nav_sender),
                    icon = Icons.Filled.Warning,
                    currentDestination = currentDestination,
                    navController = navController,
                )
                NavItem(
                    destination = ResQDestination.RECEIVER,
                    label = stringResource(R.string.nav_receiver),
                    icon = Icons.AutoMirrored.Filled.List,
                    currentDestination = currentDestination,
                    navController = navController,
                )
                NavItem(
                    destination = ResQDestination.SETTINGS,
                    label = stringResource(R.string.nav_settings),
                    icon = Icons.Filled.Settings,
                    currentDestination = currentDestination,
                    navController = navController,
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ResQDestination.SENDER.route,
            // consumeWindowInsets so a screen's own imePadding() only adds the
            // keyboard height that isn't already covered by this padding.
            modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
        ) {
            composable(ResQDestination.SENDER.route) {
                SenderRoute(
                    onRequestPermissions = onRequestPermissions,
                    onRequestBluetoothEnable = onRequestBluetoothEnable,
                )
            }
            composable(ResQDestination.RECEIVER.route) {
                ReceiverRoute(
                    onRequestPermissions = onRequestPermissions,
                    onRequestBluetoothEnable = onRequestBluetoothEnable,
                )
            }
            composable(ResQDestination.SETTINGS.route) {
                SettingsRoute(
                    onRequestBluetoothEnable = onRequestBluetoothEnable,
                    onRequestRuntimePermissions = onRequestPermissions,
                    onOpenAppSettings = onOpenAppSettings,
                    onOpenLocationSettings = onOpenLocationSettings,
                    onOpenGuide = onOpenGuide,
                )
            }
        }
    }
}

@Composable
private fun RowScope.NavItem(
    destination: ResQDestination,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    currentDestination: androidx.navigation.NavDestination?,
    navController: androidx.navigation.NavHostController,
) {
    val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
    NavigationBarItem(
        selected = selected,
        onClick = {
            navController.navigate(destination.route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        },
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
