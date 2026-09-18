package com.personal.inout

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.personal.inout.data.AppDatabase
import com.personal.inout.ui.DashboardScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enforce edge-to-edge with crisp light status bar icons for dark backgrounds
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false
        insetsController.isAppearanceLightNavigationBars = false

        val database = AppDatabase.getInstance(applicationContext)
        setContent {
            DashboardScreen(db = database)
        }
    }
}
