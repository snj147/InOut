package com.personal.inout

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.personal.inout.ui.DashboardScreen

class MainActivity : ComponentActivity() {

    private val permissionsToRequest by lazy {
        val list = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        list.toTypedArray()
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Callback completed; app already running underneath
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as InOutApp
        
        // Immediate UI render: app will never stall or exit on launch
        setContent {
            DashboardScreen(db = app.database)
        }

        val prefs = getSharedPreferences("inout_app_prefs", Context.MODE_PRIVATE)
        val hasRequestedPerms = prefs.getBoolean("has_prompted_permissions", false)

        if (!hasRequestedPerms) {
            prefs.edit().putBoolean("has_prompted_permissions", true).apply()
            permissionLauncher.launch(permissionsToRequest)
        }
    }
}
