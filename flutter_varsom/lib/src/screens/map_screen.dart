import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:intl/intl.dart';
import 'package:latlong2/latlong.dart';
import 'package:url_launcher/url_launcher.dart';

import '../models/regobs.dart';
import '../services/location_service.dart';
import '../services/regobs_service.dart';

class MapScreen extends StatefulWidget {
  const MapScreen({super.key});

  @override
  State<MapScreen> createState() => _MapScreenState();
}

class _MapScreenState extends State<MapScreen> {
  static const _norwayCenter = LatLng(65.0, 14.0);

  final _mapController = MapController();
  final _regobs = RegobsService();
  final _location = LocationService();

  late Future<List<RegobsObservation>> _observations;
  LatLng? _myLocation;

  @override
  void initState() {
    super.initState();
    _observations = _regobs.recentSnowObservations();
  }

  Future<void> _centerOnMe() async {
    try {
      final coords = await _location.currentPosition();
      final point = LatLng(coords.$1, coords.$2);
      setState(() => _myLocation = point);
      _mapController.move(point, 11);
    } catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(error.toString())),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Kart')),
      body: Stack(
        children: [
          FutureBuilder<List<RegobsObservation>>(
            future: _observations,
            builder: (context, snapshot) {
              final observations = snapshot.data ?? const <RegobsObservation>[];
              return FlutterMap(
                mapController: _mapController,
                options: const MapOptions(
                  initialCenter: _norwayCenter,
                  initialZoom: 5,
                  minZoom: 4,
                  maxZoom: 17,
                ),
                children: [
                  TileLayer(
                    urlTemplate:
                        'https://cache.kartverket.no/v1/wmts/1.0.0/topo/default/webmercator/{z}/{y}/{x}.png',
                    userAgentPackageName: 'com.appkungen.varsomwidget',
                  ),
                  TileLayer(
                    urlTemplate:
                        'https://nve.geodataonline.no/arcgis/rest/services/Bratthet/MapServer/tile/{z}/{y}/{x}',
                    userAgentPackageName: 'com.appkungen.varsomwidget',
                    maxZoom: 16,
                    opacity: 0.55,
                  ),
                  MarkerLayer(
                    markers: [
                      if (_myLocation != null)
                        Marker(
                          point: _myLocation!,
                          width: 42,
                          height: 42,
                          child: const Icon(
                            Icons.my_location,
                            color: Colors.blueAccent,
                            size: 32,
                          ),
                        ),
                      ...observations.map(
                        (obs) => Marker(
                          point: obs.location!,
                          width: 42,
                          height: 42,
                          child: IconButton.filled(
                            padding: EdgeInsets.zero,
                            icon: const Icon(Icons.location_on),
                            onPressed: () => _showObservation(obs),
                          ),
                        ),
                      ),
                    ],
                  ),
                ],
              );
            },
          ),
          Positioned(
            right: 16,
            bottom: 16,
            child: FloatingActionButton(
              onPressed: _centerOnMe,
              child: const Icon(Icons.my_location),
            ),
          ),
          Positioned(
            left: 8,
            bottom: 8,
            child: DecoratedBox(
              decoration: BoxDecoration(
                color: Colors.black.withOpacity(0.65),
                borderRadius: BorderRadius.circular(4),
              ),
              child: const Padding(
                padding: EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                child: Text(
                  '© Kartverket · Bratthet © NVE · Obs. © Regobs',
                  style: TextStyle(fontSize: 10),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  void _showObservation(RegobsObservation observation) {
    showModalBottomSheet<void>(
      context: context,
      showDragHandle: true,
      builder: (context) {
        final date = observation.obsTime == null
            ? null
            : DateFormat('d. MMM yyyy HH:mm', 'nb_NO')
                .format(observation.obsTime!);
        return SafeArea(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(20, 8, 20, 20),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  observation.regionName ??
                      observation.municipalName ??
                      'Observasjon',
                  style: Theme.of(context).textTheme.titleLarge?.copyWith(
                        fontWeight: FontWeight.w800,
                      ),
                ),
                if (date != null) Text(date),
                if (observation.observerName != null)
                  Text('Observatør: ${observation.observerName}'),
                const SizedBox(height: 12),
                Text(observation.summary ?? 'Ingen oppsummering.'),
                const SizedBox(height: 16),
                FilledButton.icon(
                  onPressed: () {
                    launchUrl(
                      Uri.parse(observation.publicUrl),
                      mode: LaunchMode.externalApplication,
                    );
                  },
                  icon: const Icon(Icons.open_in_new),
                  label: const Text('Vis fullstendig'),
                ),
              ],
            ),
          ),
        );
      },
    );
  }
}
