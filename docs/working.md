# How the Customizable Block Screen Works

This document explains the technical architecture behind the customizable block screen feature in `zo_app_blocker`.

## The Core Challenge

The fundamental constraint we faced was: **Flutter cannot normally render UI when its application is closed or running in the background.** 
When a user launches a blocked app (like Instagram), the `zo_app_blocker` Flutter application is almost certainly in the background (and potentially killed by the Android OS to save memory). 

This is why the original version of this package used a native Android `LinearLayout` built entirely in Kotlin. However, a native layout is extremely rigid and difficult for a Flutter developer to customize.

## The Solution: Headless Flutter Engines

The solution leverages the fact that the Android **Background Service** (which detects when apps are launched) runs independently of the main Flutter UI. 

We can programmatically instruct this background service to spin up a *headless* Flutter engine, attach an invisible Flutter window to it, and render it over the screen.

Here is the exact step-by-step flow of how it works:

### 1. The Callback Handle Registration
When you call `ZoAppBlocker.instance.initialize(blockScreenCallback: myCallback)` in your main app:
- We use Flutter's `PluginUtilities.getCallbackHandle()` to get a unique integer ID (a "handle") that represents your Dart function.
- We send this handle over a MethodChannel to the native Android side.
- The Android side saves this handle permanently in `SharedPreferences`.

### 2. Pre-warming the Engine (Optimization)
To ensure the block screen appears instantly, we don't wait for the user to open a blocked app.
- When the `AppBlockerForegroundService` starts up, it checks if a callback handle exists in `SharedPreferences`.
- If it does, it immediately creates a new `FlutterEngine` and boots it up using that callback handle.
- This creates an isolated Dart environment in the background that is paused and waiting for instructions.

### 3. The Instant Native Block (Anti-Flicker)
When the user launches a blocked app (e.g., Instagram):
- The `AppBlockerAccessibilityService` detects the launch within milliseconds.
- Before doing anything else, it fires `GLOBAL_ACTION_HOME`. This instantly throws the user back to the home screen, preventing them from interacting with the blocked app while the Flutter UI prepares to render.

### 4. The Flutter Overlay Takes Over
Immediately after sending the user home:
- The Android service takes the pre-warmed `FlutterEngine` and attaches a `FlutterView` to it.
- It adds this `FlutterView` to the Android `WindowManager` as a `TYPE_ACCESSIBILITY_OVERLAY`. This is a special system window that draws over everything else.
- It sends an `onAppBlocked` event via a dedicated MethodChannel to the background Dart isolate, passing along the app's package name, readable name, and icon bytes.

### 5. Flutter Renders Your UI
- Inside the background Dart isolate, `ZoBlockScreenRunner` receives the `onAppBlocked` event.
- It constructs a `BlockScreenContext` object containing the app details.
- It executes your custom builder function `(context) => MyCustomBlockScreen(...)`.
- It calls `runApp()` with your widget.
- The `FlutterEngine` renders your widget into the `FlutterView` overlay window on the screen.

### 6. Two-Way Communication
Your custom Flutter UI is now visible to the user.
- When the user taps your custom "Exit" button, it calls `context.onDismiss()`.
- This fires a MethodChannel call back to the native Android service telling it to remove the `FlutterView` from the WindowManager.
- If the user taps a custom "Unlock" button, it calls `context.onRequestUnlock()`. This also fires a MethodChannel call to the Android service, which temporarily removes the overlay *without* sending the user to the home screen, granting them access to the app underneath.

## Isolation Constraints (Important)

Because the block screen runs in a completely separate `FlutterEngine` spawned by the background service, **it does not share memory with your main Flutter app.**
This means:
1. Static variables, singletons, and state (like Riverpod/Provider providers) from your main app will be completely empty/reset inside the block screen builder.
2. If you need to share data (like user preferences, coin balances for unlocking, etc.) between your main app and the block screen, you **must** use persistent local storage (like `shared_preferences`, `sqflite`, or `hive`) or make network requests.

## The MethodChannel Architecture

Because we are dealing with two separate Flutter environments (the main app and the headless block screen), the package utilizes **two distinct MethodChannels** to prevent event crossover.

### 1. The Main Channel (`zo_app_blocker`)
This channel is used by your main Flutter app to configure the plugin and manage permissions.
*   **Flutter → Native:**
    *   `saveBlockScreenCallbackHandle`: Sends the unique integer ID of your builder function to Android so it can be saved in SharedPreferences.
    *   `blockApps`, `unblockApps`, `setBlockScreenConfig`, etc.

### 2. The Block Screen Channel (`zo_app_blocker_block_screen`)
This channel is created and used **exclusively** by the headless `FlutterEngine` running inside the Android background service. It handles the lifecycle and interactions of the block screen overlay.

Here is the exact flow of messages on this channel when an app is blocked:

1.  **Dart → Native (`blockScreenReady`)**:
    When the headless engine boots, `ZoBlockScreenRunner` tells Android: *"I am awake and ready to receive data."*
2.  **Native → Dart (`onAppBlocked`)**:
    Android responds by sending a payload containing the `packageName`, `appName`, and `appIcon` bytes. Dart uses this to construct the `BlockScreenContext` and render your UI.
3.  **Dart → Native (User Action)**:
    When the user taps a button on your custom block screen, Dart sends one of two commands back to Android:
    *   `dismissBlockScreen`: Android removes the `FlutterView` overlay and fires `GLOBAL_ACTION_HOME` to send the user back to their launcher.
    *   `requestUnlock`: Android removes the `FlutterView` overlay but **does not** send the user home, effectively granting them access to the app underneath.

## Understanding `@pragma('vm:entry-point')`

In the block screen implementation, you must annotate your custom builder callback with `@pragma('vm:entry-point')`. This is an instruction to the Dart compiler to prevent **tree-shaking**.

### The Tree-Shaking Problem
When you build a Flutter app for release (APK or AAB), the Dart compiler analyzes all your code. To keep the app size as small as possible, it looks for any code or functions that are **never called** from your `main()` function.

If it determines a function is never called, it completely deletes (or "shakes") that code out of the final compiled app. Because your block screen builder is called by the **Native Android Kotlin Code** (the background service) and not directly from your Dart `main()` function, the compiler assumes it is dead code and will delete it.

If the function gets deleted, the Android background service will try to boot the FlutterEngine, look for your function handle, fail to find it, and crash.

### The Solution
By adding this annotation right above your function:

```dart
@pragma('vm:entry-point')
void onBlockScreenRequested() { ... }
```

You are explicitly telling the Dart compiler: **"Do not delete this function during tree-shaking. I promise it is an entry-point that will be called externally by the Virtual Machine."** This guarantees the function is preserved in the compiled app and available when the background service needs it.
