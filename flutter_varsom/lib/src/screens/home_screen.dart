import 'package:flutter/material.dart';

import '../services/forecast_service.dart';
import '../services/preferences_service.dart';
import 'forecast_screen.dart';
import 'map_screen.dart';
import 'notifications_screen.dart';
import 'settings_screen.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  final _forecastService = ForecastService();
  final _preferences = PreferencesService();

  int _refreshKey = 0;

  void _refreshForecast() {
    setState(() => _refreshKey++);
  }

  @override
  Widget build(BuildContext context) {
    return DefaultTabController(
      length: 3,
      child: Scaffold(
        appBar: AppBar(
          title: const Text('Skredvarsel'),
          actions: [
            IconButton(
              tooltip: 'Kart',
              icon: const Icon(Icons.map),
              onPressed: () {
                Navigator.of(context).push(
                  MaterialPageRoute(builder: (_) => const MapScreen()),
                );
              },
            ),
          ],
          bottom: const TabBar(
            tabs: [
              Tab(icon: Icon(Icons.analytics), text: 'Varsel'),
              Tab(icon: Icon(Icons.tune), text: 'Innstillinger'),
              Tab(icon: Icon(Icons.notifications), text: 'Varsler'),
            ],
          ),
        ),
        body: TabBarView(
          children: [
            ForecastScreen(
              key: ValueKey(_refreshKey),
              forecastService: _forecastService,
              preferences: _preferences,
            ),
            SettingsScreen(
              forecastService: _forecastService,
              preferences: _preferences,
              onChanged: _refreshForecast,
            ),
            NotificationsScreen(preferences: _preferences),
          ],
        ),
      ),
    );
  }
}
