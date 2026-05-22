# zo_app_blocker

[![Pub Version](https://img.shields.io/pub/v/zo_app_blocker?color=blue)](https://pub.dev/packages/zo_app_blocker)
[![License: MIT](https://img.shields.io/badge/License-MIT-purple.svg)](https://opensource.org/licenses/MIT)

A Flutter plugin to block specific applications on Android.

Under the hood, it leverages Android's `AccessibilityService` and `ForegroundService`. This ensures that the app blocking mechanism remains persistent and active, even if the user swipes your Flutter app away from their recent apps list.

> **Note:** This package currently supports **Android only**.

---

## Features

* **Targeted Blocking:** Specify exactly which installed applications should be blocked from launching.
* **Persistent Execution:** Utilizes a Foreground Service to ensure the background blocking process is not killed by the system.
* **App Discovery:** Easily fetch a list of all installed applications on the device.

## Get Started

Add `zo_app_blocker` to your `pubspec.yaml`:

```yaml
dependencies:
  zo_app_blocker: ^latest_version
```

Or install it via the command line:

```bash
flutter pub add zo_app_blocker
```

## Setup & Permissions (Android)

To get this plugin working, several Android permissions need to be handled.

### 1. AndroidManifest.xml

Ensure you add the following permissions to your app's `android/app/src/main/AndroidManifest.xml` file, directly inside the `<manifest>` tag:

```xml
    <!-- Allows querying installed packages -->
    <uses-permission
        android:name="android.permission.QUERY_ALL_PACKAGES"
        tools:ignore="QueryAllPackagesPermission" />

    <!-- Required for the background monitoring service -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
    
    <!-- Required to show notifications on Android 13+ -->
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

```

### 2. Notification Permission (Android 13+)

If you're targeting Android 13 (API level 33) or higher, you must ask the user for permission to show notifications before you can start the background service. You can use the plugin's built-in methods:

```dart
import 'package:zo_app_blocker/zo_app_blocker.dart';

Future<void> handleNotificationPermission() async {
  final status = await ZoAppBlocker.instance.checkNotificationPermission();
  if (status != 'granted') {
    await ZoAppBlocker.instance.requestNotificationPermission();
  }
}
```

### 3. Accessibility Permission

For the plugin to actually detect when a blocked app is opened, the user has to go into their Android settings and enable Accessibility for your app. You can check the status and prompt them to open settings like this:

```dart
import 'package:zo_app_blocker/zo_app_blocker.dart';

// ...

final status = await ZoAppBlocker.instance.checkAccessibilityPermission();
if (status == 'denied') {
  await ZoAppBlocker.instance.requestAccessibilityPermission(); // Takes them to Android settings
}
```

## Usage

### 1. Style your block screen

Before you block anything, you may want to customize the screen that shows up. You can also customize the background notification here.

```dart
import 'package:zo_app_blocker/zo_app_blocker.dart';

// ...

await ZoAppBlocker.instance.setBlockScreenConfig(
  backgroundColor: '#F44336', // Hex color strings
  title: 'Stop Right There!',
  titleColor: '#FFFFFF',
  description: 'You blocked this app. Get back to work!',
  descriptionColor: '#DDDDDD',
  notificationTitle: 'Focus Mode Active', 
  notificationDescription: 'Monitoring your apps in the background.'
);
```

### 2. Get a list of installed apps

If you need to show the user a list of apps they can block, you can fetch all installed apps:

```dart
import 'package:zo_app_blocker/zo_app_blocker.dart';

// ...

final installedApps = await ZoAppBlocker.instance.getApps();
// Returns a list containing maps, e.g.:
// [{"packageName": "com.facebook.katana", "appName": "Facebook"}, ...]
```

### 3. Start blocking

Just pass a list of package names to the plugin. Once you call this, the Foreground Service starts up automatically.

```dart
import 'package:zo_app_blocker/zo_app_blocker.dart';

// ...

await ZoAppBlocker.instance.blockApps([
  'com.instagram.android',
  'com.facebook.katana',
]);
```

### 4. Unblock apps

You can remove specific apps from the blocklist, or just unblock everything (which also stops the background service).

```dart
import 'package:zo_app_blocker/zo_app_blocker.dart';

// ...

// Unblock specific ones
await ZoAppBlocker.instance.unblockApps(['com.facebook.katana']);

// Unblock everything and stop the service
await ZoAppBlocker.instance.unblockAll();
```
