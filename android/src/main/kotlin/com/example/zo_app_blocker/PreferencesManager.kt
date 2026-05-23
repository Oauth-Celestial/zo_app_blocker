package com.example.zo_app_blocker

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("zo_app_blocker_prefs", Context.MODE_PRIVATE)

    fun saveBlockedApps(apps: Set<String>) {
        prefs.edit().putStringSet("blocked_apps", apps).apply()
    }

    fun getBlockedApps(): Set<String> {
        return prefs.getStringSet("blocked_apps", emptySet()) ?: emptySet()
    }

    fun setBlockAll(blockAll: Boolean) {
        prefs.edit().putBoolean("block_all", blockAll).apply()
    }

    fun isBlockAll(): Boolean {
        return prefs.getBoolean("block_all", false)
    }

    fun setBlockScreenConfig(config: Map<String, String>) {
        val editor = prefs.edit()
        config.forEach { (key, value) ->
            editor.putString("config_$key", value)
        }
        editor.apply()
    }

    fun getBlockScreenConfig(): Map<String, String?> {
        return mapOf(
            "backgroundColor" to (prefs.getString("config_backgroundColor", null) ?: "#FF0000"),
            "title" to (prefs.getString("config_title", null) ?: "App Blocked"),
            "titleColor" to (prefs.getString("config_titleColor", null) ?: "#FFFFFF"),
            "description" to (prefs.getString("config_description", null) ?: "This app is blocked."),
            "descriptionColor" to (prefs.getString("config_descriptionColor", null) ?: "#EEEEEE"),
            "notificationTitle" to prefs.getString("config_notificationTitle", null),
            "notificationDescription" to prefs.getString("config_notificationDescription", null)
        )
    }

    fun saveBlockScreenCallbackHandle(rawHandle: Long) {
        prefs.edit().putLong("block_screen_callback_handle", rawHandle).apply()
    }

    fun getBlockScreenCallbackHandle(): Long {
        return prefs.getLong("block_screen_callback_handle", -1L)
    }

    fun hasBlockScreenCallback(): Boolean {
        return getBlockScreenCallbackHandle() != -1L
    }
}
