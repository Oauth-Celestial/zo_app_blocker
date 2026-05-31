package com.example.zo_app_blocker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A foreground service that acts as the primary engine for app blocking.
 * It uses UsageStatsManager to poll for foreground apps every second.
 */
class AppBlockerForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID       = "zo_app_blocker_channel"
        private const val NOTIFICATION_ID  = 101
        private const val POLL_INTERVAL_MS = 1000L

        @Volatile var instance: AppBlockerForegroundService? = null
            private set

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

    private lateinit var prefsManager: PreferencesManager
    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    internal lateinit var flutterOverlayManager: FlutterOverlayManager

    // Map of packageName -> Expiration Time (Unix Epoch in ms)
    private val temporaryWhitelist = mutableMapOf<String, Long>()

    @Volatile var lastPackage: String = ""
        private set

    // Time-limit tracking state
    private var activeTimedPackage: String? = null
    private var sessionStartMs: Long = 0L
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
        instance = this
        prefsManager = PreferencesManager(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        flutterOverlayManager = FlutterOverlayManager(this)

        createNotificationChannel()
        flutterOverlayManager.preWarmEngine()
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
        return START_STICKY
    }

    override fun onDestroy() {
        flushActiveSession()
        stopPolling()
        removeOverlay()
        flutterOverlayManager.destroy()
        instance = null
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

    private fun poll() {
        val today = todayString()
        if (today != lastCheckedDate) {
            handleMidnightReset(prefsManager, today)
        }

        val currentPkg = getForegroundAppFromUsageStats()
        if (currentPkg.isNullOrEmpty()) {
            flushActiveSessionTo(prefsManager)
            return
        }

        if (currentPkg == "com.android.systemui" || currentPkg == this.packageName || isLauncherPackage(currentPkg)) {
            lastPackage = currentPkg
            return
        }

        lastPackage = currentPkg

        val timeLimitInfo = prefsManager.getAppTimeLimit(currentPkg)

        if (timeLimitInfo != null) {
            val remaining = timeLimitInfo["remainingSeconds"] as? Long ?: 0L

            if (remaining <= 0L) {
                flushActiveSessionTo(prefsManager)
                ensureAppIsBlocked(currentPkg, prefsManager)
                updateNotificationDefault(prefsManager)
                return
            }

            if (activeTimedPackage != currentPkg) {
                flushActiveSessionTo(prefsManager)
                activeTimedPackage = currentPkg
                sessionStartMs = System.currentTimeMillis()
            }

            val sessionElapsedSec = (System.currentTimeMillis() - sessionStartMs) / 1000L
            val liveRemaining = (remaining - sessionElapsedSec).coerceAtLeast(0L)

            if (liveRemaining <= 0L) {
                flushActiveSessionTo(prefsManager)
                ensureAppIsBlocked(currentPkg, prefsManager)
                updateNotificationDefault(prefsManager)
                return
            }

            val appName = getAppName(currentPkg)
            updateNotificationCountdown(appName, liveRemaining)

        } else {
            if (activeTimedPackage != null) {
                flushActiveSessionTo(prefsManager)
                updateNotificationDefault(prefsManager)
            }
            checkCurrentForegroundApp()
        }
    }

    private fun getForegroundAppFromUsageStats(): String? {
        var foregroundApp: String? = null
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        val events = usm.queryEvents(time - POLL_INTERVAL_MS * 2, time)
        if (events != null) {
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                    foregroundApp = event.packageName
                }
            }
        }
        return foregroundApp ?: lastPackage
    }

    // -------------------------------------------------------------------------
    // Blocking Logic
    // -------------------------------------------------------------------------

    fun checkCurrentForegroundApp() {
        if (flutterOverlayManager.isOverlayVisible || overlayView != null) {
            val blockedPkg = flutterOverlayManager.currentBlockedPackage
                ?: return
            val stillBlocked = if (prefsManager.isBlockAll()) true
                               else prefsManager.getBlockedApps().contains(blockedPkg)
            if (!stillBlocked) {
                removeOverlay()
            }
            return
        }

        val pkg = lastPackage
        if (pkg.isEmpty() || pkg == "com.android.systemui" || isLauncherPackage(pkg)) return

        val whitelistExpiration = temporaryWhitelist[pkg]
        if (whitelistExpiration != null && System.currentTimeMillis() < whitelistExpiration) return

        val shouldBlock = if (prefsManager.isBlockAll()) true
                          else prefsManager.getBlockedApps().contains(pkg)
        if (shouldBlock) {
            showOverlayForPackage(pkg)
        }
    }

    fun showOverlayForPackage(packageName: String) {
        if ((flutterOverlayManager.isOverlayVisible || overlayView != null) &&
            flutterOverlayManager.currentBlockedPackage == packageName) return

        goHome()

        prefsManager.logBlockEvent(packageName)

        if (prefsManager.hasBlockScreenCallback()) {
            flutterOverlayManager.showOverlay(packageName, null, null)

            Thread {
                val pm = packageManager
                var appName: String? = null
                var appIcon: ByteArray? = null
                try {
                    val appInfo = pm.getApplicationInfo(packageName, 0)
                    appName = pm.getApplicationLabel(appInfo).toString()
                    val appResolver = AppResolver(this)
                    appIcon = appResolver.getAppIconSync(packageName)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                if (appName != null || appIcon != null) {
                    flutterOverlayManager.updateBlockedAppData(packageName, appName, appIcon)
                }
            }.start()
        } else {
            showNativeOverlay(packageName)
        }
    }

    fun temporarilyUnblock(packageName: String, durationMinutes: Int = 15) {
        val durationMs = durationMinutes * 60 * 1000L
        val expiration = System.currentTimeMillis() + durationMs
        temporaryWhitelist[packageName] = expiration
        if (lastPackage == packageName) {
            lastPackage = ""
        }

        handler.postDelayed({
            checkCurrentForegroundApp()
        }, durationMs)
    }

    private fun goHome() {
        val startMain = Intent(Intent.ACTION_MAIN)
        startMain.addCategory(Intent.CATEGORY_HOME)
        startMain.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(startMain)
    }

    private fun removeOverlay() {
        flutterOverlayManager.hideOverlay()
        handler.post {
            try {
                if (overlayView != null && overlayView?.parent != null) {
                    windowManager.removeView(overlayView)
                    overlayView = null
                }
            } catch (e: Exception) {
                overlayView = null
            }
        }
    }

    private fun showNativeOverlay(packageName: String) {
        if (overlayView != null) return

        handler.post {
            try {
                if (overlayView == null) {
                    overlayView = createOverlayView(packageName)
                    val params = WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT,
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        } else {
                            WindowManager.LayoutParams.TYPE_PHONE
                        },
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                        PixelFormat.TRANSLUCENT
                    )
                    windowManager.addView(overlayView, params)
                }
            } catch (e: Exception) {
                overlayView = null
                e.printStackTrace()
            }
        }
    }

    private fun createOverlayView(packageName: String): View {
        val config = prefsManager.getBlockScreenConfig()

        val bgColor = parseColorSafe(config["backgroundColor"], "#F44336")
        val tColor  = parseColorSafe(config["titleColor"],      "#FFFFFF")
        val dColor  = parseColorSafe(config["descriptionColor"],"#EEEEEE")

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(bgColor)
            setPadding(80, 80, 80, 80)
            isClickable = true
            isFocusable  = true
        }

        val titleView = TextView(this).apply {
            text = config["title"] ?: "App Blocked"
            setTextColor(tColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 32f)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }

        val descView = TextView(this).apply {
            text = config["description"] ?: "This app is blocked."
            setTextColor(dColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            gravity = Gravity.CENTER
            setLineSpacing(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4f, resources.displayMetrics), 1.0f)
            setPadding(0, 0, 0, 64)
        }

        val btn = android.widget.Button(this).apply {
            text = "Exit"
            setTextColor(bgColor)
            setBackgroundColor(tColor)
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(64, 32, 64, 32)
            elevation = 8f
            setOnClickListener {
                goHome()
                removeOverlay()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        layout.addView(titleView)
        layout.addView(descView)
        layout.addView(btn)
        return layout
    }

    private fun parseColorSafe(colorStr: String?, defaultColor: String): Int {
        if (colorStr.isNullOrEmpty()) return Color.parseColor(defaultColor)
        return try {
            Color.parseColor(colorStr)
        } catch (e: Exception) {
            try {
                if (colorStr.startsWith("0x", ignoreCase = true)) {
                    colorStr.substring(2).toLong(16).toInt()
                } else {
                    colorStr.toLong().toInt()
                }
            } catch (e2: Exception) {
                Color.parseColor(defaultColor)
            }
        }
    }

    private fun isLauncherPackage(packageName: String): Boolean {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        val res = packageManager.resolveActivity(intent, 0)
        return res?.activityInfo?.packageName == packageName
    }

    // -------------------------------------------------------------------------
    // Session flushing
    // -------------------------------------------------------------------------

    private fun flushActiveSession() {
        val pkg = activeTimedPackage ?: return
        val elapsed = (System.currentTimeMillis() - sessionStartMs) / 1000L
        if (elapsed > 0L) {
            PreferencesManager(this).addUsedSeconds(pkg, elapsed)
        }
        activeTimedPackage = null
        sessionStartMs = 0L
    }

    private fun flushActiveSessionTo(prefsManager: PreferencesManager) {
        val pkg = activeTimedPackage ?: return
        val elapsed = (System.currentTimeMillis() - sessionStartMs) / 1000L
        if (elapsed > 0L) {
            prefsManager.addUsedSeconds(pkg, elapsed)
        }
        activeTimedPackage = null
        sessionStartMs = 0L
    }

    private fun ensureAppIsBlocked(packageName: String, prefsManager: PreferencesManager) {
        val blocked = prefsManager.getBlockedApps()
        if (!blocked.contains(packageName)) {
            val updated = blocked.toMutableSet().apply { add(packageName) }
            prefsManager.saveBlockedApps(updated)
        }
        checkCurrentForegroundApp()
    }

    private fun handleMidnightReset(prefsManager: PreferencesManager, today: String) {
        lastCheckedDate = today
        flushActiveSessionTo(prefsManager)
        prefsManager.resetAllDailyUsage()

        val timeLimitedPackages = prefsManager.getTimeLimitedPackages()
        if (timeLimitedPackages.isNotEmpty()) {
            val currentBlocked = prefsManager.getBlockedApps().toMutableSet()
            val wasModified = currentBlocked.removeAll(timeLimitedPackages)
            if (wasModified) {
                prefsManager.saveBlockedApps(currentBlocked)
                checkCurrentForegroundApp()
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
