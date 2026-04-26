import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:intl/intl.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../models/forecast.dart';
import '../models/region.dart';

class ForecastService {
  static const _baseUrl =
      'https://api01.nve.no/hydrology/forecast/avalanche/v6.2.1/api';
  static const _cacheMaxAge = Duration(hours: 1);

  final http.Client _client;

  ForecastService({http.Client? client}) : _client = client ?? http.Client();

  Future<List<AvalancheReport>> getForecast({
    String? regionId,
    (double, double)? coordinates,
    bool forceRefresh = false,
  }) async {
    final cacheKey = _cacheKey(regionId, coordinates);
    if (!forceRefresh) {
      final cached = await _readForecastCache(cacheKey);
      if (cached != null) return cached;
    }

    final response = await _client.get(Uri.parse(_forecastUrl(
      regionId: regionId,
      coordinates: coordinates,
      detail: false,
    )));
    if (response.statusCode < 200 || response.statusCode >= 300) {
      final stale = await _readForecastCache(cacheKey, allowExpired: true);
      if (stale != null) return stale;
      throw Exception('NVE HTTP ${response.statusCode}');
    }

    final data = (jsonDecode(response.body) as List)
        .whereType<Map<String, dynamic>>()
        .map(AvalancheReport.fromJson)
        .toList();
    await _writeForecastCache(cacheKey, response.body);
    return data;
  }

  Future<List<DetailedAvalancheReport>> getDetailedForecast(
    String regionId,
  ) async {
    final response = await _client.get(Uri.parse(_forecastUrl(
      regionId: regionId,
      detail: true,
    )));
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw Exception('NVE HTTP ${response.statusCode}');
    }
    return (jsonDecode(response.body) as List)
        .whereType<Map<String, dynamic>>()
        .map(DetailedAvalancheReport.fromJson)
        .toList();
  }

  Future<List<Region>> getRegions() async {
    final dates = _dateRange();
    final url =
        '$_baseUrl/RegionSummary/detail/1/${dates.from}/${dates.to}';
    final response = await _client.get(Uri.parse(url));
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw Exception('NVE HTTP ${response.statusCode}');
    }
    final regions = (jsonDecode(response.body) as List)
        .whereType<Map<String, dynamic>>()
        .map(Region.fromJson)
        .where((region) => region.name.isNotEmpty && region.id.isNotEmpty)
        .toList();
    regions.sort((a, b) => a.name.compareTo(b.name));
    return regions;
  }

  String _forecastUrl({
    String? regionId,
    (double, double)? coordinates,
    required bool detail,
  }) {
    final dates = _dateRange();
    final mode = detail ? 'detail' : 'Simple';
    if (coordinates != null) {
      final lat = coordinates.$1.toStringAsFixed(3);
      final lng = coordinates.$2.toStringAsFixed(3);
      return '$_baseUrl/AvalancheWarningByCoordinates/$mode/$lat/$lng/1/${dates.from}/${dates.to}';
    }
    return '$_baseUrl/AvalancheWarningByRegion/$mode/${regionId ?? '3011'}/1/${dates.from}/${dates.to}';
  }

  ({String from, String to}) _dateRange() {
    final formatter = DateFormat('yyyy-MM-dd');
    final now = DateTime.now();
    return (
      from: formatter.format(now.subtract(const Duration(days: 1))),
      to: formatter.format(now.add(const Duration(days: 2))),
    );
  }

  String _cacheKey(String? regionId, (double, double)? coordinates) {
    if (coordinates != null) {
      return 'coord_${coordinates.$1.toStringAsFixed(3)}_${coordinates.$2.toStringAsFixed(3)}';
    }
    return 'region_${regionId ?? '3011'}';
  }

  Future<List<AvalancheReport>?> _readForecastCache(
    String cacheKey, {
    bool allowExpired = false,
  }) async {
    final prefs = await SharedPreferences.getInstance();
    final timestamp = prefs.getInt('forecast_timestamp_$cacheKey');
    final body = prefs.getString('forecast_body_$cacheKey');
    if (timestamp == null || body == null) return null;
    if (!allowExpired) {
      final age = DateTime.now().difference(
        DateTime.fromMillisecondsSinceEpoch(timestamp),
      );
      if (age > _cacheMaxAge) return null;
    }
    return (jsonDecode(body) as List)
        .whereType<Map<String, dynamic>>()
        .map(AvalancheReport.fromJson)
        .toList();
  }

  Future<void> _writeForecastCache(String cacheKey, String body) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('forecast_body_$cacheKey', body);
    await prefs.setInt(
      'forecast_timestamp_$cacheKey',
      DateTime.now().millisecondsSinceEpoch,
    );
  }
}
