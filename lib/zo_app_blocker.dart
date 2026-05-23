import 'dart:typed_data';
import 'dart:ui';

import 'zo_app_blocker_platform_interface.dart';

export 'app_info.dart';
export 'src/block_screen_context.dart';
export 'src/zo_block_screen_runner.dart';

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

  /// Customizes the visual appearance of the native block screen and the
  /// background service notification.
  ///
  /// This configures the **native fallback** block screen that is used when
  /// no custom [initialize] callback has been registered. If a custom callback
  /// is registered, this config is ignored for the overlay but still used for
  /// the foreground service notification.
  ///
  /// *   [backgroundColor], [titleColor], [descriptionColor]: Colors in Hex format (e.g. '#FF0000').
  /// *   [title], [description]: The text displayed on the overlay when an app is blocked.
  /// *   [notificationTitle], [notificationDescription]: The text displayed on the persistent
  ///     Foreground Service notification (Android only).
  Future<void> setBlockScreenConfig({
    String backgroundColor = '#FFFFFF',
    String title = 'App Blocked',
    String titleColor = '#000000',
    String description = 'This app is blocked.',
    String descriptionColor = '#555555',
    String notificationTitle = 'App Blocker Active',
    String notificationDescription = 'Monitoring and blocking restricted apps.',
  }) {
    return ZoAppBlockerPlatform.instance.setBlockScreenConfig({
      'backgroundColor': backgroundColor,
      'title': title,
      'titleColor': titleColor,
      'description': description,
      'descriptionColor': descriptionColor,
      'notificationTitle': notificationTitle,
      'notificationDescription': notificationDescription,
    });
  }
}
