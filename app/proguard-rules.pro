# Add project specific ProGuard rules here.

# ========================================
# GENERAL RULES
# ========================================

# Keep line numbers for better crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep annotations
-keepattributes *Annotation*

# Keep generic signatures
-keepattributes Signature

# Keep exceptions
-keepattributes Exceptions

# ========================================
# KOTLIN
# ========================================

# Keep Kotlin metadata
-keep class kotlin.Metadata { *; }

# Keep Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepnames class kotlinx.coroutines.android.AndroidExceptionPreHandler {}
-keepnames class kotlinx.coroutines.android.AndroidDispatcherFactory {}
-keep class com.google.gson.reflect.TypeToken { *; }

-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# ========================================
# GSON
# ========================================

# Keep Gson classes
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }

# Keep data classes used with Gson
-keep class com.appkungen.skredvarsel.models.AvalancheReport { *; }
-keep class com.appkungen.skredvarsel.Region { *; }
-keep class com.appkungen.skredvarsel.models.AvalancheWarning { *; }
-keep class com.appkungen.skredvarsel.repository.** { *; }

# Keep all model classes in the models package
-keep class com.appkungen.skredvarsel.models.** { *; }

# Gson uses generic type information stored in a class file when working with fields
-keepattributes Signature

# Gson specific classes
-dontwarn sun.misc.**

# ========================================
# OKHTTP
# ========================================

# OkHttp platform used only on JVM and when Conscrypt dependency is available.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Keep OkHttp classes
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**

# Okio
-dontwarn okio.**
-keep class okio.** { *; }

# ========================================
# ANDROID COMPONENTS
# ========================================

# Keep AppWidget provider
-keep class * extends android.appwidget.AppWidgetProvider {
    *;
}

# Keep BroadcastReceivers
-keep class * extends android.content.BroadcastReceiver {
    *;
}

# Keep Activities
-keep class * extends android.app.Activity {
    *;
}

# Keep all widget-related classes
-keep class com.appkungen.skredvarsel.repository.AvalancheForecastRepository { *; }
-keep class com.appkungen.skredvarsel.WidgetutilsKt { *; }
-keep class com.appkungen.skredvarsel.VarsomWidgetProvider { *; }
-keep class com.appkungen.skredvarsel.Configure { *; }
-keep class com.appkungen.skredvarsel.ForecastDetailActivity { *; }
-keep class com.appkungen.skredvarsel.NotificationSettingsActivity { *; }
-keep class com.appkungen.skredvarsel.WidgetPreviewActivity { *; }

# Keep receivers
-keep class com.appkungen.skredvarsel.AvalancheForecastReceiver { *; }
-keep class com.appkungen.skredvarsel.BootReceiver { *; }

# ========================================
# GOOGLE PLAY SERVICES
# ========================================

# Keep Google Play Services classes
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# ========================================
# CRASHLYTICS (uncomment if using)
# ========================================

# -keepattributes *Annotation*
# -keep public class * extends java.lang.Exception
# -keep class com.google.firebase.crashlytics.** { *; }
# -dontwarn com.google.firebase.crashlytics.**

# ========================================
# REMOVE LOGGING IN RELEASE
# ========================================

# Remove all logging
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

# Optimization is turned on by default
# Remove unused code aggressively
-optimizationpasses 5
-dontpreverify

# Allow optimization
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*

# ========================================
# SERIALIZATION
# ========================================

# Keep serialization classes
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ========================================
# PARCELABLE
# ========================================

-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# ========================================
# VIEW BINDING
# ========================================

-keep class * extends androidx.viewbinding.ViewBinding {
    public static *** bind(***);
    public static *** inflate(***);
}

# ========================================
# REMOVE DEBUG CODE
# ========================================

# Remove all debug-related code
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    static void checkParameterIsNotNull(java.lang.Object, java.lang.String);
    static void checkExpressionValueIsNotNull(java.lang.Object, java.lang.String);
    static void checkNotNullExpressionValue(java.lang.Object, java.lang.String);
    static void checkReturnedValueIsNotNull(java.lang.Object, java.lang.String, java.lang.String);
    static void checkFieldIsNotNull(java.lang.Object, java.lang.String, java.lang.String);
    static void checkNotNullParameter(java.lang.Object, java.lang.String);
}

# ========================================
# WARNINGS TO IGNORE
# ========================================

# Ignore warnings about missing classes that are not used
-dontwarn org.slf4j.**
-dontwarn org.apache.log4j.**
-dontwarn org.apache.commons.logging.**
-dontwarn org.apache.http.**
-dontwarn android.net.http.**

# ========================================
# KEEP CUSTOM RULES
# ========================================

# If you have specific classes you want to keep, add them here
# Example:
# -keep class com.appkungen.skredvarsel.MyImportantClass { *; }