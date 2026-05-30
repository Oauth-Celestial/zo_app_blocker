package com.example.zo_app_blocker

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.LinearLayout
import android.widget.TextView
import android.content.Intent

class AppBlockerAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile var instance: AppBlockerAccessibilityService? = null
            private set
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefsManager: PreferencesManager
    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null

    internal lateinit var flutterOverlayManager: FlutterOverlayManager

    /** Exposed for the foreground service's polling loop. */
    val flutterOverlayManagerRef: FlutterOverlayManager?
        get() = if (::flutterOverlayManager.isInitialized) flutterOverlayManager else null

    /**
     * The most-recently-seen foreground package.
     * Marked @Volatile so the foreground service's polling thread can read it safely.
     */
    @Volatile var lastPackage: String = ""
        private set

    // Map of packageName -> Expiration Time (Unix Epoch in ms)
    private val temporaryWhitelist = mutableMapOf<String, Long>()

    // -------------------------------------------------------------------------
    // Service lifecycle
    // -------------------------------------------------------------------------

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        prefsManager = PreferencesManager(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        flutterOverlayManager = FlutterOverlayManager(this)

        if (prefsManager.isBlockAll() || prefsManager.getBlockedApps().isNotEmpty()) {
            AppBlockerForegroundService.start(this)
            // Pre-warm the FlutterEngine so it's ready when an app is first blocked.
            flutterOverlayManager.preWarmEngine()
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        removeOverlay()
        flutterOverlayManager.destroy()
        instance = null
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    // -------------------------------------------------------------------------
    // Accessibility event handling
    // -------------------------------------------------------------------------

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val packageName = event.packageName?.toString() ?: return

        // Always keep lastPackage current so polling works after overlay is dismissed.
        if (packageName == "com.android.systemui" ||
            packageName == this.packageName ||
            isLauncherPackage(packageName)
        ) {
            lastPackage = packageName
            // *** Do NOT remove the overlay here. ***
            // If overlay is currently showing, it must stay up until the user
            // explicitly presses the Exit / Unlock button.
            // The EXIT button already calls performGlobalAction(HOME) + hideOverlay().
            return
        }

        lastPackage = packageName

        // If overlay is already showing, don't interfere — it stays up.
        // The only removal path is the user pressing Exit/Unlock.
        val overlayUp = flutterOverlayManager.isOverlayVisible || overlayView != null
        if (overlayUp) return

        checkAndBlock(packageName)
    }


    // -------------------------------------------------------------------------
    // Public API (called by foreground service polling and plugin)
    // -------------------------------------------------------------------------

    /**
     * Called by the foreground service's polling loop and by the plugin after
     * blockApps/unblockApps to re-evaluate the current foreground app.
     */
    fun checkCurrentForegroundApp() {
        // --- Case 1: overlay is already visible ---
        // Only remove if the package it is blocking has been explicitly unblocked.
        // Never remove it just because the foreground app changed to something else;
        // that would let users bypass the block by opening a second app.
        if (flutterOverlayManager.isOverlayVisible || overlayView != null) {
            val blockedPkg = flutterOverlayManager.currentBlockedPackage
                ?: return // native overlay — leave it alone
            val stillBlocked = if (prefsManager.isBlockAll()) true
                               else prefsManager.getBlockedApps().contains(blockedPkg)
            if (!stillBlocked) {
                // Package was unblocked from the plugin — dismiss.
                removeOverlay()
            }
            // Otherwise keep the overlay up regardless of what is in the foreground.
            return
        }

        // --- Case 2: no overlay showing — decide whether to show one ---
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

    /**
     * Show the overlay for a specific package. Used both internally and by the
     * foreground service's polling loop when it detects a blocked app.
     * This is the single entry point for triggering a block.
     */
    fun showOverlayForPackage(packageName: String) {
        // Skip if already showing for this package.
        if ((flutterOverlayManager.isOverlayVisible || overlayView != null) &&
            flutterOverlayManager.currentBlockedPackage == packageName) return

        // First line of defense: instantly send them home so they can't interact.
        performGlobalAction(GLOBAL_ACTION_HOME)

        prefsManager.logBlockEvent(packageName)

        if (prefsManager.hasBlockScreenCallback()) {
            // Show overlay immediately with placeholder data — zero latency on screen.
            flutterOverlayManager.showOverlay(packageName, null, null)

            // Fetch app name + icon on a background thread, then push the data
            // to Dart as an update (no view recreation needed).
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
                // Push updated data to the already-visible overlay.
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

        // Auto-block when the whitelist entry expires.
        handler.postDelayed({
            checkCurrentForegroundApp()
        }, durationMs)
    }

    // -------------------------------------------------------------------------
    // Internal blocking logic
    // -------------------------------------------------------------------------

    private fun checkAndBlock(packageName: String) {
        val whitelistExpiration = temporaryWhitelist[packageName]
        if (whitelistExpiration != null) {
            if (System.currentTimeMillis() < whitelistExpiration) {
                return // Still whitelisted
            } else {
                temporaryWhitelist.remove(packageName) // Expired
            }
        }

        val shouldBlock = if (prefsManager.isBlockAll()) true
                          else prefsManager.getBlockedApps().contains(packageName)

        if (shouldBlock) {
            showOverlayForPackage(packageName)
        }
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

    // -------------------------------------------------------------------------
    // Native (non-Flutter) overlay — fallback when no callback is registered
    // -------------------------------------------------------------------------

    private fun showNativeOverlay(packageName: String) {
        if (overlayView != null) return // Already showing native overlay

        handler.post {
            try {
                if (overlayView == null) {
                    overlayView = createOverlayView(packageName)
                    val params = WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
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
                performGlobalAction(GLOBAL_ACTION_HOME)
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
}
