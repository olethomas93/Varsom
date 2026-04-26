import 'package:flutter/material.dart';

ThemeData buildVarsomTheme() {
  const background = Color(0xFF111827);
  const surface = Color(0xFF1F2937);
  const accent = Color(0xFF22C55E);

  return ThemeData(
    useMaterial3: true,
    brightness: Brightness.dark,
    colorScheme: ColorScheme.fromSeed(
      seedColor: accent,
      brightness: Brightness.dark,
      surface: surface,
    ).copyWith(primary: accent),
    scaffoldBackgroundColor: background,
    appBarTheme: const AppBarTheme(
      backgroundColor: surface,
      foregroundColor: Colors.white,
      centerTitle: false,
    ),
    cardTheme: CardTheme(
      color: surface,
      elevation: 0,
      margin: EdgeInsets.zero,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
    ),
    tabBarTheme: const TabBarTheme(
      indicatorColor: accent,
      labelColor: Colors.white,
      unselectedLabelColor: Color(0xFF9CA3AF),
    ),
  );
}

Color dangerColor(String level) {
  return switch (level) {
    '1' => const Color(0xFF4ADE80),
    '2' => const Color(0xFFFACC15),
    '3' => const Color(0xFFF97316),
    '4' => const Color(0xFFEF4444),
    '5' => const Color(0xFF7F1D1D),
    _ => const Color(0xFF6B7280),
  };
}
