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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
 *
 * Additionally, this service manages the **Daily Time Limit** feature:
 * - Tracks how long the current foreground timed-app has been open this session.
 * - Updates the notification in real-time with remaining time.
 * - Auto-blocks the app when the daily budget is exhausted.
 * - Resets all daily usage counters at midnight.
 */
class AppBlockerForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID       = "zo_app_blocker_channel"
        private const val NOTIFICATION_ID  = 101
        /** How often (ms) to poll the foreground app as a safety net. */
        private const val POLL_INTERVAL_MS = 1000L   // 1 second — drives the countdown timer

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

        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        fun todayString(): String = DATE_FORMAT.format(Date())
    }

    private val handler = Handler(Looper.getMainLooper())
    private var isPolling = false

    // -------------------------------------------------------------------------
    // Time-limit tracking state
    // -------------------------------------------------------------------------

    /** Package name of the timed app currently in the foreground, or null. */
    private var activeTimedPackage: String? = null

    /** Wall-clock ms when the current foreground session for [activeTimedPackage] started. */
    private var sessionStartMs: Long = 0L

    /** The date string of the last poll — used to detect day rollover. */
    private var lastCheckedDate: String = todayString()

    // -------------------------------------------------------------------------
    // Polling runnable
    // -------------------------------------------------------------------------

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
        val notification = buildNotification(null, null)
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
        flushActiveSession()
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
     * Core polling tick. Runs every second.
     *
     * 1. Check for midnight rollover → reset daily usage, unblock time-limited apps.
     * 2. Get the current foreground package from the accessibility service.
     * 3. If it has a time limit, track the session and update the notification.
     * 4. If the budget runs out, block the app immediately.
     * 5. Delegate the normal block-list check to the accessibility service.
     */
    private fun poll() {
        val prefsManager = PreferencesManager(this)

        // --- 1. Midnight rollover check ---
        val today = todayString()
        if (today != lastCheckedDate) {
            handleMidnightReset(prefsManager, today)
        }

        val service = AppBlockerAccessibilityService.instance

        // --- 2. Get current foreground package ---
        val currentPkg = service?.lastPackage ?: run {
            // Accessibility service not connected; flush any active session.
            flushActiveSessionTo(prefsManager)
            return
        }

        if (currentPkg.isEmpty()) {
            flushActiveSessionTo(prefsManager)
            return
        }

        // --- 3. Time-limit tracking ---
        val timeLimitInfo = prefsManager.getAppTimeLimit(currentPkg)

        if (timeLimitInfo != null) {
            // This app has a daily time limit.
            val remaining = timeLimitInfo["remainingSeconds"] as? Long ?: 0L

            if (remaining <= 0L) {
                // --- 4. Budget already exhausted — ensure it is blocked ---
                flushActiveSessionTo(prefsManager)
                ensureAppIsBlocked(currentPkg, prefsManager, service)
                updateNotificationDefault(prefsManager)
                return
            }

            // Start a new session if the timed app just became foreground.
            if (activeTimedPackage != currentPkg) {
                // Flush the previous session if there was one.
                flushActiveSessionTo(prefsManager)
                activeTimedPackage = currentPkg
                sessionStartMs = System.currentTimeMillis()
            }

            // Calculate elapsed seconds in this session (not yet flushed to DB).
            val sessionElapsedSec = (System.currentTimeMillis() - sessionStartMs) / 1000L
            val liveRemaining = (remaining - sessionElapsedSec).coerceAtLeast(0L)

            if (liveRemaining <= 0L) {
                // Budget just ran out mid-session — flush and block.
                flushActiveSessionTo(prefsManager)
                ensureAppIsBlocked(currentPkg, prefsManager, service)
                updateNotificationDefault(prefsManager)
                return
            }

            // Update notification with live countdown.
            val appName = getAppName(currentPkg)
            updateNotificationCountdown(appName, liveRemaining)

        } else {
            // Current app has no time limit — flush any previous timed session.
            if (activeTimedPackage != null) {
                flushActiveSessionTo(prefsManager)
                updateNotificationDefault(prefsManager)
            }
            // Standard block-list check.
            service.checkCurrentForegroundApp()
        }
    }

    // -------------------------------------------------------------------------
    // Session flushing
    // -------------------------------------------------------------------------

    /** Flush the active timed session without a PreferencesManager instance. */
    private fun flushActiveSession() {
        val pkg = activeTimedPackage ?: return
        val elapsed = (System.currentTimeMillis() - sessionStartMs) / 1000L
        if (elapsed > 0L) {
            PreferencesManager(this).addUsedSeconds(pkg, elapsed)
        }
        activeTimedPackage = null
        sessionStartMs = 0L
    }

    /** Flush the active timed session using an existing [prefsManager] instance. */
    private fun flushActiveSessionTo(prefsManager: PreferencesManager) {
        val pkg = activeTimedPackage ?: return
        val elapsed = (System.currentTimeMillis() - sessionStartMs) / 1000L
        if (elapsed > 0L) {
            prefsManager.addUsedSeconds(pkg, elapsed)
        }
        activeTimedPackage = null
        sessionStartMs = 0L
    }

    // -------------------------------------------------------------------------
    // Auto-block when budget is exhausted
    // -------------------------------------------------------------------------

    private fun ensureAppIsBlocked(
        packageName: String,
        prefsManager: PreferencesManager,
        service: AppBlockerAccessibilityService?
    ) {
        val blocked = prefsManager.getBlockedApps()
        if (!blocked.contains(packageName)) {
            val updated = blocked.toMutableSet().apply { add(packageName) }
            prefsManager.saveBlockedApps(updated)
        }
        service?.checkCurrentForegroundApp()
    }

    // -------------------------------------------------------------------------
    // Midnight reset
    // -------------------------------------------------------------------------

    private fun handleMidnightReset(prefsManager: PreferencesManager, today: String) {
        lastCheckedDate = today

        // Flush any active session before resetting (it belongs to the previous day).
        flushActiveSessionTo(prefsManager)

        // Reset all daily usage counters.
        prefsManager.resetAllDailyUsage()

        // Remove time-limit-exhausted packages from the blocked list so they're
        // accessible again in the new day.
        val timeLimitedPackages = prefsManager.getTimeLimitedPackages()
        if (timeLimitedPackages.isNotEmpty()) {
            val currentBlocked = prefsManager.getBlockedApps().toMutableSet()
            val wasModified = currentBlocked.removeAll(timeLimitedPackages)
            if (wasModified) {
                prefsManager.saveBlockedApps(currentBlocked)
                AppBlockerAccessibilityService.instance?.checkCurrentForegroundApp()
            }
        }
    }

    // -------------------------------------------------------------------------
    // Notification
    // -------------------------------------------------------------------------

    private fun getAppName(packageName: String): String {
        return try {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    private fun formatRemainingTime(seconds: Long): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return if (mins > 0) {
            "$mins min ${secs} sec"
        } else {
            "${secs} sec"
        }
    }

    private fun updateNotificationCountdown(appName: String, remainingSeconds: Long) {
        val timeStr = formatRemainingTime(remainingSeconds)
        val warning = if (remainingSeconds <= 60) " ⚠️" else ""
        val notification = buildNotification(
            title = "$appName — Time Limit",
            text = "$timeStr remaining today$warning"
        )
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun updateNotificationDefault(prefsManager: PreferencesManager) {
        val notification = buildNotification(null, null)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(title: String?, text: String?): Notification {
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

        val notifTitle = title ?: (config["notificationTitle"] ?: "$appName Blocker Active")
        val notifDesc = text ?: (config["notificationDescription"] ?: "Monitoring and blocking restricted apps.")

        return builder
            .setContentTitle(notifTitle)
            .setContentText(notifDesc)
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
