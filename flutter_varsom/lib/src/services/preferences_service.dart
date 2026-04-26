import 'package:shared_preferences/shared_preferences.dart';

class PreferencesService {
  static const defaultRegionId = '3011';
  static const _selectedRegion = 'selectedRegion';
  static const _selectedRegionName = 'selectedRegionName';
  static const _latitude = 'latitude';
  static const _longitude = 'longitude';
  static const _notificationsEnabled = 'notificationsEnabled';
  static const _notificationHour = 'notificationHour';
  static const _notificationMinute = 'notificationMinute';

  Future<String> selectedRegionId() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getString(_selectedRegion) ?? defaultRegionId;
  }

  Future<String?> selectedRegionName() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getString(_selectedRegionName);
  }

  Future<void> setRegion(String id, String name) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_selectedRegion, id);
    await prefs.setString(_selectedRegionName, name);
    await prefs.remove(_latitude);
    await prefs.remove(_longitude);
  }

  Future<(double, double)?> coordinates() async {
    final prefs = await SharedPreferences.getInstance();
    final lat = prefs.getDouble(_latitude);
    final lng = prefs.getDouble(_longitude);
    if (lat == null || lng == null) return null;
    return (lat, lng);
  }

  Future<void> setCoordinates(double lat, double lng) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setDouble(_latitude, lat);
    await prefs.setDouble(_longitude, lng);
    await prefs.remove(_selectedRegion);
    await prefs.remove(_selectedRegionName);
  }

  Future<bool> notificationsEnabled() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getBool(_notificationsEnabled) ?? false;
  }

  Future<({int hour, int minute})> notificationTime() async {
    final prefs = await SharedPreferences.getInstance();
    return (
      hour: prefs.getInt(_notificationHour) ?? 8,
      minute: prefs.getInt(_notificationMinute) ?? 0,
    );
  }

  Future<void> setNotificationSettings({
    required bool enabled,
    required int hour,
    required int minute,
  }) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_notificationsEnabled, enabled);
    await prefs.setInt(_notificationHour, hour);
    await prefs.setInt(_notificationMinute, minute);
  }
}
