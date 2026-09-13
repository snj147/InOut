package com.personal.inout.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.personal.inout.data.AppDatabase
import kotlinx.coroutines.flow.firstOrNull
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val db = AppDatabase.getDatabase(applicationContext)
            val transactions = db.vaultDao().getAllTransactions().firstOrNull() ?: emptyList()

            val backupDir = File(applicationContext.filesDir, "backups")
            if (!backupDir.exists()) {
                backupDir.mkdirs()
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val backupFile = File(backupDir, "ledger_backup_$timestamp.csv")

            val csvContent = StringBuilder().apply {
                append("ID,FlowType,Category,Amount,Timestamp,Note,PartyName,IsRecurring,Frequency\n")
                for (tx in transactions) {
                    val sanitizedNote = tx.note.replace(",", " ")
                    val sanitizedParty = tx.partyName.replace(",", " ")
                    append("${tx.id},${tx.flowType},${tx.category},${tx.amount},${tx.timestamp},$sanitizedNote,$sanitizedParty,${tx.isRecurring},${tx.frequency}\n")
                }
            }

            backupFile.writeText(csvContent.toString())
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
