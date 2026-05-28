package com.example.zo_app_blocker

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import io.flutter.FlutterInjector
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.dart.DartExecutor
import io.flutter.embedding.engine.plugins.util.GeneratedPluginRegister
import io.flutter.plugin.common.MethodChannel
import io.flutter.view.FlutterCallbackInformation
import io.flutter.embedding.android.FlutterView

/**
 * Manages the FlutterEngine and FlutterView used for rendering the customizable
 * block screen over other apps.
 *
 * It boots the engine using the user's saved Dart entrypoint callback, adds the
 * FlutterView to the WindowManager as a TYPE_ACCESSIBILITY_OVERLAY, and handles
 * the MethodChannel communication to send the blocked app data to Dart and receive
 * dismiss/unlock requests back.
 */
class FlutterOverlayManager(private val context: Context) {
    private var engine: FlutterEngine? = null
    private var flutterView: FlutterView? = null
    private var channel: MethodChannel? = null
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val prefsManager = PreferencesManager(context)
    private val handler = Handler(Looper.getMainLooper())

    private var isOverlayShowing = false
    private var isEngineReady = false
    private var currentBlockedPackage: String? = null

    // We cache the last blocked app data in case the engine is still booting
    // when the block request comes in, so we can send it once ready.
    private var pendingBlockData: Map<String, Any?>? = null

    /**
     * Optional: Pre-warm the FlutterEngine.
     * Call this when the foreground service starts so the engine is ready
     * immediately when an app is blocked, reducing the delay.
     */
    fun preWarmEngine() {
        if (engine == null && prefsManager.hasBlockScreenCallback()) {
            handler.post {
                startEngine()
            }
        }
    }

    /**
     * Boot the FlutterEngine using the saved callback handle.
     */
    private fun startEngine() {
        if (engine != null) return

        val callbackHandle = prefsManager.getBlockScreenCallbackHandle()
        if (callbackHandle == -1L) return

        val callbackInfo = FlutterCallbackInformation.lookupCallbackInformation(callbackHandle)
        if (callbackInfo == null) return

        engine = FlutterEngine(context.applicationContext)
        GeneratedPluginRegister.registerGeneratedPlugins(engine!!)

        channel = MethodChannel(engine!!.dartExecutor.binaryMessenger, "zo_app_blocker_block_screen")
        channel?.setMethodCallHandler { call, result ->
            when (call.method) {
                "blockScreenReady" -> {
                    isEngineReady = true
                    // Send any pending block event now that Dart is listening
                    pendingBlockData?.let {
                        sendBlockEvent(it)
                        pendingBlockData = null
                    }
                    result.success(null)
                }
                "dismissBlockScreen" -> {
                    hideOverlay()
                    // Send user to home screen
                    AppBlockerAccessibilityService.instance?.performGlobalAction(
                        android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME
                    )
                    result.success(null)
                }
                "requestUnlock" -> {
                    // Temporarily unlock the app
                    hideOverlay()
                    
                    val durationMinutes = call.argument<Int>("durationMinutes") ?: 15
                    
                    currentBlockedPackage?.let { pkg ->
                        // 1. Tell the service to temporarily whitelist this app
                        AppBlockerAccessibilityService.instance?.temporarilyUnblock(pkg, durationMinutes)
                        
                        // 2. Launch the app so the user is thrown right back into it
                        val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
                        if (launchIntent != null) {
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(launchIntent)
                        }
                    }
                    result.success(true)
                }
                else -> result.notImplemented()
            }
        }

        val dartEntrypoint = DartExecutor.DartEntrypoint(
            FlutterInjector.instance().flutterLoader().findAppBundlePath(),
            callbackInfo.callbackLibraryPath,
            callbackInfo.callbackName
        )
        engine!!.dartExecutor.executeDartEntrypoint(dartEntrypoint)
    }

    /**
     * Shows the Flutter overlay for the given blocked app.
     */
    fun showOverlay(packageName: String, appName: String?, appIcon: ByteArray?) {
        if (isOverlayShowing) return
        currentBlockedPackage = packageName

        handler.post {
            // Ensure engine is started
            if (engine == null) {
                startEngine()
            }

            // If we STILL don't have an engine (e.g. no callback registered), bail out
            // so the AccessibilityService can fall back to the native overlay.
            if (engine == null) return@post

            isOverlayShowing = true

            if (flutterView == null) {
                flutterView = FlutterView(context.applicationContext)
                flutterView?.attachToFlutterEngine(engine!!)
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )

            try {
                // Remove view if it was already added (defensive)
                if (flutterView?.parent != null) {
                    windowManager.removeView(flutterView)
                }
                windowManager.addView(flutterView, params)
                
                // Manually tell the engine it's resumed so it renders
                engine?.lifecycleChannel?.appIsResumed()

            } catch (e: Exception) {
                isOverlayShowing = false
                e.printStackTrace()
                return@post
            }

            val eventData = mapOf(
                "packageName" to packageName,
                "appName" to appName,
                "appIcon" to appIcon
            )

            if (isEngineReady) {
                sendBlockEvent(eventData)
            } else {
                pendingBlockData = eventData
            }
        }
    }

    private fun sendBlockEvent(data: Map<String, Any?>) {
        channel?.invokeMethod("onAppBlocked", data)
    }

    /**
     * Hides and removes the Flutter overlay.
     */
    fun hideOverlay() {
        if (!isOverlayShowing) return
        isOverlayShowing = false

        handler.post {
            try {
                if (flutterView != null && flutterView?.parent != null) {
                    windowManager.removeView(flutterView)
                    // Tell engine it's paused
                    engine?.lifecycleChannel?.appIsPaused()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Cleanup resources. Usually called when service is destroyed.
     */
    fun destroy() {
        hideOverlay()
        handler.post {
            flutterView?.detachFromFlutterEngine()
            flutterView = null
            engine?.destroy()
            engine = null
            channel?.setMethodCallHandler(null)
            channel = null
            isEngineReady = false
        }
    }
}
