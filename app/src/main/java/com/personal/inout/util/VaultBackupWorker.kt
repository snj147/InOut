package com.personal.inout.util

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker.Result
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
            
            // Moves backups from public Documents to App-Specific Internal Storage for strict privacy
            val inOutDir = File(applicationContext.filesDir, "InOut_Backups")
            if (!inOutDir.exists()) inOutDir.mkdirs()

            val timeStamp = SimpleDateFormat("yyMMddHHmmss", Locale.getDefault()).format(Date())
            val backupFile = File(inOutDir, "backup$timeStamp.inout")
            val uri = Uri.fromFile(backupFile)

            // Retrieves the user's secure passphrase from SharedPreferences (or Android Keystore proxy)
            val prefs = applicationContext.getSharedPreferences("inout_app_prefs", Context.MODE_PRIVATE)
            val passphrase = prefs.getString("vault_backup_passphrase", null)
            
            if (passphrase.isNullOrBlank()) {
                return Result.failure() // Cannot backup without the secure key
            }

            val exportResult = VaultBackupManager.exportEncryptedBackup(
                applicationContext, 
                db, 
                uri, 
                passphrase
            )

            if (exportResult.isSuccess) {
                val backups = inOutDir.listFiles { file -> 
                    file.name.startsWith("backup") && file.name.endsWith(".inout") 
                }
                
                if (backups != null && backups.size > 7) {
                    backups.sortByDescending { it.lastModified() }
                    for (i in 7 until backups.size) {
                        backups[i].delete() 
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
