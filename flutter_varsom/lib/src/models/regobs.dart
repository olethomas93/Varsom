import 'package:latlong2/latlong.dart';

class RegobsObservation {
  const RegobsObservation({
    required this.regId,
    this.obsTime,
    this.location,
    this.observerName,
    this.regionName,
    this.municipalName,
    this.summary,
    this.url,
  });

  final int regId;
  final DateTime? obsTime;
  final LatLng? location;
  final String? observerName;
  final String? regionName;
  final String? municipalName;
  final String? summary;
  final String? url;

  String get publicUrl => url ?? 'https://www.regobs.no/Registration/$regId';

  factory RegobsObservation.fromJson(Map<String, dynamic> json) {
    final locationJson = json['ObsLocation'];
    final observerJson = json['Observer'];
    final summariesJson = json['Summaries'];

    LatLng? point;
    String? regionName;
    String? municipalName;
    if (locationJson is Map<String, dynamic>) {
      final lat = (locationJson['Latitude'] as num?)?.toDouble();
      final lng = (locationJson['Longitude'] as num?)?.toDouble();
      if (lat != null && lng != null) point = LatLng(lat, lng);
      regionName = locationJson['ForecastRegionName']?.toString();
      municipalName = locationJson['MunicipalName']?.toString();
    }

    String? summary;
    if (summariesJson is List && summariesJson.isNotEmpty) {
      final first = summariesJson.first;
      if (first is Map<String, dynamic>) {
        summary = first['Summary']?.toString() ??
            first['RegistrationName']?.toString();
      }
    }

    return RegobsObservation(
      regId: (json['RegId'] as num?)?.toInt() ?? 0,
      obsTime: DateTime.tryParse(json['DtObsTime']?.toString() ?? ''),
      location: point,
      observerName: observerJson is Map<String, dynamic>
          ? observerJson['NickName']?.toString()
          : null,
      regionName: regionName,
      municipalName: municipalName,
      summary: summary,
      url: json['Url']?.toString(),
    );
  }
}
