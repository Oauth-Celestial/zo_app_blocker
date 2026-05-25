import 'dart:io';
import 'package:flutter/material.dart';
import 'dart:typed_data';
import 'package:zo_app_blocker/zo_app_blocker.dart';

@pragma('vm:entry-point')
void onBlockScreenRequested() {
  ZoBlockScreenRunner.run(
    builder: (context) {
      return Scaffold(
        backgroundColor: Colors.black87,
        body: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              if (context.appIcon != null)
                Image.memory(
                  context.appIcon!,
                  width: 100,
                  height: 100,
                ),
              const SizedBox(height: 24),
              Text(
                '${context.appName ?? 'App'} is Blocked!',
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 24,
                  fontWeight: FontWeight.bold,
                ),
              ),
              const SizedBox(height: 12),
              const Text(
                'You have customized this screen using Flutter.',
                style: TextStyle(color: Colors.white70),
              ),
              const SizedBox(height: 48),
              ElevatedButton.icon(
                onPressed: context.onDismiss,
                icon: const Icon(Icons.exit_to_app),
                label: const Text('Exit'),
              ),
              const SizedBox(height: 16),
              OutlinedButton.icon(
                onPressed: () async {
                  // In a real app, you would check coins here
                  // and deduct them if sufficient.
                  
                  // Unlock for 15 minutes
                  final granted = await context.onRequestUnlock?.call(
                        duration: const Duration(minutes: 15),
                      ) ??
                      false;
                  if (!granted) {
                    // Could show a snackbar or dialog if unlock failed
                  }
                },
                icon: const Icon(Icons.lock_open),
                label: const Text('Unlock temporarily (Cost: 50 coins)'),
                style: OutlinedButton.styleFrom(
                  foregroundColor: Colors.white,
                  side: const BorderSide(color: Colors.white70),
                ),
              ),
            ],
          ),
        ),
      );
    },
  );
}

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  ZoAppBlocker.instance.initialize(
    blockScreenCallback: onBlockScreenRequested,
  );
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
    
    // Fallback native configuration (used if blockScreenCallback is not set)
    _zoAppBlockerPlugin.setBlockScreenConfig(
      backgroundColor: '#000000',
      title: 'Stop Right There!',
      titleColor: '#FFFFFF',
      description: 'You blocked this app. Get back to work!',
      descriptionColor: '#DDDDDD',
    );
  }

  Future<void> _checkPermission() async {
    final status = await _zoAppBlockerPlugin.checkAccessibilityPermission();
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
      await _zoAppBlockerPlugin.requestNotificationPermission();
      final notifStatus = await _zoAppBlockerPlugin.checkNotificationPermission();
      if (notifStatus != 'granted') {
        if (!mounted) return;
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text(
              'Notification permission is required for the background service.',
            ),
          ),
        );
      }
    }
    await _zoAppBlockerPlugin.requestAccessibilityPermission();
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
                      leading: FutureBuilder<Uint8List?>(
                        future: _zoAppBlockerPlugin.getAppIcon(app['packageName'] ?? ''),
                        builder: (context, snapshot) {
                          if (snapshot.connectionState == ConnectionState.waiting) {
                            return const SizedBox(width: 40, height: 40, child: Padding(padding: EdgeInsets.all(8.0), child: CircularProgressIndicator(strokeWidth: 2)));
                          }
                          if (snapshot.hasData && snapshot.data != null) {
                            return Image.memory(snapshot.data!, width: 40, height: 40);
                          }
                          return const Icon(Icons.android, size: 40);
                        },
                      ),
                      title: Text(app['appName'] ?? ''),
                      subtitle: Text(app['packageName'] ?? ''),
                      onTap: () async {
                        final nav = Navigator.of(context);
                        final scaffold = ScaffoldMessenger.of(context);
                        await _zoAppBlockerPlugin.blockApps([
                          app['packageName'],
                        ]);
                        if (!mounted) return;
                        nav.pop();
                        scaffold.showSnackBar(
                          SnackBar(
                            content: Text('Blocked ${app['appName']}'),
                          ),
                        );
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
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text('Error selecting apps: $e')));
    }
  }

  Future<void> _showActivityLog() async {
    final log = await _zoAppBlockerPlugin.getBlockActivityLog();
    if (!mounted) return;

    showModalBottomSheet(
      context: context,
      builder: (context) {
        return Column(
          children: [
            Padding(
              padding: const EdgeInsets.all(16.0),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  const Text(
                    'Block Activity Log',
                    style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
                  ),
                  TextButton(
                    onPressed: () async {
                      await _zoAppBlockerPlugin.clearBlockActivityLog();
                      if (context.mounted) {
                        Navigator.pop(context);
                        _showActivityLog();
                      }
                    },
                    child: const Text('Clear'),
                  ),
                ],
              ),
            ),
            Expanded(
              child: log.isEmpty
                  ? const Center(child: Text('No activity yet.'))
                  : ListView.builder(
                      itemCount: log.length,
                      itemBuilder: (context, index) {
                        final entry = log[index];
                        final packageName = entry['packageName'] as String;
                        final timestamp = entry['timestamp'] as int;
                        final date = DateTime.fromMillisecondsSinceEpoch(timestamp);
                        return ListTile(
                          leading: FutureBuilder<Uint8List?>(
                            future: _zoAppBlockerPlugin.getAppIcon(packageName),
                            builder: (context, snapshot) {
                              if (snapshot.connectionState == ConnectionState.waiting) {
                                return const SizedBox(width: 32, height: 32, child: Padding(padding: EdgeInsets.all(4.0), child: CircularProgressIndicator(strokeWidth: 2)));
                              }
                              if (snapshot.hasData && snapshot.data != null) {
                                return Image.memory(snapshot.data!, width: 32, height: 32);
                              }
                              return const Icon(Icons.block, size: 32, color: Colors.grey);
                            },
                          ),
                          title: Text(packageName),
                          subtitle: Text('${date.toLocal()}'.split('.')[0]),
                        );
                      },
                    ),
            ),
          ],
        );
      },
    );
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
                final scaffold = ScaffoldMessenger.of(context);
                await _zoAppBlockerPlugin.unblockAll();
                if (!mounted) return;
                scaffold.showSnackBar(
                  const SnackBar(content: Text('All apps unblocked')),
                );
                _loadBlockedApps();
              },
              child: const Text('Unblock All Apps'),
            ),
            const SizedBox(height: 24),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                const Text(
                  'Currently Blocked Apps:',
                  style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
                ),
                TextButton(
                  onPressed: _showActivityLog,
                  child: const Text('Activity Log'),
                ),
              ],
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
                          leading: FutureBuilder<Uint8List?>(
                            future: _zoAppBlockerPlugin.getAppIcon(app['packageName'] ?? ''),
                            builder: (context, snapshot) {
                              if (snapshot.connectionState == ConnectionState.waiting) {
                                return const SizedBox(width: 40, height: 40, child: Padding(padding: EdgeInsets.all(8.0), child: CircularProgressIndicator(strokeWidth: 2)));
                              }
                              if (snapshot.hasData && snapshot.data != null) {
                                return Image.memory(snapshot.data!, width: 40, height: 40);
                              }
                              return const Icon(Icons.block, color: Colors.red, size: 40);
                            },
                          ),
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
