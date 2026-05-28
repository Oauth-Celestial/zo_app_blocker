package com.example.zo_app_blocker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder

class AppBlockerForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "zo_app_blocker_channel"
        private const val NOTIFICATION_ID = 101

        fun start(context: Context) {
            val intent = Intent(context, AppBlockerForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, AppBlockerForegroundService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) { // Build.VERSION_CODES.UPSIDE_DOWN_CAKE
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        val pm = packageManager
        val appName = try {
            applicationInfo.loadLabel(pm).toString()
        } catch (e: Exception) {
            "App"
        }

        val prefsManager = PreferencesManager(this)
        val config = prefsManager.getNotificationConfig()
        val title = config["notificationBannerTitle"] ?: "$appName Blocker Active"
        val desc = config["notificationBannerDescription"] ?: "Monitoring and blocking restricted apps."

        var finalIcon = applicationInfo.icon
        val customIconName = config["notificationIcon"]
        if (customIconName != null) {
            var resId = resources.getIdentifier(customIconName, "drawable", packageName)
            if (resId == 0) {
                resId = resources.getIdentifier(customIconName, "mipmap", packageName)
            }
            if (resId != 0) {
                finalIcon = resId
            }
        }

        if (finalIcon == 0) {
            finalIcon = android.R.drawable.ic_dialog_info
        }

        return builder
            .setContentTitle(title)
            .setContentText(desc)
            .setSmallIcon(finalIcon)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "App Blocker Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the app blocker running in the background."
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
