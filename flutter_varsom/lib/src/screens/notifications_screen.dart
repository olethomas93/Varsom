import 'package:flutter/material.dart';

import '../services/notification_service.dart';
import '../services/preferences_service.dart';

class NotificationsScreen extends StatefulWidget {
  const NotificationsScreen({
    required this.preferences,
    super.key,
  });

  final PreferencesService preferences;

  @override
  State<NotificationsScreen> createState() => _NotificationsScreenState();
}

class _NotificationsScreenState extends State<NotificationsScreen> {
  bool _enabled = false;
  TimeOfDay _time = const TimeOfDay(hour: 8, minute: 0);
  bool _loaded = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final enabled = await widget.preferences.notificationsEnabled();
    final time = await widget.preferences.notificationTime();
    if (!mounted) return;
    setState(() {
      _enabled = enabled;
      _time = TimeOfDay(hour: time.hour, minute: time.minute);
      _loaded = true;
    });
  }

  Future<void> _save() async {
    await widget.preferences.setNotificationSettings(
      enabled: _enabled,
      hour: _time.hour,
      minute: _time.minute,
    );
    if (_enabled) {
      await NotificationService.instance.scheduleDaily(
        hour: _time.hour,
        minute: _time.minute,
      );
    } else {
      await NotificationService.instance.cancelDaily();
    }
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('Varslingsinnstillinger lagret')),
    );
  }

  Future<void> _pickTime() async {
    final selected = await showTimePicker(
      context: context,
      initialTime: _time,
    );
    if (selected != null) {
      setState(() => _time = selected);
    }
  }

  @override
  Widget build(BuildContext context) {
    if (!_loaded) {
      return const Center(child: CircularProgressIndicator());
    }
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Card(
          child: SwitchListTile(
            value: _enabled,
            onChanged: (value) => setState(() => _enabled = value),
            title: const Text('Daglig skredvarsel'),
            subtitle: const Text('Få en påminnelse om å sjekke dagens varsel'),
          ),
        ),
        const SizedBox(height: 12),
        Card(
          child: ListTile(
            enabled: _enabled,
            leading: const Icon(Icons.schedule),
            title: const Text('Tidspunkt'),
            subtitle: Text(_time.format(context)),
            trailing: const Icon(Icons.chevron_right),
            onTap: _enabled ? _pickTime : null,
          ),
        ),
        const SizedBox(height: 16),
        FilledButton.icon(
          onPressed: _save,
          icon: const Icon(Icons.save),
          label: const Text('Lagre'),
        ),
      ],
    );
  }
}
