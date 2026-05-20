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
    
    @Volatile private var lastPackage: String = ""
    @Volatile private var isOverlayShowing = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        prefsManager = PreferencesManager(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        if (prefsManager.isBlockAll() || prefsManager.getBlockedApps().isNotEmpty()) {
            AppBlockerForegroundService.start(this)
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        removeOverlay()
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
            removeOverlay()
            lastPackage = packageName
            return
        }
        
        if (packageName == this.packageName) {
            removeOverlay()
            lastPackage = packageName
            return
        }

        if (packageName != lastPackage) {
            lastPackage = packageName
            checkAndBlock(packageName)
        }
    }

    fun checkCurrentForegroundApp() {
        if (lastPackage.isNotEmpty() && lastPackage != "com.android.systemui" && !isLauncherPackage(lastPackage)) {
            checkAndBlock(lastPackage)
        }
    }

    private fun checkAndBlock(packageName: String) {
        val shouldBlock = if (prefsManager.isBlockAll()) {
            true
        } else {
            prefsManager.getBlockedApps().contains(packageName)
        }

        if (shouldBlock) {
            showOverlay(packageName)
        } else {
            removeOverlay()
        }
    }

    private fun showOverlay(packageName: String) {
        if (isOverlayShowing) return
        isOverlayShowing = true

        performGlobalAction(GLOBAL_ACTION_HOME)

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

    private fun removeOverlay() {
        if (!isOverlayShowing) return
        isOverlayShowing = false
        handler.post {
            try {
                if (overlayView != null) {
                    windowManager.removeView(overlayView)
                    overlayView = null
                }
            } catch (e: Exception) {}
        }
    }

    private fun createOverlayView(packageName: String): View {
        val config = prefsManager.getBlockScreenConfig()
        
        val bgColor = parseColorSafe(config["backgroundColor"], "#FF0000")
        val tColor = parseColorSafe(config["titleColor"], "#FFFFFF")
        val dColor = parseColorSafe(config["descriptionColor"], "#EEEEEE")
        
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(bgColor)
            setPadding(64, 64, 64, 64)
        }

        val titleView = TextView(this).apply {
            text = config["title"]
            setTextColor(tColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 32)
        }

        val descView = TextView(this).apply {
            text = config["description"]
            setTextColor(dColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            gravity = Gravity.CENTER
        }

        val btn = android.widget.Button(this).apply {
            text = "Exit"
            setOnClickListener {
                performGlobalAction(GLOBAL_ACTION_HOME)
                removeOverlay()
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 64
            }
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
        return try {
            Color.parseColor(colorStr ?: defaultColor)
        } catch (e: Exception) {
            Color.parseColor(defaultColor)
        }
    }

    private fun isLauncherPackage(packageName: String): Boolean {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        val res = packageManager.resolveActivity(intent, 0)
        return res?.activityInfo?.packageName == packageName
    }
}
