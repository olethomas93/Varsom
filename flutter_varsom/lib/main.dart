import 'package:flutter/material.dart';
import 'package:intl/date_symbol_data_local.dart';

import 'src/screens/home_screen.dart';
import 'src/services/notification_service.dart';
import 'src/theme.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await initializeDateFormatting('nb_NO');
  await NotificationService.instance.initialize();
  runApp(const VarsomApp());
}

class VarsomApp extends StatelessWidget {
  const VarsomApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Varsom',
      debugShowCheckedModeBanner: false,
      theme: buildVarsomTheme(),
      home: const HomeScreen(),
    );
  }
}
