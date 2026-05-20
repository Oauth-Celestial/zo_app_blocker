package com.example.zo_app_blocker

import android.app.Activity
import android.content.Context
import androidx.annotation.NonNull
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ZoAppBlockerPlugin : FlutterPlugin, MethodCallHandler, ActivityAware {
    private lateinit var channel: MethodChannel
    private var context: Context? = null
    private var activity: Activity? = null
    private var permissionManager: PermissionManager? = null
    private var appResolver: AppResolver? = null
    private var prefsManager: PreferencesManager? = null

    companion object {
        var instance: ZoAppBlockerPlugin? = null
            private set
    }

    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "zo_app_blocker")
        channel.setMethodCallHandler(this)
        
        context = flutterPluginBinding.applicationContext
        permissionManager = PermissionManager(context!!)
        appResolver = AppResolver(context!!)
        prefsManager = PreferencesManager(context!!)
        instance = this
    }

    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
        val prefs = prefsManager ?: return result.error("NO_PREFS", "Preferences not initialized", null)
        when (call.method) {
            "checkPermission" -> {
                result.success(permissionManager?.checkPermission() ?: "denied")
            }
            "requestPermission" -> {
                activity?.let { permissionManager?.requestPermission(it) }
                result.success(null)
            }
            "getApps" -> {
                CoroutineScope(Dispatchers.Main).launch {
                    val apps = appResolver?.getInstalledApps() ?: emptyList()
                    result.success(apps)
                }
            }
            "getBlockedApps" -> {
                CoroutineScope(Dispatchers.Main).launch {
                    val apps = appResolver?.getInstalledApps() ?: emptyList()
                    val blockedPackages = prefs.getBlockedApps()
                    val blockedApps = apps.filter {
                        val packageName = (it as? Map<*, *>)?.get("packageName") as? String
                        blockedPackages.contains(packageName)
                    }
                    result.success(blockedApps)
                }
            }
            "blockApps" -> {
                val identifiers = call.argument<List<String>>("identifiers") ?: emptyList()
                val current = prefs.getBlockedApps().toMutableSet()
                current.addAll(identifiers)
                prefs.saveBlockedApps(current)
                prefs.setBlockAll(false)
                AppBlockerAccessibilityService.instance?.checkCurrentForegroundApp()
                context?.let { AppBlockerForegroundService.start(it) }
                result.success(null)
            }
            "unblockApps" -> {
                val identifiers = call.argument<List<String>>("identifiers") ?: emptyList()
                val current = prefs.getBlockedApps().toMutableSet()
                current.removeAll(identifiers.toSet())
                prefs.saveBlockedApps(current)
                AppBlockerAccessibilityService.instance?.checkCurrentForegroundApp()
                if (!prefs.isBlockAll() && current.isEmpty()) {
                    context?.let { AppBlockerForegroundService.stop(it) }
                }
                result.success(null)
            }
            "blockAll" -> {
                prefs.setBlockAll(true)
                AppBlockerAccessibilityService.instance?.checkCurrentForegroundApp()
                context?.let { AppBlockerForegroundService.start(it) }
                result.success(null)
            }
            "unblockAll" -> {
                prefs.setBlockAll(false)
                prefs.saveBlockedApps(emptySet())
                AppBlockerAccessibilityService.instance?.checkCurrentForegroundApp()
                context?.let { AppBlockerForegroundService.stop(it) }
                result.success(null)
            }
            "setBlockScreenConfig" -> {
                val config = call.arguments as? Map<String, String> ?: emptyMap()
                prefs.setBlockScreenConfig(config)
                result.success(null)
            }
            else -> {
                result.notImplemented()
            }
        }
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
        instance = null
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
    }

    override fun onDetachedFromActivityForConfigChanges() {
        activity = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activity = binding.activity
    }

    override fun onDetachedFromActivity() {
        activity = null
    }
}
