import 'dart:io';
import 'package:flutter/material.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:zo_app_blocker/zo_app_blocker.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return const MaterialApp(home: HomeScreen());
  }
}

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  final _zoAppBlockerPlugin = ZoAppBlocker.instance;
  String _permissionStatus = 'Unknown';
  List<Map<String, dynamic>> _blockedApps = [];

  @override
  void initState() {
    super.initState();
    _checkPermission();
    _loadBlockedApps();
    _zoAppBlockerPlugin.setBlockScreenConfig(
      backgroundColor: '#000000',
      title: 'Stop Right There!',
      titleColor: '#FFFFFF',
      description: 'You blocked this app. Get back to work!',
      descriptionColor: '#DDDDDD',
    );
  }

  Future<void> _checkPermission() async {
    final status = await _zoAppBlockerPlugin.checkPermission();
    setState(() {
      _permissionStatus = status;
    });
  }

  Future<void> _loadBlockedApps() async {
    final apps = await _zoAppBlockerPlugin.getBlockedApps();
    setState(() {
      _blockedApps = apps;
    });
  }

  Future<void> _requestPermissions() async {
    if (Platform.isAndroid) {
      final notifStatus = await Permission.notification.request();
      if (!notifStatus.isGranted) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text(
                'Notification permission is required for the background service.',
              ),
            ),
          );
        }
      }
    }
    await _zoAppBlockerPlugin.requestPermission();
    _checkPermission();
  }

  Future<void> _selectAndBlockApps() async {
    try {
      final apps = await _zoAppBlockerPlugin.getApps();

      if (Platform.isIOS) {
        // iOS handles the picker UI and blocking internally
        _loadBlockedApps();
        return;
      }

      if (apps.isEmpty) return;
      if (!mounted) return;

      showModalBottomSheet(
        context: context,
        builder: (context) {
          return Column(
            children: [
              const Padding(
                padding: EdgeInsets.all(16.0),
                child: Text(
                  'Select an app to block',
                  style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
                ),
              ),
              Expanded(
                child: ListView.builder(
                  itemCount: apps.length,
                  itemBuilder: (context, index) {
                    final app = apps[index];
                    return ListTile(
                      title: Text(app['appName'] ?? ''),
                      subtitle: Text(app['packageName'] ?? ''),
                      onTap: () async {
                        await _zoAppBlockerPlugin.blockApps([
                          app['packageName'],
                        ]);
                        if (mounted) {
                          Navigator.pop(context);
                          ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(
                              content: Text('Blocked ${app['appName']}'),
                            ),
                          );
                        }
                        _loadBlockedApps();
                      },
                    );
                  },
                ),
              ),
            ],
          );
        },
      );
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('Error selecting apps: $e')));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Zo App Blocker Example')),
      body: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(
              'Permission Status: $_permissionStatus',
              style: const TextStyle(fontSize: 18),
            ),
            const SizedBox(height: 16),
            ElevatedButton(
              onPressed: _requestPermissions,
              child: const Text('Request Permissions (Android/iOS)'),
            ),
            const SizedBox(height: 16),
            ElevatedButton(
              onPressed: _selectAndBlockApps,
              child: const Text('Select Apps to Block'),
            ),
            const SizedBox(height: 8),
            ElevatedButton(
              onPressed: () async {
                await _zoAppBlockerPlugin.unblockAll();
                if (mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(content: Text('All apps unblocked')),
                  );
                }
                _loadBlockedApps();
              },
              child: const Text('Unblock All Apps'),
            ),
            const SizedBox(height: 24),
            const Text(
              'Currently Blocked Apps:',
              style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 8),
            Expanded(
              child: _blockedApps.isEmpty
                  ? const Center(child: Text('No apps currently blocked.'))
                  : ListView.builder(
                      itemCount: _blockedApps.length,
                      itemBuilder: (context, index) {
                        final app = _blockedApps[index];
                        return ListTile(
                          leading: const Icon(Icons.block, color: Colors.red),
                          title: Text(app['appName'] ?? 'Unknown App'),
                          subtitle: Text(app['packageName'] ?? ''),
                          trailing: IconButton(
                            icon: const Icon(Icons.delete, color: Colors.grey),
                            onPressed: () async {
                              final packageName = app['packageName'] as String;
                              await _zoAppBlockerPlugin.unblockApps([
                                packageName,
                              ]);
                              _loadBlockedApps();
                            },
                          ),
                        );
                      },
                    ),
            ),
          ],
        ),
      ),
    );
  }
}
