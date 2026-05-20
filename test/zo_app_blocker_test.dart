import 'package:flutter_test/flutter_test.dart';
import 'package:zo_app_blocker/zo_app_blocker.dart';
import 'package:zo_app_blocker/zo_app_blocker_platform_interface.dart';
import 'package:zo_app_blocker/zo_app_blocker_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockZoAppBlockerPlatform
    with MockPlatformInterfaceMixin
    implements ZoAppBlockerPlatform {
  @override
  Future<String?> getPlatformVersion() => Future.value('42');
}

void main() {
  final ZoAppBlockerPlatform initialPlatform = ZoAppBlockerPlatform.instance;

  test('$MethodChannelZoAppBlocker is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelZoAppBlocker>());
  });

  test('getPlatformVersion', () async {
    ZoAppBlocker zoAppBlockerPlugin = ZoAppBlocker();
    MockZoAppBlockerPlatform fakePlatform = MockZoAppBlockerPlatform();
    ZoAppBlockerPlatform.instance = fakePlatform;

    expect(await zoAppBlockerPlugin.getPlatformVersion(), '42');
  });
}
