package com.example.zo_app_blocker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/**
 * A foreground service that acts as a reliable safety net for app blocking.
 *
 * While [AppBlockerAccessibilityService] handles instant event-based detection,
 * this service runs an active polling loop every [POLL_INTERVAL_MS] milliseconds
 * to catch any cases that the accessibility service may miss (e.g. delayed events,
 * service restart lag, or edge-case transitions).
 *
 * This mirrors how production screen-time / app-lock apps (e.g. Digital Wellbeing,
 * ActionDash) implement reliable blocking — using both event-driven detection AND
 * a safety-net polling loop.
 */
class AppBlockerForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID       = "zo_app_blocker_channel"
        private const val NOTIFICATION_ID  = 101
        /** How often (ms) to poll the foreground app as a safety net. */
        private const val POLL_INTERVAL_MS = 300L

        fun start(context: Context) {
            val intent = Intent(context, AppBlockerForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AppBlockerForegroundService::class.java))
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var isPolling = false

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (!isPolling) return
            poll()
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    // -------------------------------------------------------------------------
    // Service lifecycle
    // -------------------------------------------------------------------------

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        startPolling()
        // START_STICKY ensures Android restarts the service immediately if killed.
        return START_STICKY
    }

    override fun onDestroy() {
        stopPolling()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // -------------------------------------------------------------------------
    // Polling loop
    // -------------------------------------------------------------------------

    private fun startPolling() {
        if (isPolling) return
        isPolling = true
        handler.post(pollRunnable)
    }

    private fun stopPolling() {
        isPolling = false
        handler.removeCallbacks(pollRunnable)
    }

    /**
     * Core polling tick. Reads the last known foreground package from the
     * accessibility service and triggers a block if needed.
     *
     * Intentionally lightweight — delegates all blocking decisions to the
     * accessibility service which handles whitelists, block-all mode, etc.
     * [AppBlockerAccessibilityService.checkCurrentForegroundApp] is idempotent:
     * it will do nothing if the overlay is already showing for the current package.
     */
    private fun poll() {
        val service = AppBlockerAccessibilityService.instance ?: return

        // Skip if the foreground package hasn't been seen yet.
        if (service.lastPackage.isEmpty()) return

        // Delegate all decisions (whitelist, blockAll, getBlockedApps) to the service.
        service.checkCurrentForegroundApp()
    }

    // -------------------------------------------------------------------------
    // Notification
    // -------------------------------------------------------------------------

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        val icon = applicationInfo.icon
        val finalIcon = if (icon != 0) icon else android.R.drawable.ic_dialog_info

        val pm = packageManager
        val appName = try {
            applicationInfo.loadLabel(pm).toString()
        } catch (e: Exception) { "App" }

        val prefsManager = PreferencesManager(this)
        val config = prefsManager.getBlockScreenConfig()
        val title = config["notificationTitle"] ?: "$appName Blocker Active"
        val desc  = config["notificationDescription"] ?: "Monitoring and blocking restricted apps."

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
