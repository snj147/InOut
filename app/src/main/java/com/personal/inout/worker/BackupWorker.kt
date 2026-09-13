package com.personal.inout.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.personal.inout.data.AppDatabase
import kotlinx.coroutines.flow.firstOrNull
import java.io.File

class BackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val db = AppDatabase.getDatabase(applicationContext)
            val accounts = db.vaultDao().getAllAccounts().firstOrNull() ?: emptyList()
            val transactions = db.vaultDao().getRecentTransactions().firstOrNull() ?: emptyList()

            val backupDir = File(applicationContext.filesDir, "backups")
            if (!backupDir.exists()) backupDir.mkdirs()

            val backupFile = File(backupDir, "inout_vault_export_${System.currentTimeMillis()}.csv")
            backupFile.bufferedWriter().use { writer ->
                writer.write("--- ACCOUNTS ---\n")
                writer.write("ID,Name,Balance,Type\n")
                accounts.forEach { writer.write("${it.id},${it.name},${it.balance},${it.type}\n") }

                writer.write("\n--- TRANSACTIONS ---\n")
                writer.write("ID,AccountID,Type,Category,Amount,Timestamp,Note\n")
                transactions.forEach { writer.write("${it.id},${it.accountId},${it.type},${it.category},${it.amount},${it.timestamp},${it.note}\n") }
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
