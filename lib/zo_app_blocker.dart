import 'dart:typed_data';
import 'dart:ui';
import 'zo_app_blocker_platform_interface.dart';
import 'src/app_time_limit.dart';

export 'app_info.dart';
export 'src/block_screen_context.dart';
export 'src/zo_block_screen_runner.dart';
export 'src/app_time_limit.dart';

/// The main entry point for the Zo App Blocker plugin.
///
/// Use [ZoAppBlocker.instance] to access the singleton instance and invoke
/// app blocking methods.
class ZoAppBlocker {
  ZoAppBlocker._();

  /// The singleton instance of [ZoAppBlocker].
  static final ZoAppBlocker instance = ZoAppBlocker._();

  /// Checks the current status of the required accessibility permissions.
  ///
  /// On Android, this checks if the Accessibility Service is enabled.
  /// On iOS, this checks Family Controls authorization.
  /// Returns a [String] representing the status (e.g., 'granted', 'denied').
  Future<String> checkAccessibilityPermission() {
    return ZoAppBlockerPlatform.instance.checkAccessibilityPermission();
  }

  /// Requests the necessary accessibility permissions to block apps.
  ///
  /// On Android, this opens the device's Accessibility Settings page so the user
  /// can enable the service.
  /// On iOS, this prompts the user for Family Controls authorization.
  Future<void> requestAccessibilityPermission() {
    return ZoAppBlockerPlatform.instance.requestAccessibilityPermission();
  }

  /// Checks the current status of the notification permission.
  ///
  /// Returns a [String] representing the status (e.g., 'granted', 'denied').
  Future<String> checkNotificationPermission() {
    return ZoAppBlockerPlatform.instance.checkNotificationPermission();
  }

  /// Requests notification permission from the user.
  ///
  /// Required on Android 13+ to show the foreground service notification.
  Future<void> requestNotificationPermission() {
    return ZoAppBlockerPlatform.instance.requestNotificationPermission();
  }

  /// Retrieves a list of apps that can be blocked.
  ///
  /// On Android, this returns a list of installed packages.
  /// On iOS, this opens the native `FamilyActivityPicker` and returns the
  /// opaque tokens for the selected apps/categories.
  Future<List<Map<String, dynamic>>> getApps() {
    return ZoAppBlockerPlatform.instance.getApps();
  }

  /// Retrieves the icon of a specific app by its [packageName].
  ///
  /// Returns a [Uint8List] containing the PNG bytes of the icon, or null
  /// if the icon cannot be found or is not supported (e.g. on iOS).
  Future<Uint8List?> getAppIcon(String packageName) async {
    final bytes = await ZoAppBlockerPlatform.instance.getAppIcon(packageName);
    if (bytes == null) return null;
    return Uint8List.fromList(bytes);
  }

  /// Retrieves a list of apps that are currently blocked.
  ///
  /// Returns a list of maps containing information about the blocked apps.
  Future<List<Map<String, dynamic>>> getBlockedApps() {
    return ZoAppBlockerPlatform.instance.getBlockedApps();
  }

  /// Blocks the apps identified by the provided [identifiers].
  ///
  /// On Android, the identifiers are the package names (e.g., 'com.example.app').
  /// On iOS, these are the base64-encoded strings of the opaque tokens returned
  /// by the FamilyActivityPicker.
  Future<void> blockApps(List<String> identifiers) {
    return ZoAppBlockerPlatform.instance.blockApps(identifiers);
  }

  /// Unblocks the apps identified by the provided [identifiers].
  Future<void> unblockApps(List<String> identifiers) {
    return ZoAppBlockerPlatform.instance.unblockApps(identifiers);
  }

  /// Blocks all applications or app categories.
  Future<void> blockAll() {
    return ZoAppBlockerPlatform.instance.blockAll();
  }

  /// Unblocks all applications or app categories.
  Future<void> unblockAll() {
    return ZoAppBlockerPlatform.instance.unblockAll();
  }

  /// Initializes the app blocker with a custom block screen callback.
  ///
  /// The [blockScreenCallback] must be a **top-level or static function**
  /// annotated with `@pragma('vm:entry-point')`. It will be invoked in a
  /// separate Dart isolate by the background service when a blocked app is
  /// detected — even if the main Flutter app is closed.
  ///
  /// Inside this callback, call [ZoBlockScreenRunner.run] with a builder
  /// that returns your custom block screen widget:
  ///
  /// ```dart
  /// @pragma('vm:entry-point')
  /// void onBlockScreenRequested() {
  ///   ZoBlockScreenRunner.run(
  ///     builder: (context) => MyCustomBlockScreen(context: context),
  ///   );
  /// }
  ///
  /// // At app startup:
  /// ZoAppBlocker.instance.initialize(
  ///   blockScreenCallback: onBlockScreenRequested,
  /// );
  /// ```
  ///
  /// If [initialize] is never called, the package falls back to the native
  /// block screen controlled by [setBlockScreenConfig].
  Future<void> initialize({required Function blockScreenCallback}) async {
    final handle = PluginUtilities.getCallbackHandle(
      blockScreenCallback as void Function(),
    );
    if (handle == null) {
      throw ArgumentError(
        'The blockScreenCallback must be a top-level or static function. '
        'Instance methods and closures are not supported.',
      );
    }
    return ZoAppBlockerPlatform.instance
        .saveBlockScreenCallbackHandle(handle.toRawHandle());
  }

