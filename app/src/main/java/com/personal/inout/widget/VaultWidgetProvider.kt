package com.personal.inout.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.personal.inout.MainActivity
import com.personal.inout.R

class SpeedDialWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            // Tapping widget directly opens the fast natural-language entry overlay
            val intent = Intent(context, WidgetCommandActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_IMMUTABLE)

            val views = RemoteViews(context.packageName, R.layout.widget_speed_dial).apply {
                setOnClickPendingIntent(R.id.widget_root, pendingIntent)
                setTextViewText(R.id.tv_widget_runway, "+ Fast Entry")
            }
            appWidgetManager.updateAppWidget(id, views)
        }
    }
}

class HorizonGlanceWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            val intent = Intent(context, MainActivity::class.java)
            val pending = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_IMMUTABLE)
            val views = RemoteViews(context.packageName, R.layout.widget_horizon_glance).apply {
                setOnClickPendingIntent(R.id.widget_root, pending)
            }
            appWidgetManager.updateAppWidget(id, views)
        }
    }
}

class MicroLeakWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            val intent = Intent(context, MainActivity::class.java)
            val pending = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_IMMUTABLE)
            val views = RemoteViews(context.packageName, R.layout.widget_micro_leak).apply {
                setOnClickPendingIntent(R.id.widget_root, pending)
            }
            appWidgetManager.updateAppWidget(id, views)
        }
    }
}
