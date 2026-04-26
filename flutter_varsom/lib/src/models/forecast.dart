class AvalancheReport {
  const AvalancheReport({
    required this.regionName,
    required this.dangerLevel,
    required this.mainText,
    required this.validFrom,
    required this.publishTime,
  });

  final String regionName;
  final String dangerLevel;
  final String mainText;
  final DateTime? validFrom;
  final DateTime? publishTime;

  factory AvalancheReport.fromJson(Map<String, dynamic> json) {
    return AvalancheReport(
      regionName: json['RegionName']?.toString() ?? '',
      dangerLevel: json['DangerLevel']?.toString() ?? '0',
      mainText: json['MainText']?.toString() ?? '',
      validFrom: DateTime.tryParse(json['ValidFrom']?.toString() ?? ''),
      publishTime: DateTime.tryParse(json['PublishTime']?.toString() ?? ''),
    );
  }
}

class DetailedAvalancheReport {
  const DetailedAvalancheReport({
    required this.regionName,
    required this.dangerLevel,
    this.mainText,
    this.avalancheDanger,
    this.snowSurface,
    this.currentWeakLayers,
    this.latestAvalancheActivity,
    this.latestObservations,
    this.mountainWeather,
    this.problems = const [],
  });

  final String regionName;
  final String dangerLevel;
  final String? mainText;
  final String? avalancheDanger;
  final String? snowSurface;
  final String? currentWeakLayers;
  final String? latestAvalancheActivity;
  final String? latestObservations;
  final MountainWeather? mountainWeather;
  final List<AvalancheProblem> problems;

  factory DetailedAvalancheReport.fromJson(Map<String, dynamic> json) {
    final problemsJson = json['AvalancheProblems'];
    return DetailedAvalancheReport(
      regionName: json['RegionName']?.toString() ?? '',
      dangerLevel: json['DangerLevel']?.toString() ?? '0',
      mainText: json['MainText']?.toString(),
      avalancheDanger: json['AvalancheDanger']?.toString(),
      snowSurface: json['SnowSurface']?.toString(),
      currentWeakLayers: json['CurrentWeakLayers']?.toString(),
      latestAvalancheActivity: json['LatestAvalancheActivity']?.toString(),
      latestObservations: json['LatestObservations']?.toString(),
      mountainWeather: json['MountainWeather'] is Map<String, dynamic>
          ? MountainWeather.fromJson(json['MountainWeather'])
          : null,
      problems: problemsJson is List
          ? problemsJson
              .whereType<Map<String, dynamic>>()
              .map(AvalancheProblem.fromJson)
              .toList()
          : const [],
    );
  }
}

class AvalancheProblem {
  const AvalancheProblem({
    this.typeName,
    this.causeName,
    this.triggerName,
    this.sizeName,
    this.validExpositions,
    this.exposedClimateName,
  });

  final String? typeName;
  final String? causeName;
  final String? triggerName;
  final String? sizeName;
  final String? validExpositions;
  final String? exposedClimateName;

  factory AvalancheProblem.fromJson(Map<String, dynamic> json) {
    return AvalancheProblem(
      typeName: json['AvalancheProblemTypeName']?.toString(),
      causeName: json['AvalCauseName']?.toString(),
      triggerName: json['AvalTriggerSimpleName']?.toString(),
      sizeName: json['DestructiveSizeExtName']?.toString(),
      validExpositions: json['ValidExpositions']?.toString(),
      exposedClimateName: json['ExposedClimateName']?.toString(),
    );
  }
}

class MountainWeather {
  const MountainWeather({
    this.text,
    this.comment,
    this.cloudCover,
    this.precipitation,
    this.windSpeed,
    this.windDirection,
    this.freezingLevel,
  });

  final String? text;
  final String? comment;
  final String? cloudCover;
  final String? precipitation;
  final String? windSpeed;
  final String? windDirection;
  final String? freezingLevel;

  factory MountainWeather.fromJson(Map<String, dynamic> json) {
    return MountainWeather(
      text: json['MountainWeatherText']?.toString(),
      comment: json['Comment']?.toString(),
      cloudCover: json['CloudCoverName']?.toString(),
      precipitation: json['PrecipitationName']?.toString(),
      windSpeed: json['WindSpeedName']?.toString(),
      windDirection: json['WindDirectionName']?.toString(),
      freezingLevel: json['FreezingLevel']?.toString(),
    );
  }
}
