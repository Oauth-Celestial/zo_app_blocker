package com.example.zo_app_blocker

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_VERSION = 1
        private const val DATABASE_NAME = "zo_app_blocker.db"

        // Preferences Table
        const val TABLE_PREFERENCES = "preferences"
        const val COLUMN_PREF_KEY = "key"
        const val COLUMN_PREF_VALUE = "value"

        // Blocked Apps Table
        const val TABLE_BLOCKED_APPS = "blocked_apps"
        const val COLUMN_BLOCKED_PACKAGE = "package_name"

        // Block Activity Log Table
        const val TABLE_BLOCK_ACTIVITY = "block_activity"
        const val COLUMN_ACTIVITY_ID = "id"
        const val COLUMN_ACTIVITY_PACKAGE = "package_name"
        const val COLUMN_ACTIVITY_TIMESTAMP = "timestamp"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createPrefTable = ("CREATE TABLE " + TABLE_PREFERENCES + "("
                + COLUMN_PREF_KEY + " TEXT PRIMARY KEY,"
                + COLUMN_PREF_VALUE + " TEXT" + ")")
        db.execSQL(createPrefTable)

        val createBlockedAppsTable = ("CREATE TABLE " + TABLE_BLOCKED_APPS + "("
                + COLUMN_BLOCKED_PACKAGE + " TEXT PRIMARY KEY" + ")")
        db.execSQL(createBlockedAppsTable)

        val createBlockActivityTable = ("CREATE TABLE " + TABLE_BLOCK_ACTIVITY + "("
                + COLUMN_ACTIVITY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COLUMN_ACTIVITY_PACKAGE + " TEXT,"
                + COLUMN_ACTIVITY_TIMESTAMP + " INTEGER" + ")")
        db.execSQL(createBlockActivityTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_PREFERENCES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_BLOCKED_APPS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_BLOCK_ACTIVITY")
        onCreate(db)
    }

    // --- Preference Methods ---

    fun setPreference(key: String, value: String) {
        val db = this.writableDatabase
        val values = ContentValues()
        values.put(COLUMN_PREF_KEY, key)
        values.put(COLUMN_PREF_VALUE, value)
        db.replace(TABLE_PREFERENCES, null, values)
        db.close()
    }

    fun getPreference(key: String, defaultValue: String?): String? {
        val db = this.readableDatabase
        val cursor = db.query(
            TABLE_PREFERENCES, arrayOf(COLUMN_PREF_VALUE),
            "$COLUMN_PREF_KEY=?", arrayOf(key), null, null, null, null
        )
        var result: String? = defaultValue
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                result = cursor.getString(0)
            }
            cursor.close()
        }
        db.close()
        return result
    }

    fun deletePreference(key: String) {
        val db = this.writableDatabase
        db.delete(TABLE_PREFERENCES, "$COLUMN_PREF_KEY=?", arrayOf(key))
        db.close()
    }

    // --- Blocked Apps Methods ---

    fun saveBlockedApps(apps: Set<String>) {
        val db = this.writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE_BLOCKED_APPS, null, null)
            for (app in apps) {
                val values = ContentValues()
                values.put(COLUMN_BLOCKED_PACKAGE, app)
                db.insert(TABLE_BLOCKED_APPS, null, values)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        db.close()
    }

    fun getBlockedApps(): Set<String> {
        val apps = mutableSetOf<String>()
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT $COLUMN_BLOCKED_PACKAGE FROM $TABLE_BLOCKED_APPS", null)
        if (cursor.moveToFirst()) {
            do {
                apps.add(cursor.getString(0))
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return apps
    }

    // --- Block Activity Methods ---

    fun logBlockEvent(packageName: String) {
        val db = this.writableDatabase
        val values = ContentValues()
        values.put(COLUMN_ACTIVITY_PACKAGE, packageName)
        values.put(COLUMN_ACTIVITY_TIMESTAMP, System.currentTimeMillis())
        db.insert(TABLE_BLOCK_ACTIVITY, null, values)
        db.close()
    }

    fun getBlockActivityLog(): List<Map<String, Any>> {
        val log = mutableListOf<Map<String, Any>>()
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT $COLUMN_ACTIVITY_PACKAGE, $COLUMN_ACTIVITY_TIMESTAMP FROM $TABLE_BLOCK_ACTIVITY ORDER BY $COLUMN_ACTIVITY_TIMESTAMP DESC", null)
        if (cursor.moveToFirst()) {
            do {
                val map = mapOf<String, Any>(
                    "packageName" to cursor.getString(0),
                    "timestamp" to cursor.getLong(1)
                )
                log.add(map)
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return log
    }

    fun clearBlockActivityLog() {
        val db = this.writableDatabase
        db.delete(TABLE_BLOCK_ACTIVITY, null, null)
        db.close()
    }
}
