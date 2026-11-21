package com.resq.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.resq.app.core.data.settings.ResQSettings
import com.resq.app.core.data.settings.SettingsRepository
import com.resq.app.ui.guide.GuideScreen
import com.resq.app.ui.navigation.ResQNavHost
import com.resq.app.ui.theme.OledBlack
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Decides what the app shows: the guide once on first launch, then the three screens. The guide can be
 * opened again from Settings and is drawn over the screens, which stay in place underneath.
 */
@Composable
fun ResQRoot(
    settings: SettingsRepository,
    onRequestPermissions: () -> Unit,
    onRequestBluetoothEnable: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenLocationSettings: () -> Unit,
) {
    // Null until the saved flag has been read, so the guide does not flash up for a returning user.
    val introSeen by remember(settings) { settings.settings.map<ResQSettings, Boolean?> { it.introSeen } }
        .collectAsState(initial = null)
    var showGuide by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    when (introSeen) {
        null -> Box(Modifier.fillMaxSize().background(OledBlack)) // still reading the saved flag
        false -> GuideScreen(firstLaunch = true, onDone = { scope.launch { settings.markIntroSeen() } })
        true -> Box(Modifier.fillMaxSize()) {
            ResQNavHost(
                onRequestPermissions = onRequestPermissions,
                onRequestBluetoothEnable = onRequestBluetoothEnable,
                onOpenAppSettings = onOpenAppSettings,
                onOpenLocationSettings = onOpenLocationSettings,
                onOpenGuide = { showGuide = true },
            )
            if (showGuide) {
                BackHandler { showGuide = false }
                GuideScreen(firstLaunch = false, onDone = { showGuide = false })
            }
        }
    }
}
