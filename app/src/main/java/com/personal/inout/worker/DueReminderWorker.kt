package com.personal.inout.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.widget.RemoteViews
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
            val accounts = db.vaultDao().getAllAccounts().firstOrNull() ?: emptyList()

            val now = Calendar.getInstance()
            val todayStart = now.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.timeInMillis
            val tomorrowStart = todayStart + (24 * 60 * 60 * 1000L)
            val dayAfterTomorrowStart = tomorrowStart + (24 * 60 * 60 * 1000L)

            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "inout_due_alerts"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(channelId, "InOut Due Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Custom theme reminders for upcoming repayments and bills"
                }
                nm.createNotificationChannel(channel)
            }

            val dueAccounts = accounts.filter { it.dueDate in todayStart until dayAfterTomorrowStart && it.balance > 0.0 }

            dueAccounts.forEach { acc ->
                val isDueToday = acc.dueDate in todayStart until tomorrowStart
                val timingText = if (isDueToday) "Due Today" else "Due Tomorrow"
                val isLender = acc.type == "LENDER" || acc.type == "CREDIT"
                val headline = "${if (isLender) "Pay to" else "Collect from"} ${acc.name}"
                val dueAmt = if (acc.type == "CREDIT") (acc.totalLimit - acc.balance).coerceAtLeast(0.0) else acc.balance

                val customView = RemoteViews(applicationContext.packageName, R.layout.notification_custom_due).apply {
                    setTextViewText(R.id.notif_badge, timingText.uppercase())
                    setTextViewText(R.id.notif_partyHeadline, headline)
                    setTextViewText(R.id.notif_amountSubtext, "₹ ${String.format("%,.0f", dueAmt)} • $timingText")
                }

                val notification = NotificationCompat.Builder(applicationContext, channelId)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                    .setCustomContentView(customView)
                    .setColor(0xFFDEAC64.toInt())
                    .setAutoCancel(true)
                    .build()

                nm.notify(acc.id.toInt(), notification)
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
