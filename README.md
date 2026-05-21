# zo_app_blocker

A Flutter plugin that lets you block apps on Android.

It uses Android's `AccessibilityService` and `ForegroundService` under the hood. This means the app blocking actually works consistently, even if the user swipes your Flutter app away from the recent apps list.

*Note: Right now, this package only supports Android.*

## What it does

* **Block specific apps:** Stop users from opening apps they shouldn't.
* **Stays alive:** Thanks to the Foreground Service, the blocking keeps working in the background.
* **Custom UI:** You can customize the full-screen overlay that pops up when a user tries to open a blocked app.
* **Custom Notification:** Set your own title and description for the persistent background notification.

## Setup & Permissions (Android)

To get this working, there are a few permissions you need to handle on Android.

### 1. AndroidManifest.xml

The plugin automatically adds most of what it needs to your manifest, but here's what it uses in case you're curious:

* `QUERY_ALL_PACKAGES` (to get the installed apps)
* `FOREGROUND_SERVICE` and `POST_NOTIFICATIONS` (to run in the background)
* `BIND_ACCESSIBILITY_SERVICE` (to detect when apps are launched)

### 2. Notification Permission (Android 13+)

If you're targeting Android 13 or higher, you have to ask the user for permission to show notifications before you can start the background service. You can use the `permission_handler` package for this:

```dart
import 'package:permission_handler/permission_handler.dart';

Future<void> requestPermissions() async {
  if (await Permission.notification.isDenied) {
    await Permission.notification.request();
  }
}
```

### 3. Accessibility Permission

For the plugin to actually detect when a blocked app is opened, the user has to go into their Android settings and enable Accessibility for your app. You can check the status and prompt them to open settings like this:

```dart
final status = await ZoAppBlocker.instance.checkPermission();
if (status == 'denied') {
  await ZoAppBlocker.instance.requestPermission(); // Takes them to Android settings
}
```

## How to use it

### 1. Style your block screen

Before you block anything, you probably want to customize the screen that shows up. You can also customize the background notification here.

```dart
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
final installedApps = await ZoAppBlocker.instance.getApps();
// Returns something like: [{"packageName": "com.facebook.katana", "appName": "Facebook"}, ...]
```

### 3. Start blocking

Just pass a list of package names to the plugin. Once you call this, the Foreground Service starts up automatically.

```dart
await ZoAppBlocker.instance.blockApps([
   
  'com.instagram.android'
]);
```

### 4. Unblock apps

You can remove specific apps from the blocklist, or just unblock everything (which also stops the background service).

```dart
// Unblock specific ones
await ZoAppBlocker.instance.unblockApps(['com.facebook.katana']);

// Unblock everything and stop the service
await ZoAppBlocker.instance.unblockAll();
```
