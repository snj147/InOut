package com.personal.inout.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.personal.inout.MainActivity
import com.personal.inout.R
import com.personal.inout.data.AppDatabase
import com.personal.inout.data.PocketType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class VaultWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val db = AppDatabase.getInstance(context)
        val prefs = context.getSharedPreferences("inout_app_prefs", Context.MODE_PRIVATE)
        val burnRate = prefs.getFloat("daily_burn_ceiling", 450f).toDouble()

        CoroutineScope(Dispatchers.IO).launch {
            val pockets = db.stateFlowDao().getPocketBalancesSync()
            val totalLiquid = pockets.filter { it.pocketType == PocketType.LIQUID }
                .sumOf { it.currentBalance }
                .coerceAtLeast(0.0)

            val unpaidCardDues = pockets.filter { it.pocketType == PocketType.CREDIT || it.pocketType == PocketType.CREDIT_LINE }
                .filter { it.computedBalance < 0.0 }
                .sumOf { Math.abs(it.computedBalance) }

            val safeLiquid = (totalLiquid - unpaidCardDues).coerceAtLeast(0.0)
            val runwayDays = if (burnRate > 0) (safeLiquid / burnRate).toInt() else 0

            for (widgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_horizon_glance)

                views.setTextViewText(R.id.widget_safe_liquid_value, "₹${String.format("%,.0f", safeLiquid)}")
                views.setTextViewText(R.id.widget_runway_days_value, "$runwayDays Days Safe")

                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    widgetId,
                    launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_root_container, pendingIntent)

                appWidgetManager.updateAppWidget(widgetId, views)
            }
        }
    }

    companion object {
        fun updateAllWidgets(context: Context) {
            val intent = Intent(context, VaultWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(
                    ComponentName(context, VaultWidgetProvider::class.java)
                )
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
        }
    }
}
