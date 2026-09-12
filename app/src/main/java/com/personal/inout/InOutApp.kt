package com.personal.inout

import android.app.Application
import androidx.work.*
import com.personal.inout.data.AppDatabase
import com.personal.inout.worker.BackupWorker
import java.util.concurrent.TimeUnit

class InOutApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate() {
        super.onCreate()
        scheduleDailyBackup()
    }

    private fun scheduleDailyBackup() {
        val constraints = Constraints.Builder()
            .setRequiresCharging(true)
            .build()

        val backupRequest = PeriodicWorkRequestBuilder<BackupWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "DailyBackupWork",
            ExistingPeriodicWorkPolicy.KEEP,
            backupRequest
        )
    }
}
