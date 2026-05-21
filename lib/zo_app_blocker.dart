import 'app_info.dart';
import 'zo_app_blocker_platform_interface.dart';

export 'app_info.dart';

class ZoAppBlocker {
  ZoAppBlocker._();
  static final ZoAppBlocker instance = ZoAppBlocker._();

  Future<String> checkPermission() {
    return ZoAppBlockerPlatform.instance.checkPermission();
  }

  Future<void> requestPermission() {
    return ZoAppBlockerPlatform.instance.requestPermission();
  }

  Future<List<Map<String, dynamic>>> getApps() {
    return ZoAppBlockerPlatform.instance.getApps();
  }

  Future<List<Map<String, dynamic>>> getBlockedApps() {
    return ZoAppBlockerPlatform.instance.getBlockedApps();
  }

  Future<void> blockApps(List<String> identifiers) {
    return ZoAppBlockerPlatform.instance.blockApps(identifiers);
  }

  Future<void> unblockApps(List<String> identifiers) {
    return ZoAppBlockerPlatform.instance.unblockApps(identifiers);
  }

  Future<void> blockAll() {
    return ZoAppBlockerPlatform.instance.blockAll();
  }

  Future<void> unblockAll() {
    return ZoAppBlockerPlatform.instance.unblockAll();
  }

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
