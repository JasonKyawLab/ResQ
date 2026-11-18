package com.resq.app

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.resq.app.core.di.ServiceLocator
import com.resq.app.core.permissions.PermissionManager
import com.resq.app.ui.navigation.ResQNavHost
import com.resq.app.ui.theme.ResQTheme

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { bumpPermissionRefreshTick() }

    private val bluetoothEnableLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { bumpPermissionRefreshTick() }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Draw edge-to-edge on every Android version, not just 15+ where it is
        // forced. Without this, older phones resize the whole window for the
        // keyboard AND Compose's imePadding() adds the keyboard height again,
        // leaving a keyboard-sized gap and a squashed message box.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        setContent {
            ResQTheme {
                ResQNavHost(
                    onRequestPermissions = ::requestAllPermissions,
                    onRequestBluetoothEnable = ::requestBluetoothEnable,
                    onOpenAppSettings = ::openAppSettings,
                    onOpenLocationSettings = ::openLocationSettings,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Catches returning from the system Settings app itself (e.g. the
        // "Manage everything from system settings" link), which — unlike
        // the launchers above — has no result callback of its own.
        bumpPermissionRefreshTick()
    }

    private fun requestAllPermissions() {
        permissionLauncher.launch(PermissionManager.allRequired())
    }

    private fun requestBluetoothEnable() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !PermissionManager.hasBluetoothPermissions(this)
        ) {
            requestAllPermissions()
            return
        }
        bluetoothEnableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
    }

    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        )
    }

    private fun openLocationSettings() {
        startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
    }

    private fun bumpPermissionRefreshTick() {
        val locator = ServiceLocator.getInstance(this)
        locator.permissionRefreshTick.value++
    }
}
