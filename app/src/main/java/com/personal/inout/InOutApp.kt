package com.personal.inout

import android.app.Application
import androidx.work.*
import com.personal.inout.data.AppDatabase
import com.personal.inout.worker.BackupWorker
import com.personal.inout.worker.DueReminderWorker
import java.util.concurrent.TimeUnit

class InOutApp : Application() {

    val database by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate() {
        super.onCreate()

        val reminderWork = PeriodicWorkRequestBuilder<DueReminderWorker>(12, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "InOutDueReminders",
            ExistingPeriodicWorkPolicy.KEEP,
            reminderWork
        )

        val backupWork = PeriodicWorkRequestBuilder<BackupWorker>(24, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "InOutDailyBackup",
            ExistingPeriodicWorkPolicy.KEEP,
            backupWork
        )
    }
}
