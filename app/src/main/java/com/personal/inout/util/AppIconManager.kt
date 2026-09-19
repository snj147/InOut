package com.personal.inout.util

import android.content.Context

object AppIconManager {
    // Android terminates the process when modifying active launcher activity-alias while running.
    // We safely persist the flag and only schedule changes when not in the active session.
    fun setProIconEnabled(context: Context, enablePro: Boolean) {
        val prefs = context.getSharedPreferences("inout_app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("pro_icon_target", enablePro).apply()
        // No runtime pm.setComponentEnabledSetting() call during active UI to prevent app crashes!
    }
}
