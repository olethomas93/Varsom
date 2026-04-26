import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:intl/intl.dart';

import '../models/regobs.dart';

class RegobsService {
  static const _searchUrl = 'https://api.regobs.no/v5/Search';

  final http.Client _client;

  RegobsService({http.Client? client}) : _client = client ?? http.Client();

  Future<List<RegobsObservation>> recentSnowObservations({
    int days = 7,
    int maxRecords = 200,
  }) async {
    final formatter = DateFormat('yyyy-MM-dd');
    final now = DateTime.now();
    final body = jsonEncode({
      'SelectedGeoHazards': [10],
      'FromDtObsTime': formatter.format(now.subtract(Duration(days: days))),
      'ToDtObsTime': formatter.format(now),
      'NumberOfRecords': maxRecords,
      'Offset': 0,
      'LangKey': 1,
      'OrderBy': 'DtObsTime',
    });

    final response = await _client.post(
      Uri.parse(_searchUrl),
      headers: const {
        'Accept': 'application/json',
        'Content-Type': 'application/json',
      },
      body: body,
    );
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw Exception('Regobs HTTP ${response.statusCode}');
    }

    return (jsonDecode(response.body) as List)
        .whereType<Map<String, dynamic>>()
        .map(RegobsObservation.fromJson)
        .where((obs) => obs.location != null)
        .toList();
  }
}
