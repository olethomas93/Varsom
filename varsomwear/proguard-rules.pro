# Varsom Wear ProGuard Rules

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Data models
-keep class com.appkungen.wear.data.** { *; }
-keepclassmembers class com.appkungen.wear.data.** { *; }
-keep class com.appkungen.wear.WearSettingsActivity$RegionResponse { *; }

# OkHttp
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-keep class okio.** { *; }
-dontwarn okio.**

# Wear OS Tiles
-keep class * extends androidx.wear.tiles.TileService { *; }

# Complications
-keep class * extends androidx.wear.watchface.complications.datasource.ComplicationDataSourceService { *; }

# Kotlin coroutines
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# Logging
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}