  /// Customizes the visual appearance of the background service notification.
  ///
  /// *   [notificationBannerTitle], [notificationBannerDescription]: The text displayed on the persistent
  ///     Foreground Service notification (Android only).
  /// *   [notificationIcon]: To set a custom notification icon, place your icon in the
  ///     `android/app/src/main/res/drawable` folder (e.g., `my_custom_icon.png`) and pass
  ///     its name without the extension (e.g., 'my_custom_icon'). If not provided, the default
  ///     application icon is used.
  Future<void> setNotificationConfig({
    String notificationBannerTitle = 'App Blocker Active',
    String notificationBannerDescription =
        'Monitoring and blocking restricted apps.',
    String? notificationIcon,
  }) {
    final config = {
      'notificationBannerTitle': notificationBannerTitle,
      'notificationBannerDescription': notificationBannerDescription,
    };
    if (notificationIcon != null) {
      config['notificationIcon'] = notificationIcon;
    }
    return ZoAppBlockerPlatform.instance.setNotificationConfig(config);
  }

  /// Retrieves the history of blocked activities.
  ///
  /// Returns a list of maps containing 'packageName' (String) and 'timestamp' (int) in milliseconds.
  Future<List<Map<String, dynamic>>> getBlockActivityLog() {
    return ZoAppBlockerPlatform.instance.getBlockActivityLog();
  }

  /// Clears the history of blocked activities.
  Future<void> clearBlockActivityLog() {
    return ZoAppBlockerPlatform.instance.clearBlockActivityLog();
  }

  // ---------------------------------------------------------------------------
  // Daily Time Limit API
  // ---------------------------------------------------------------------------

  /// Sets a daily usage time limit for a specific app.
  ///
  /// Once the user has spent [dailyLimitMinutes] minutes in [packageName] today,
  /// the app is automatically blocked for the remainder of the day. The counter
  /// resets to zero at midnight.
  ///
  /// The foreground service notification updates in real-time while the app is
  /// open, showing the remaining budget (e.g. `"Instagram — 47 min 23 sec remaining today"`).
  ///
  /// Calling this method again for the same [packageName] overwrites the limit
  /// and resets today's usage to zero.
  ///
  /// Example:
  /// ```dart
  /// await ZoAppBlocker.instance.setAppTimeLimit(
  ///   packageName: 'com.instagram.android',
  ///   dailyLimitMinutes: 50,
  /// );
  /// ```
  Future<void> setAppTimeLimit({
    required String packageName,
    required int dailyLimitMinutes,
  }) {
    return ZoAppBlockerPlatform.instance.setAppTimeLimit(
      packageName: packageName,
      dailyLimitMinutes: dailyLimitMinutes,
    );
  }

  /// Removes a previously configured daily time limit for [packageName].
  ///
  /// If the app was automatically blocked because its budget was exhausted,
  /// calling this method also unblocks it immediately.
  ///
  /// Example:
  /// ```dart
  /// await ZoAppBlocker.instance.removeAppTimeLimit('com.instagram.android');
  /// ```
  Future<void> removeAppTimeLimit(String packageName) {
    return ZoAppBlockerPlatform.instance.removeAppTimeLimit(packageName);
  }

  /// Returns all configured daily time limits with their current usage stats.
  ///
  /// Each [AppTimeLimit] contains:
  /// - [AppTimeLimit.packageName] — the app's package name
  /// - [AppTimeLimit.dailyLimitMinutes] — the configured cap
  /// - [AppTimeLimit.usedMinutes] — minutes spent today
  /// - [AppTimeLimit.remainingMinutes] — minutes remaining today
  /// - [AppTimeLimit.isExhausted] — whether the budget is fully used
  /// - [AppTimeLimit.usageRatio] — `usedSeconds / dailyLimitSeconds` (0.0–1.0)
  ///
  /// Example:
  /// ```dart
  /// final limits = await ZoAppBlocker.instance.getAppTimeLimits();
  /// for (final limit in limits) {
  ///   print('${limit.packageName}: ${limit.remainingMinutes} min left');
  /// }
  /// ```
  Future<List<AppTimeLimit>> getAppTimeLimits() async {
    final raw = await ZoAppBlockerPlatform.instance.getAppTimeLimits();
    return raw.map(AppTimeLimit.fromMap).toList();
  }

  /// Manually resets today's usage counter for [packageName] to zero.
  ///
  /// If the app was automatically blocked due to limit exhaustion, it is
  /// immediately unblocked and becomes accessible again.
  ///
  /// Useful for "give me one more hour" flows or parental override scenarios.
  ///
  /// Example:
  /// ```dart
  /// await ZoAppBlocker.instance.resetAppUsage('com.instagram.android');
  /// ```
  Future<void> resetAppUsage(String packageName) {
    return ZoAppBlockerPlatform.instance.resetAppUsage(packageName);
  }
}
