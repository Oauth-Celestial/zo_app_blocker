import 'package:plugin_platform_interface/plugin_platform_interface.dart';
import 'app_info.dart';
import 'zo_app_blocker_method_channel.dart';

abstract class ZoAppBlockerPlatform extends PlatformInterface {
  ZoAppBlockerPlatform() : super(token: _token);

  static final Object _token = Object();

  static ZoAppBlockerPlatform _instance = MethodChannelZoAppBlocker();

  static ZoAppBlockerPlatform get instance => _instance;

  static set instance(ZoAppBlockerPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<String> checkPermission() {
    throw UnimplementedError('checkPermission() has not been implemented.');
  }

  Future<void> requestPermission() {
    throw UnimplementedError('requestPermission() has not been implemented.');
  }

  Future<List<Map<String, dynamic>>> getApps() {
    throw UnimplementedError('getApps() has not been implemented.');
  }

  Future<List<Map<String, dynamic>>> getBlockedApps() {
    throw UnimplementedError('getBlockedApps() has not been implemented.');
  }

  Future<void> blockApps(List<String> identifiers) {
    throw UnimplementedError('blockApps() has not been implemented.');
  }

  Future<void> unblockApps(List<String> identifiers) {
    throw UnimplementedError('unblockApps() has not been implemented.');
  }

  Future<void> blockAll() {
    throw UnimplementedError('blockAll() has not been implemented.');
  }

  Future<void> unblockAll() {
    throw UnimplementedError('unblockAll() has not been implemented.');
  }

  Future<void> setBlockScreenConfig(Map<String, String> config) {
    throw UnimplementedError('setBlockScreenConfig() has not been implemented.');
  }
}
