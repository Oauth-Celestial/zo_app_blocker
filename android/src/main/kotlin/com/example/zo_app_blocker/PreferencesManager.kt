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

    fun setNotificationConfig(config: Map<String, String>) {
        config.forEach { (key, value) ->
            dbHelper.setPreference("config_$key", value)
        }
    }

    fun getNotificationConfig(): Map<String, String?> {
        return mapOf(
            "notificationBannerTitle" to dbHelper.getPreference("config_notificationBannerTitle", null),
            "notificationBannerDescription" to dbHelper.getPreference("config_notificationBannerDescription", null),
            "notificationIcon" to dbHelper.getPreference("config_notificationIcon", null)
        )
    }

    fun getBlockScreenConfig(): Map<String, String?> {
        return mapOf(
            "backgroundColor"       to dbHelper.getPreference("config_backgroundColor", null),
            "titleColor"            to dbHelper.getPreference("config_titleColor", null),
            "descriptionColor"      to dbHelper.getPreference("config_descriptionColor", null),
            "title"                 to dbHelper.getPreference("config_title", null),
            "description"           to dbHelper.getPreference("config_description", null),
            "notificationTitle"     to dbHelper.getPreference("config_notificationTitle", null),
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
