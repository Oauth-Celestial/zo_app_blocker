import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'app_info.dart';
import 'zo_app_blocker_platform_interface.dart';

class MethodChannelZoAppBlocker extends ZoAppBlockerPlatform {
  @visibleForTesting
  final methodChannel = const MethodChannel('zo_app_blocker');

  @override
  Future<String> checkPermission() async {
    final result = await methodChannel.invokeMethod<String>('checkPermission');
    return result ?? 'denied';
  }

  @override
  Future<void> requestPermission() async {
    await methodChannel.invokeMethod<void>('requestPermission');
  }

  @override
  Future<List<Map<String, dynamic>>> getApps() async {
    final List<dynamic>? result = await methodChannel.invokeMethod('getApps');
    if (result == null) return [];
    return result.map((e) => Map<String, dynamic>.from(e as Map)).toList();
  }

  @override
  Future<List<Map<String, dynamic>>> getBlockedApps() async {
    final List<dynamic>? result = await methodChannel.invokeMethod('getBlockedApps');
    if (result == null) return [];
    return result.map((e) => Map<String, dynamic>.from(e as Map)).toList();
  }

  @override
  Future<void> blockApps(List<String> identifiers) async {
    await methodChannel.invokeMethod<void>('blockApps', {'identifiers': identifiers});
  }

  @override
  Future<void> unblockApps(List<String> identifiers) async {
    await methodChannel.invokeMethod<void>('unblockApps', {'identifiers': identifiers});
  }

  @override
  Future<void> blockAll() async {
    await methodChannel.invokeMethod('blockAll');
  }

  @override
  Future<void> unblockAll() async {
    await methodChannel.invokeMethod('unblockAll');
  }

  @override
  Future<void> setBlockScreenConfig(Map<String, String> config) async {
    await methodChannel.invokeMethod('setBlockScreenConfig', config);
  }
}
