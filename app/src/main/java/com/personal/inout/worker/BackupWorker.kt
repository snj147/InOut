package com.personal.inout.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val dbFile = context.getDatabasePath("inout_encrypted.db")
            if (!dbFile.exists()) return Result.success()

            // Export to public accessible Documents storage
            val backupFolder = File(context.getExternalFilesDir(null), "Backups").apply { mkdirs() }
            val dateStamp = SimpleDateFormat("yyyy_MM_dd", Locale.US).format(Date())
            val destination = File(backupFolder, "inout_backup_$dateStamp.enc")

            FileInputStream(dbFile).use { input ->
                FileOutputStream(destination).use { output ->
                    input.copyTo(output)
                }
            }

            pruneOldBackups(backupFolder)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun pruneOldBackups(folder: File) {
        val files = folder.listFiles() ?: return
        // Keep files strictly within the last 30 daily snapshots
        if (files.size > 30) {
            files.sortBy { it.lastModified() }
            val toDeleteCount = files.size - 30
            for (i in 0 until toDeleteCount) {
                files[i].delete()
            }
        }
    }
}
