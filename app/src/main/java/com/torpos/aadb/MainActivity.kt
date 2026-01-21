package com.torpos.aadb

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.torpos.aadb.ui.theme.MyApplicationTheme
import com.torpos.aadb.ui.AdbHostScreen
import com.torpos.aadb.service.AdbHostService
import com.torpos.aadb.state.AdbHostRepository
import com.torpos.aadb.state.AdbServiceDiscovery

class MainActivity : ComponentActivity() {
    companion object {
        private const val PERM_POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"
        private const val PERM_NEARBY_WIFI_DEVICES = "android.permission.NEARBY_WIFI_DEVICES"
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val notificationsGranted = result[PERM_POST_NOTIFICATIONS] ?: true
        val nearbyGranted = result[PERM_NEARBY_WIFI_DEVICES] ?: true

        if (notificationsGranted) {
            AdbHostService.start(this)
        } else {
            AdbHostRepository.reportError("Notification permission is required for pairing input.")
        }

        if (nearbyGranted) {
            AdbServiceDiscovery.start(this)
        } else {
            AdbHostRepository.reportError("Nearby devices permission is required to auto-detect ports.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                AdbHostScreen()
            }
        }

        AdbHostRepository.initialize(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val missing = mutableListOf<String>()
            if (ContextCompat.checkSelfPermission(this, PERM_POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                missing.add(PERM_POST_NOTIFICATIONS)
            }
            if (ContextCompat.checkSelfPermission(this, PERM_NEARBY_WIFI_DEVICES) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                missing.add(PERM_NEARBY_WIFI_DEVICES)
            }
            if (missing.isNotEmpty()) {
                permissionLauncher.launch(missing.toTypedArray())
            } else {
                AdbHostService.start(this)
                AdbServiceDiscovery.start(this)
            }
        } else {
            AdbHostService.start(this)
            AdbServiceDiscovery.start(this)
        }
    }
}
