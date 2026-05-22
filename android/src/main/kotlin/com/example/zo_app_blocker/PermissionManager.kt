package com.example.zo_app_blocker

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

class PermissionManager(private val context: Context) {
    fun checkAccessibilityPermission(): String {
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

    fun requestAccessibilityPermission(activity: Activity) {
        if (checkAccessibilityPermission() != "granted") {
            activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    fun checkNotificationPermission(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val status = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            return if (status == PackageManager.PERMISSION_GRANTED) "granted" else "denied"
        }
        return "granted"
    }

    fun requestNotificationPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkNotificationPermission() != "granted") {
                activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }
    }
}
