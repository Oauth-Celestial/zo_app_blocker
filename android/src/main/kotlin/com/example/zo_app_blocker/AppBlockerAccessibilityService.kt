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
    
    private lateinit var flutterOverlayManager: FlutterOverlayManager
    
    @Volatile private var lastPackage: String = ""
    @Volatile private var isOverlayShowing = false

    // Map of packageName -> Expiration Time (Unix Epoch in ms)
    private val temporaryWhitelist = mutableMapOf<String, Long>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        prefsManager = PreferencesManager(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        flutterOverlayManager = FlutterOverlayManager(this)
        
        if (prefsManager.isBlockAll() || prefsManager.getBlockedApps().isNotEmpty()) {
            AppBlockerForegroundService.start(this)
            // Pre-warm the FlutterEngine so it's ready when an app is blocked
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

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString() ?: return

        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        
        // System UI and launcher checks
        if (packageName == "com.android.systemui" || isLauncherPackage(packageName)) {
            lastPackage = packageName
            return
        }
        
        if (packageName == this.packageName) {
            lastPackage = packageName
            return
        }

        // Ignore events while overlay is showing to prevent flicker
        if (isOverlayShowing) return

        if (packageName != lastPackage) {
            lastPackage = packageName
            checkAndBlock(packageName)
        }
    }

    fun checkCurrentForegroundApp() {
        if (lastPackage.isNotEmpty() && lastPackage != "com.android.systemui" && !isLauncherPackage(lastPackage)) {
            
            val whitelistExpiration = temporaryWhitelist[lastPackage]
            if (whitelistExpiration != null && System.currentTimeMillis() < whitelistExpiration) {
                removeOverlay()
                return
            }
            
            val shouldBlock = if (prefsManager.isBlockAll()) true
                              else prefsManager.getBlockedApps().contains(lastPackage)
            if (shouldBlock) {
                showOverlay(lastPackage)
            } else {
                removeOverlay()
            }
        } else {
            removeOverlay()
        }
    }

    fun temporarilyUnblock(packageName: String, durationMinutes: Int = 15) {
        val durationMs = durationMinutes * 60 * 1000L
        val expiration = System.currentTimeMillis() + durationMs
        temporaryWhitelist[packageName] = expiration
        // Clear lastPackage so if they are already in the app, it can be re-evaluated
        // when they come back later, or allow them to immediately use it.
        if (lastPackage == packageName) {
            lastPackage = ""
        }
        
        // Auto-block the app when the time expires
        handler.postDelayed({
            // Force re-eval of current foreground app
            checkCurrentForegroundApp()
        }, durationMs)
    }

    private fun checkAndBlock(packageName: String) {
        // Check if it's temporarily whitelisted
        val whitelistExpiration = temporaryWhitelist[packageName]
        if (whitelistExpiration != null) {
            if (System.currentTimeMillis() < whitelistExpiration) {
                // Still whitelisted, do not block
                return
            } else {
                // Expired, remove from whitelist
                temporaryWhitelist.remove(packageName)
            }
        }

        val shouldBlock = if (prefsManager.isBlockAll()) {
            true
        } else {
            prefsManager.getBlockedApps().contains(packageName)
        }

        if (shouldBlock) {
            showOverlay(packageName)
        }
    }

    private fun showOverlay(packageName: String) {
        if (isOverlayShowing) return
        isOverlayShowing = true

        // First line of defense: instantly send them home so they can't interact
        performGlobalAction(GLOBAL_ACTION_HOME)
        
        prefsManager.logBlockEvent(packageName)

        // Determine if we should use the Flutter overlay or native overlay
        if (prefsManager.hasBlockScreenCallback()) {
            // Get app info for the Flutter context
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

            // Let FlutterOverlayManager handle it
            flutterOverlayManager.showOverlay(packageName, appName, appIcon)
        } else {
            // Fall back to the native legacy overlay
            showNativeOverlay(packageName)
        }
    }

    private fun removeOverlay() {
        if (!isOverlayShowing) return
        isOverlayShowing = false
        
        flutterOverlayManager.hideOverlay()
        
        handler.post {
            try {
                if (overlayView != null && overlayView?.parent != null) {
                    windowManager.removeView(overlayView)
                    overlayView = null
                }
            } catch (e: Exception) {}
        }
    }

    private fun showNativeOverlay(packageName: String) {
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
                                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        PixelFormat.TRANSLUCENT
                    )
                    windowManager.addView(overlayView, params)
                }
            } catch (e: Exception) {
                isOverlayShowing = false
            }
        }
    }

    private fun createOverlayView(packageName: String): View {
        val config = prefsManager.getNotificationConfig()
        
        val bgColor = parseColorSafe(config["backgroundColor"], "#F44336")
        val tColor = parseColorSafe(config["titleColor"], "#FFFFFF")
        val dColor = parseColorSafe(config["descriptionColor"], "#EEEEEE")
        
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(bgColor)
            setPadding(80, 80, 80, 80)
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
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams = lp
        }

        layout.addView(titleView)
        layout.addView(descView)
        layout.addView(btn)

        // Make layout intercept touches so users can't click through it
        layout.isClickable = true
        layout.isFocusable = true

        return layout
    }

    private fun parseColorSafe(colorStr: String?, defaultColor: String): Int {
        if (colorStr.isNullOrEmpty()) return Color.parseColor(defaultColor)
        return try {
            Color.parseColor(colorStr)
        } catch (e: Exception) {
            try {
                // Handle cases where the color is passed as an integer string (e.g. "4278190080" or "0xFF...")
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
