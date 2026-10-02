package com.personal.inout

import android.app.Application
import com.personal.inout.data.AppDatabase

class InOutApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Initialize Room Database cleanly on startup with zero auto-seeded cash accounts
        AppDatabase.getInstance(this)
    }
}
