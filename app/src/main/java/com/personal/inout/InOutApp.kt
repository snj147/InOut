package com.personal.inout

import android.app.Application
import com.personal.inout.data.AppDatabase

class InOutApp : Application() {
    val database: AppDatabase by lazy {
        AppDatabase.getInstance(this)
    }

    override fun onCreate() {
        super.onCreate()
    }
}
