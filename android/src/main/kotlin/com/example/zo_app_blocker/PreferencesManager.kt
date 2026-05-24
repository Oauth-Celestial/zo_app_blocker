package com.example.zo_app_blocker

import android.content.Context

class PreferencesManager(context: Context) {
    private val dbHelper = DatabaseHelper(context)

    fun saveBlockedApps(apps: Set<String>) {
        dbHelper.saveBlockedApps(apps)
    }

    fun getBlockedApps(): Set<String> {
        return dbHelper.getBlockedApps()
    }

    fun setBlockAll(blockAll: Boolean) {
        dbHelper.setPreference("block_all", blockAll.toString())
    }

    fun isBlockAll(): Boolean {
        val value = dbHelper.getPreference("block_all", "false")
        return value == "true"
    }

    fun setBlockScreenConfig(config: Map<String, String>) {
        config.forEach { (key, value) ->
            dbHelper.setPreference("config_$key", value)
        }
    }

    fun getBlockScreenConfig(): Map<String, String?> {
        return mapOf(
            "backgroundColor" to (dbHelper.getPreference("config_backgroundColor", null) ?: "#FF0000"),
            "title" to (dbHelper.getPreference("config_title", null) ?: "App Blocked"),
            "titleColor" to (dbHelper.getPreference("config_titleColor", null) ?: "#FFFFFF"),
            "description" to (dbHelper.getPreference("config_description", null) ?: "This app is blocked."),
            "descriptionColor" to (dbHelper.getPreference("config_descriptionColor", null) ?: "#EEEEEE"),
            "notificationTitle" to dbHelper.getPreference("config_notificationTitle", null),
            "notificationDescription" to dbHelper.getPreference("config_notificationDescription", null)
        )
    }

    fun saveBlockScreenCallbackHandle(rawHandle: Long) {
        dbHelper.setPreference("block_screen_callback_handle", rawHandle.toString())
    }

    fun getBlockScreenCallbackHandle(): Long {
        val value = dbHelper.getPreference("block_screen_callback_handle", "-1")
        return value?.toLongOrNull() ?: -1L
    }

    fun hasBlockScreenCallback(): Boolean {
        return getBlockScreenCallbackHandle() != -1L
    }

    fun logBlockEvent(packageName: String) {
        dbHelper.logBlockEvent(packageName)
    }

    fun getBlockActivityLog(): List<Map<String, Any>> {
        return dbHelper.getBlockActivityLog()
    }

    fun clearBlockActivityLog() {
        dbHelper.clearBlockActivityLog()
    }
}
