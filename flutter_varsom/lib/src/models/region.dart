class Region {
  const Region({
    required this.id,
    required this.name,
    required this.typeName,
    required this.warnings,
  });

  final String id;
  final String name;
  final String typeName;
  final List<String> warnings;

  factory Region.fromJson(Map<String, dynamic> json) {
    final warningList = json['AvalancheWarningList'];
    return Region(
      id: json['Id']?.toString() ?? '',
      name: json['Name']?.toString() ?? '',
      typeName: json['TypeName']?.toString() ?? '',
      warnings: warningList is List
          ? warningList
              .map((item) => item is Map<String, dynamic>
                  ? item['DangerLevel']?.toString() ?? '0'
                  : '0')
              .toList()
          : const [],
    );
  }
}
