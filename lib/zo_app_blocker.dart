import 'app_info.dart';
import 'zo_app_blocker_platform_interface.dart';

export 'app_info.dart';

/// The main entry point for the Zo App Blocker plugin.
///
/// Use [ZoAppBlocker.instance] to access the singleton instance and invoke
/// app blocking methods.
class ZoAppBlocker {
  ZoAppBlocker._();

  /// The singleton instance of [ZoAppBlocker].
  static final ZoAppBlocker instance = ZoAppBlocker._();

  /// Checks the current status of the required permissions.
  ///
  /// On Android, this checks if the Accessibility Service is enabled.
  /// On iOS, this checks Family Controls authorization.
  /// Returns a [String] representing the status (e.g., 'granted', 'denied').
  Future<String> checkPermission() {
    return ZoAppBlockerPlatform.instance.checkPermission();
  }

  /// Requests the necessary permissions to block apps.
  ///
  /// On Android, this opens the device's Accessibility Settings page so the user
  /// can enable the service.
  /// On iOS, this prompts the user for Family Controls authorization.
  Future<void> requestPermission() {
    return ZoAppBlockerPlatform.instance.requestPermission();
  }

  /// Retrieves a list of apps that can be blocked.
  ///
  /// On Android, this returns a list of installed packages.
  /// On iOS, this opens the native `FamilyActivityPicker` and returns the
  /// opaque tokens for the selected apps/categories.
  Future<List<Map<String, dynamic>>> getApps() {
    return ZoAppBlockerPlatform.instance.getApps();
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

  /// Customizes the visual appearance of the native block screen and the
  /// background service notification.
  ///
  /// *   [backgroundColor], [titleColor], [descriptionColor]: Colors in Hex format (e.g. '#FF0000').
  /// *   [title], [description]: The text displayed on the overlay when an app is blocked.
  /// *   [notificationTitle], [notificationDescription]: The text displayed on the persistent
  ///     Foreground Service notification (Android only).
  Future<void> setBlockScreenConfig({
    String backgroundColor = '#FFFFFF',
    String title = 'App Blocked',
    String titleColor = '#000000',
    String description = 'This app is blocked by Zo App Blocker.',
    String descriptionColor = '#555555',
    String notificationTitle = 'Zo App Blocker Active',
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
