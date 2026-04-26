# Varsom Flutter

Flutter-versjon av Varsom-appen, laget for Android og iOS.

Denne mappen inneholder Flutter-koden. Flutter SDK er ikke installert på denne maskinen akkurat nå, så plattformmappene må genereres før første bygg:

```powershell
cd flutter_varsom
flutter create --platforms android,ios .
flutter pub get
flutter run
```

For iOS må dette bygges på macOS med Xcode:

```bash
cd flutter_varsom
flutter build ios
```

Legg inn disse tillatelsestekstene i `ios/Runner/Info.plist` etter `flutter create`:

```xml
<key>NSLocationWhenInUseUsageDescription</key>
<string>Brukes for å finne skredvarsel og vise posisjonen din i kartet.</string>
```

Legg inn disse tillatelsene i `android/app/src/main/AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

Funksjoner i første Flutter-versjon:
- Skredvarsel fra NVE Varsom API
- Regionliste og lagret valgt region
- Bruk min posisjon
- Kart med Kartverket-topo og Regobs-observasjoner
- Varslingsinnstillinger lagret lokalt

Ikke portet ennå:
- Android App Widget
- Wear OS tile/complication
