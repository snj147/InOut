package com.personal.inout

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.personal.inout.data.AppDatabase
import com.personal.inout.ui.DashboardScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val database = AppDatabase.getInstance(applicationContext)
        setContent {
            DashboardScreen(db = database)
        }
    }
}
