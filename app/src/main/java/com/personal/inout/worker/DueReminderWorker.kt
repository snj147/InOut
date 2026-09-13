package com.personal.inout.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.personal.inout.R
import com.personal.inout.data.AppDatabase
import kotlinx.coroutines.flow.firstOrNull
import java.util.Calendar

class DueReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val db = AppDatabase.getDatabase(applicationContext)
            val transactions = db.vaultDao().getAllTransactions().firstOrNull() ?: emptyList()

            val now = Calendar.getInstance()
            val todayStart = now.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0) }.timeInMillis
            val tomorrowStart = todayStart + (24 * 60 * 60 * 1000L)
            val dayAfterTomorrowStart = tomorrowStart + (24 * 60 * 60 * 1000L)

            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "inout_due_reminders"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(channelId, "InOut Due Reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Alerts for upcoming repayment and recurring dates"
                }
                nm.createNotificationChannel(channel)
            }

            transactions.filter { it.returnDate > 0L }.forEach { tx ->
                val isDueToday = tx.returnDate in todayStart until tomorrowStart
                val isDueTomorrow = tx.returnDate in tomorrowStart until dayAfterTomorrowStart

                if (isDueToday || isDueTomorrow) {
                    val alertTiming = if (isDueToday) "Due Today" else "Due Tomorrow"
                    val title = "InOut: $alertTiming - ${tx.flowType}"
                    val content = "${tx.partyName}: ₹${String.format("%.0f", tx.amount)} (${tx.note.ifBlank { "Repayment reminder" }})"

                    val notif = NotificationCompat.Builder(applicationContext, channelId)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle(title)
                        .setContentText(content)
                        .setColor(0xFFDEAC64.toInt()) // Matching Amber Ochre
                        .setAutoCancel(true)
                        .build()

                    nm.notify(tx.id.toInt(), notif)
                }
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
