import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:url_launcher/url_launcher.dart';

import '../models/forecast.dart';
import '../services/forecast_service.dart';
import '../services/preferences_service.dart';
import '../widgets/danger_badge.dart';
import '../widgets/error_panel.dart';

class ForecastScreen extends StatefulWidget {
  const ForecastScreen({
    required this.forecastService,
    required this.preferences,
    super.key,
  });

  final ForecastService forecastService;
  final PreferencesService preferences;

  @override
  State<ForecastScreen> createState() => _ForecastScreenState();
}

class _ForecastScreenState extends State<ForecastScreen> {
  late Future<_ForecastState> _future;

  @override
  void initState() {
    super.initState();
    _future = _load();
  }

  Future<_ForecastState> _load({bool forceRefresh = false}) async {
    final coords = await widget.preferences.coordinates();
    final regionId = await widget.preferences.selectedRegionId();
    final forecasts = await widget.forecastService.getForecast(
      regionId: regionId,
      coordinates: coords,
      forceRefresh: forceRefresh,
    );
    final details = coords == null
        ? await widget.forecastService.getDetailedForecast(regionId)
        : <DetailedAvalancheReport>[];
    return _ForecastState(forecasts: forecasts, details: details);
  }

  void _refresh() {
    setState(() {
      _future = _load(forceRefresh: true);
    });
  }

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<_ForecastState>(
      future: _future,
      builder: (context, snapshot) {
        if (snapshot.connectionState != ConnectionState.done) {
          return const Center(child: CircularProgressIndicator());
        }
        if (snapshot.hasError) {
          return ErrorPanel(
            message: 'Kunne ikke laste skredvarsel.',
            onRetry: _refresh,
          );
        }
        final state = snapshot.requireData;
        if (state.forecasts.isEmpty) {
          return ErrorPanel(message: 'Ingen varsel funnet.', onRetry: _refresh);
        }
        return RefreshIndicator(
          onRefresh: () async => _refresh(),
          child: ListView(
            padding: const EdgeInsets.all(16),
            children: [
              _ForecastHero(report: state.forecasts.first),
              const SizedBox(height: 16),
              if (state.forecasts.length > 1)
                _Timeline(forecasts: state.forecasts),
              if (state.details.isNotEmpty) ...[
                const SizedBox(height: 16),
                _DetailedForecast(details: state.details.first),
              ],
            ],
          ),
        );
      },
    );
  }
}

class _ForecastHero extends StatelessWidget {
  const _ForecastHero({required this.report});

  final AvalancheReport report;

  @override
  Widget build(BuildContext context) {
    final date = report.validFrom == null
        ? ''
        : DateFormat.EEEE('nb_NO').format(report.validFrom!);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            DangerBadge(level: report.dangerLevel, size: 68),
            const SizedBox(width: 16),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    report.regionName,
                    style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                          fontWeight: FontWeight.w800,
                        ),
                  ),
                  if (date.isNotEmpty) Text(date),
                  const SizedBox(height: 12),
                  Text(report.mainText),
                  const SizedBox(height: 12),
                  OutlinedButton.icon(
                    onPressed: () {
                      final region = Uri.encodeComponent(report.regionName);
                      final day = report.validFrom == null
                          ? ''
                          : DateFormat('yyyy-MM-dd').format(report.validFrom!);
                      launchUrl(
                        Uri.parse(
                          'https://www.varsom.no/snoskred/varsling/varsel/$region/$day',
                        ),
                        mode: LaunchMode.externalApplication,
                      );
                    },
                    icon: const Icon(Icons.open_in_new),
                    label: const Text('Åpne på Varsom.no'),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _Timeline extends StatelessWidget {
  const _Timeline({required this.forecasts});

  final List<AvalancheReport> forecasts;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Row(
          children: forecasts.take(4).map((report) {
            final label = report.validFrom == null
                ? ''
                : DateFormat.E('nb_NO').format(report.validFrom!);
            return Expanded(
              child: Column(
                children: [
                  Text(label),
                  const SizedBox(height: 8),
                  DangerBadge(level: report.dangerLevel, size: 38),
                ],
              ),
            );
          }).toList(),
        ),
      ),
    );
  }
}

class _DetailedForecast extends StatelessWidget {
  const _DetailedForecast({required this.details});

  final DetailedAvalancheReport details;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _InfoCard(title: 'Skredfarevurdering', body: details.avalancheDanger),
        _InfoCard(title: 'Snødekke og fjellvær', body: details.snowSurface),
        _InfoCard(title: 'Svake lag', body: details.currentWeakLayers),
        if (details.mountainWeather != null)
          _InfoCard(
            title: 'Fjellvær',
            body: [
              details.mountainWeather!.text,
              details.mountainWeather!.comment,
              details.mountainWeather!.windSpeed,
              details.mountainWeather!.windDirection,
              details.mountainWeather!.precipitation,
            ].whereType<String>().where((text) => text.isNotEmpty).join('\n'),
          ),
        if (details.problems.isNotEmpty)
          ...details.problems.map(
            (problem) => _InfoCard(
              title: problem.typeName ?? 'Skredproblem',
              body: [
                problem.causeName,
                problem.triggerName,
                problem.sizeName,
                problem.exposedClimateName,
              ].whereType<String>().where((text) => text.isNotEmpty).join('\n'),
            ),
          ),
        _InfoCard(
          title: 'Siste skredaktivitet',
          body: details.latestAvalancheActivity,
        ),
        _InfoCard(title: 'Observasjoner', body: details.latestObservations),
      ],
    );
  }
}

class _InfoCard extends StatelessWidget {
  const _InfoCard({required this.title, required this.body});

  final String title;
  final String? body;

  @override
  Widget build(BuildContext context) {
    if (body == null || body!.trim().isEmpty) {
      return const SizedBox.shrink();
    }
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Card(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                title,
                style: Theme.of(context).textTheme.titleMedium?.copyWith(
                      fontWeight: FontWeight.w800,
                    ),
              ),
              const SizedBox(height: 8),
              Text(body!),
            ],
          ),
        ),
      ),
    );
  }
}

class _ForecastState {
  const _ForecastState({required this.forecasts, required this.details});

  final List<AvalancheReport> forecasts;
  final List<DetailedAvalancheReport> details;
}
