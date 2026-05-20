package com.example.zo_app_blocker

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils

class PermissionManager(private val context: Context) {
    fun checkPermission(): String {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return "denied"

        val component = ComponentName(context, AppBlockerAccessibilityService::class.java)
        val flat = component.flattenToString()

        val enabled = TextUtils.SimpleStringSplitter(':').apply { setString(enabledServices) }
            .any { it.equals(flat, ignoreCase = true) }

        return if (enabled) "granted" else "denied"
    }

    fun requestPermission(activity: Activity) {
        if (checkPermission() != "granted") {
            activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }
}
