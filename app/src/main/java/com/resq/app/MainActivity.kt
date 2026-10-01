package com.resq.app

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.resq.app.core.permissions.PermissionManager
import com.resq.app.ui.navigation.ResQNavHost
import com.resq.app.ui.theme.ResQTheme

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* SenderViewModel/ReceiverViewModel re-read permission state on next recomposition. */ }

    private val bluetoothEnableLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* SettingsViewModel re-reads adapter state on next recomposition. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        setContent {
            ResQTheme {
                ResQNavHost(
                    onRequestPermissions = ::requestAllPermissions,
                    onRequestBluetoothEnable = ::requestBluetoothEnable,
                    onOpenAppSettings = ::openAppSettings,
                )
            }
        }
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
}
