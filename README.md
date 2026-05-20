# Zo App Blocker

A powerful Flutter plugin for blocking applications on both Android and iOS. This plugin uses native APIs (`AccessibilityService` and `ForegroundService` on Android, `FamilyControls` and `ManagedSettings` on iOS) to reliably block user-selected applications, even when your Flutter app is completely killed.

## Features

*   **Cross-Platform App Blocking:** Prevent users from opening specific apps or specific app categories.
*   **Android Background Persistence:** Uses a robust Foreground Service and Accessibility Service to guarantee that app blocking stays active even if the device reboots or your Flutter app is swiped away from recents.
*   **iOS Screen Time Integration:** Native iOS 16.0+ implementation using Apple's official Screen Time API (`FamilyControls`).
*   **Customizable Block Screen (Android):** Design the full-screen overlay that appears when a user tries to open a blocked app directly from your Dart code.

---

## 🚀 Setup & Installation

### Android Setup

1.  **Permissions:** The plugin automatically adds the required `QUERY_ALL_PACKAGES`, `FOREGROUND_SERVICE`, `POST_NOTIFICATIONS`, and `BIND_ACCESSIBILITY_SERVICE` permissions. However, starting from Android 13 (API 33), you **must** request the notification permission at runtime so the Foreground Service notification can be displayed. You can use a package like `permission_handler` to request this before blocking apps:
    ```dart
    import 'package:permission_handler/permission_handler.dart';
    
    Future<void> requestPermissions() async {
      if (await Permission.notification.isDenied) {
        await Permission.notification.request();
      }
    }
    ```

2.  **Accessibility Service:** For the plugin to work, the user must explicitly enable your app in their Android Accessibility Settings. The plugin provides a method to check and request this:
    ```dart
    final status = await ZoAppBlocker.instance.checkPermission();
    if (status == 'denied') {
      await ZoAppBlocker.instance.requestPermission(); // Opens Android settings
    }
    ```

3.  **Foreground Service Notification:** Once `blockApps()` is called, a Foreground Service will automatically start and display a persistent notification. This guarantees that Android battery optimization does not kill the accessibility service when the user closes your app.

### iOS Setup (Requires iOS 16.0+)

1.  **Capabilities:** Open your project in Xcode. Go to your target's **Signing & Capabilities** tab and add the **Family Controls** capability.
2.  **Info.plist:** Add a reason for using Family Controls to your `ios/Runner/Info.plist`:
    ```xml
    <key>NSFamilyControlsUsageDescription</key>
    <string>We need Screen Time access to block distracting apps.</string>
    ```
3.  **Authorization:** You must request Family Controls authorization before using the plugin.
    ```dart
    final status = await ZoAppBlocker.instance.checkPermission();
    if (status == 'denied') {
      await ZoAppBlocker.instance.requestPermission();
    }
    ```

---

## 🛠 Usage

### 1. Customizing the Block Screen (Android Only)

Before blocking apps, customize the instant native overlay that appears over blocked apps:

```dart
await ZoAppBlocker.instance.setBlockScreenConfig(
  backgroundColor: '#FF0000',
  title: 'Stop Right There!',
  titleColor: '#FFFFFF',
  description: 'You blocked this app. Get back to work!',
  descriptionColor: '#DDDDDD',
);
```

### 2. Selecting Apps to Block

The plugin provides a built-in UI for selecting apps:
*   **Android:** Returns a list of installed apps.
*   **iOS:** Displays the native SwiftUI `FamilyActivityPicker`.

```dart
final selectedApps = await ZoAppBlocker.instance.getApps();
// Returns a list of maps, e.g. [{"packageName": "com.facebook.katana", "appName": "Facebook"}]
```

### 3. Blocking Apps

Pass the `packageName` (or the iOS opaque tokens returned from `getApps()`) to the blocker:

```dart
final identifiersToBlock = selectedApps.map((app) => app['packageName']!).toList();
await ZoAppBlocker.instance.blockApps(identifiersToBlock);
```

### 4. Unblocking Apps

```dart
await ZoAppBlocker.instance.unblockApps(identifiersToBlock);

// Or unblock everything:
await ZoAppBlocker.instance.unblockAll();
```

## Note on App Kills & Background Behavior
*   **Android:** Thanks to the Foreground Service, if the user swipes away your Flutter app, the block configuration remains active, and the custom native overlay will appear instantly.
*   **iOS:** Apple's `ManagedSettingsStore` natively persists the block selection across reboots and app kills.
