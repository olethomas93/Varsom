import 'package:flutter/material.dart';

import '../models/region.dart';
import '../services/forecast_service.dart';
import '../services/location_service.dart';
import '../services/preferences_service.dart';
import '../widgets/danger_badge.dart';
import '../widgets/error_panel.dart';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({
    required this.forecastService,
    required this.preferences,
    required this.onChanged,
    super.key,
  });

  final ForecastService forecastService;
  final PreferencesService preferences;
  final VoidCallback onChanged;

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  final _locationService = LocationService();
  late Future<List<Region>> _regions;
  String _query = '';
  String? _currentLabel;
  bool _usingLocation = false;

  @override
  void initState() {
    super.initState();
    _regions = widget.forecastService.getRegions();
    _loadCurrent();
  }

  Future<void> _loadCurrent() async {
    final coords = await widget.preferences.coordinates();
    final name = await widget.preferences.selectedRegionName();
    final id = await widget.preferences.selectedRegionId();
    if (!mounted) return;
    setState(() {
      _usingLocation = coords != null;
      _currentLabel = coords == null
          ? name ?? 'Region $id'
          : 'Min posisjon (${coords.$1.toStringAsFixed(3)}, ${coords.$2.toStringAsFixed(3)})';
    });
  }

  Future<void> _selectRegion(Region region) async {
    await widget.preferences.setRegion(region.id, region.name);
    await _loadCurrent();
    widget.onChanged();
  }

  Future<void> _useCurrentLocation() async {
    try {
      final coords = await _locationService.currentPosition();
      await widget.preferences.setCoordinates(coords.$1, coords.$2);
      await _loadCurrent();
      widget.onChanged();
    } catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(error.toString())),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Card(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Valgt område',
                  style: Theme.of(context).textTheme.titleMedium?.copyWith(
                        fontWeight: FontWeight.w800,
                      ),
                ),
                const SizedBox(height: 8),
                Text(_currentLabel ?? 'Laster...'),
                const SizedBox(height: 12),
                FilledButton.icon(
                  onPressed: _useCurrentLocation,
                  icon: const Icon(Icons.my_location),
                  label: Text(_usingLocation
                      ? 'Oppdater min posisjon'
                      : 'Bruk min posisjon'),
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 16),
        TextField(
          decoration: const InputDecoration(
            prefixIcon: Icon(Icons.search),
            hintText: 'Søk etter region',
            border: OutlineInputBorder(),
          ),
          onChanged: (value) => setState(() => _query = value),
        ),
        const SizedBox(height: 16),
        FutureBuilder<List<Region>>(
          future: _regions,
          builder: (context, snapshot) {
            if (snapshot.connectionState != ConnectionState.done) {
              return const Center(child: CircularProgressIndicator());
            }
            if (snapshot.hasError) {
              return ErrorPanel(
                message: 'Kunne ikke laste regioner.',
                onRetry: () {
                  setState(() => _regions = widget.forecastService.getRegions());
                },
              );
            }
            final query = _query.trim().toLowerCase();
            final regions = snapshot.requireData
                .where((region) => query.isEmpty
                    ? true
                    : region.name.toLowerCase().contains(query))
                .toList();
            return Column(
              children: regions.map((region) {
                final level = region.warnings.isEmpty ? '0' : region.warnings[0];
                return Padding(
                  padding: const EdgeInsets.only(bottom: 8),
                  child: Card(
                    child: ListTile(
                      leading: DangerBadge(level: level, size: 40),
                      title: Text(region.name),
                      subtitle: Text(region.typeName),
                      trailing: const Icon(Icons.chevron_right),
                      onTap: () => _selectRegion(region),
                    ),
                  ),
                );
              }).toList(),
            );
          },
        ),
      ],
    );
  }
}
