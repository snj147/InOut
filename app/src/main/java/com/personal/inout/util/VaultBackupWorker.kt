package com.personal.inout.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.personal.inout.data.AppDatabase
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VaultBackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val db = AppDatabase.getInstance(applicationContext)
            
            // Creates the InOut folder in your phone's public Documents directory
            val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val inOutDir = File(docsDir, "InOut")
            if (!inOutDir.exists()) inOutDir.mkdirs()

            // Generates the simplistic .inout branding requested: backupyymmddhhmmss.inout
            val timeStamp = SimpleDateFormat("yyMMddHHmmss", Locale.getDefault()).format(Date())
            val backupFile = File(inOutDir, "backup$timeStamp.inout")
            val uri = Uri.fromFile(backupFile)

            // Exports the cryptographic vault silently
            val exportResult = VaultBackupManager.exportEncryptedBackup(
                applicationContext, 
                db, 
                uri, 
                "INOUT_LEDGER_MASTER_KEY"
            )

            if (exportResult.isSuccess) {
                // Rolling 7-Day Cleanup: Enforces a strict 7-file limit for automated backups
                val backups = inOutDir.listFiles { file -> 
                    file.name.startsWith("backup") && file.name.endsWith(".inout") 
                }
                
                if (backups != null && backups.size > 7) {
                    backups.sortByDescending { it.lastModified() } // Sort newest to oldest
                    for (i in 7 until backups.size) {
                        backups[i].delete() // Purges the oldest files
                    }
                }
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Result.failure()
        }
    }
}
