package com.personal.inout.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

object AppIconManager {
    private const val DEFAULT_ALIAS = "com.personal.inout.MainActivity"
    private const val PRO_GOLD_ALIAS = "com.personal.inout.MainActivityProGold"

    fun setProIconEnabled(context: Context, enablePro: Boolean) {
        val pm = context.packageManager
        val pkg = context.packageName

        val defaultComp = ComponentName(pkg, DEFAULT_ALIAS)
        val proComp = ComponentName(pkg, PRO_GOLD_ALIAS)

        val enableTarget = if (enablePro) proComp else defaultComp
        val disableTarget = if (enablePro) defaultComp else proComp

        pm.setComponentEnabledSetting(
            enableTarget,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
        pm.setComponentEnabledSetting(
            disableTarget,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }
}
