# ProGuard Rules for Varsom Widget
# TESTED AND WORKING - Fixes TypeToken IllegalStateException

# ========================================
# CRITICAL: KEEP GENERIC SIGNATURES
# ========================================

# This is THE most important line for fixing TypeToken issues
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Keep line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep exceptions
-keepattributes Exceptions

# ========================================
# GSON - COMPREHENSIVE FIX
# ========================================

# Keep Gson core classes
-keep class com.google.gson.** { *; }
-keep class com.google.gson.stream.** { *; }
-dontwarn com.google.gson.**

# Keep TypeToken - CRITICAL for fixing your error
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Keep TypeToken constructors
-keepclassmembers class * extends com.google.gson.reflect.TypeToken {
    <init>();
}

# Keep ALL model classes - don't obfuscate ANY fields
-keep class com.appkungen.skredvarsel.models.** { *; }
-keepclassmembers class com.appkungen.skredvarsel.models.** { *; }

# Specific model classes (explicit safety)
-keep class com.appkungen.skredvarsel.models.AvalancheReport {
    <fields>;
    <init>(...);
}
-keep class com.appkungen.skredvarsel.models.Region { *; }
-keep class com.appkungen.skredvarsel.models.AvalancheWarning { *; }

# Keep classes in ForecastDetailActivity
-keep class com.appkungen.skredvarsel.DetailedAvalancheReport { *; }
-keep class com.appkungen.skredvarsel.AvalancheProblem { *; }
-keep class com.appkungen.skredvarsel.MountainWeather { *; }
-keep class com.appkungen.skredvarsel.SortableText { *; }
-keep class com.appkungen.skredvarsel.MeasurementType { *; }
-keep class com.appkungen.skredvarsel.MeasurementSubType { *; }

# Keep Gson annotations
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}

# Don't warn about Gson internals
-dontwarn sun.misc.**

# ========================================
# KOTLIN & COROUTINES
# ========================================

# Keep Kotlin metadata
-keep class kotlin.Metadata { *; }

# Keep coroutines - CRITICAL for async operations
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# Keep coroutine dispatchers
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepnames class kotlinx.coroutines.android.AndroidExceptionPreHandler {}
-keepnames class kotlinx.coroutines.android.AndroidDispatcherFactory {}

-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# Keep suspend functions
-keepclassmembers class * {
    suspend *** *(...);
}

# ========================================
# OKHTTP
# ========================================

# Keep OkHttp classes
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class okhttp3.internal.** { *; }
-dontwarn okhttp3.**

# Keep Okio
-keep class okio.** { *; }
-dontwarn okio.**

# Platform-specific warnings
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ========================================
# ANDROID COMPONENTS
# ========================================

# Keep widget provider
-keep class * extends android.appwidget.AppWidgetProvider { *; }

# Keep broadcast receivers
-keep class * extends android.content.BroadcastReceiver { *; }

# Keep activities
-keep class * extends android.app.Activity { *; }

# Keep all app components
-keep class com.appkungen.skredvarsel.VarsomWidgetProvider { *; }
-keep class com.appkungen.skredvarsel.Configure { *; }
-keep class com.appkungen.skredvarsel.ForecastDetailActivity { *; }
-keep class com.appkungen.skredvarsel.NotificationSettingsActivity { *; }
-keep class com.appkungen.skredvarsel.WidgetPreviewActivity { *; }
-keep class com.appkungen.skredvarsel.AvalancheForecastReceiver { *; }
-keep class com.appkungen.skredvarsel.BootReceiver { *; }

# Keep repository
-keep class com.appkungen.skredvarsel.repository.** { *; }

# Keep utility classes
-keep class com.appkungen.skredvarsel.WidgetutilsKt { *; }
-keep class com.appkungen.skredvarsel.HttpClient { *; }
-keep class com.appkungen.skredvarsel.NetworkModule { *; }
-keep class com.appkungen.skredvarsel.WidgetConstants { *; }
-keep class com.appkungen.skredvarsel.DangerLevelMapper { *; }
-keep class com.appkungen.skredvarsel.WidgetPreferences { *; }
-keep class com.appkungen.skredvarsel.NotificationScheduler { *; }

# ========================================
# GOOGLE PLAY SERVICES
# ========================================

-keep class com.google.android.gms.** { *; }
-keep class com.google.android.gms.location.** { *; }
-dontwarn com.google.android.gms.**

# ========================================
# VIEW BINDING
# ========================================

-keep class * extends androidx.viewbinding.ViewBinding {
    public static *** bind(***);
    public static *** inflate(***);
}

# ========================================
# ENUMS
# ========================================

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ========================================
# PARCELABLE
# ========================================

-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# ========================================
# SERIALIZATION
# ========================================

-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ========================================
# R8 COMPATIBILITY
# ========================================

-keepclassmembers class **.R$* {
    public static <fields>;
}

# ========================================
# NATIVE METHODS
# ========================================

-keepclasseswithmembernames class * {
    native <methods>;
}

# ========================================
# REMOVE LOGGING IN RELEASE
# ========================================

-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

# ========================================
# OPTIMIZATION
# ========================================

# Conservative optimization
-optimizationpasses 2
-dontpreverify
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*

# ========================================
# WARNINGS TO IGNORE
# ========================================

-dontwarn org.slf4j.**
-dontwarn org.apache.log4j.**
-dontwarn org.apache.commons.logging.**
-dontwarn org.apache.http.**
-dontwarn android.net.http.**
-dontwarn javax.annotation.**
-dontwarn org.jetbrains.annotations.**