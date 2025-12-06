package com.appkungen.skredvarsel

import HttpClient
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.appkungen.varsomwidget.R
import java.util.*
import com.appkungen.skredvarsel.models.*

object NotificationScheduler {
    private const val NOTIFICATION_CHANNEL_ID = "avalanche_forecast_channel"
    private const val NOTIFICATION_ID = 1001
    private const val ALARM_REQUEST_CODE = 2001

    fun scheduleDaily(context: Context, hour: Int = 7, minute: Int = 0) {
        createNotificationChannel(context)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AvalancheForecastReceiver::class.java)

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_IMMUTABLE
            } else {
                0
            }
        )

        // Set alarm for specific time
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)

            // If the time has passed today, schedule for tomorrow
            if (before(Calendar.getInstance())) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        // Schedule repeating alarm
        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )

        Log.d("NotificationScheduler", "Daily notification scheduled for ${calendar.time}")
    }

    fun cancelDaily(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AvalancheForecastReceiver::class.java)

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_IMMUTABLE
            } else {
                0
            }
        )

        alarmManager.cancel(pendingIntent)
        Log.d("NotificationScheduler", "Daily notification cancelled")
    }

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Avalanche Forecast"
            val descriptionText = "Daily avalanche danger level notifications"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun sendNotification(context: Context, regionName: String, dangerLevel: String, description: String) {
        createNotificationChannel(context)

        // Create intent to open app/widget when notification is clicked
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = android.net.Uri.parse(WidgetConstants.VARSOM_URL)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_IMMUTABLE
            } else {
                0
            }
        )

        // Get appropriate icon and color based on danger level
        val icon = DangerLevelMapper.getLevelIcon(dangerLevel)
        val title = "Avalanche Warning: Level $dangerLevel"

        val notificationText = "$regionName - $description"

        val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.level_0) // Use a small icon version
            .setContentTitle(title)
            .setContentText(notificationText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notificationText))
            .setPriority(getPriorityForDangerLevel(dangerLevel))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        // Show notification
        with(NotificationManagerCompat.from(context)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    notify(NOTIFICATION_ID, builder.build())
                }
            } else {
                notify(NOTIFICATION_ID, builder.build())
            }
        }
    }

    private fun getPriorityForDangerLevel(dangerLevel: String): Int {
        return when (dangerLevel) {
            "4" -> NotificationCompat.PRIORITY_HIGH
            "3" -> NotificationCompat.PRIORITY_HIGH
            "2" -> NotificationCompat.PRIORITY_DEFAULT
            else -> NotificationCompat.PRIORITY_LOW
        }
    }
}

class AvalancheForecastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Log.d("AvalancheForecast", "Notification receiver triggered")

        val widgetPrefs = WidgetPreferences(context)
        val httpClient = HttpClient()

        val lang = Locale.getDefault()
        val langCode = if (lang.toString().contains("nb")) {
            WidgetConstants.LANG_CODE_NORWEGIAN
        } else {
            WidgetConstants.LANG_CODE_ENGLISH
        }

        val (yesterdayDate, dayAfterTomorrowDate) = getYesterdayAndDayAfterTomorrow()

        // Build URL based on user's selected region or coordinates
        val url = buildApiUrl(widgetPrefs, langCode, yesterdayDate, dayAfterTomorrowDate)

        httpClient.makeRequest(url) { response, error ->
            if (error != null) {
                Log.e("AvalancheForecast", "Failed to fetch forecast", error)
            } else {
                response?.let {
                    val list = parseJsonToArrayList(it)
                    if (list != null && list.size >= 2) {
                        val today = list[1] // Index 1 is today
                        NotificationScheduler.sendNotification(
                            context,
                            today.RegionName,
                            today.DangerLevel,
                            today.MainText
                        )
                    }
                }
            }
        }
    }

    private fun buildApiUrl(
        widgetPrefs: WidgetPreferences,
        langCode: Int,
        yesterdayDate: String,
        dayAfterTomorrowDate: String
    ): String {
        val coord = widgetPrefs.fetchedCoord
        val selectedRegion = widgetPrefs.selectedRegion

        return when {
            coord != null -> {
                val obj = org.json.JSONObject(coord)
                "${WidgetConstants.API_BASE_URL}/AvalancheWarningByCoordinates/Simple/${obj.get("lat")}/${obj.get("lng")}/$langCode/$yesterdayDate/$dayAfterTomorrowDate"
            }
            selectedRegion != null -> {
                "${WidgetConstants.API_BASE_URL}/AvalancheWarningByRegion/Simple/$selectedRegion/$langCode/$yesterdayDate/$dayAfterTomorrowDate"
            }
            else -> {
                "${WidgetConstants.API_BASE_URL}/AvalancheWarningByRegion/Simple/${WidgetConstants.DEFAULT_REGION_ID}/$langCode/$yesterdayDate/$dayAfterTomorrowDate"
            }
        }
    }
}