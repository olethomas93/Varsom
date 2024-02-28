package com.northei.skredvarsel

import HttpClient
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.SizeF
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.core.widget.RemoteViewsCompat.setViewBackgroundResource
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.northei.varsomwidget.R
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.*


class AvalanceReport(
    var RegionName: String,
    var DangerLevel: String,
    var image: Int,
    var color: Int,
    var MainText: String,
    var ValidFrom: String,
    var PublishTime: String
)

/**
 * Implementation of App Widget functionality.
 */
class varsom : AppWidgetProvider() {
    @RequiresApi(Build.VERSION_CODES.S)
    private val job = SupervisorJob()
    companion object {
        const val ACTION_DOUBLE_CLICK = "com.example.appwidget.DOUBLE_CLICK"
    }
    @RequiresApi(Build.VERSION_CODES.S)
    private val client = OkHttpClient()

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            val remoteViews = RemoteViews(context.packageName, R.layout.varsom_medium)
            updateWidgetViews(client, context)


            // Create an Intent for the double-click action
            val intent = Intent(context, varsom::class.java)
            intent.action = ACTION_DOUBLE_CLICK

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_IMMUTABLE
            } else {
                0
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent, flags
            )

            // Set the PendingIntent for the double-click action
            remoteViews.setOnClickPendingIntent(R.id.card, pendingIntent)
            appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
        }


    }


    @RequiresApi(Build.VERSION_CODES.S)
    override fun onEnabled(context: Context) {
        val httpClient = HttpClient(client)
        val url = "https://api01.nve.no/hydrology/forecast/avalanche/v6.2.1/api/AvalancheWarningByRegion/Simple/3011/1/"

        Log.d("DEBUG", "ENABLED")

        updateWidgetViews(client, context)

    }
    private fun openBrowser(context: Context, url: String) {
        // Create an Intent to open the browser with the specified URL
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(browserIntent)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        val smallView = RemoteViews(context.packageName, R.layout.varsom_small)
        val mediumView = RemoteViews(context.packageName, R.layout.varsom_medium)
        val largeView = RemoteViews(context.packageName, R.layout.varsom_large2)


        updateWidgetViews(client, context)

    }


    @RequiresApi(Build.VERSION_CODES.S)
    override fun onReceive(context: Context?, intent: Intent?) {
        super.onReceive(context, intent)
        if (intent != null) {

            if (intent.getAction() != null && intent.getAction().equals(ACTION_DOUBLE_CLICK)) {
                // Handle the double-click action
                if (context != null) {
                    openBrowser(context, "https://www.varsom.no")
                };
            }
            if (intent.action == "setRegion") {
                //Log.d("COORD", "POSITION: SETTING REGION")

                val selectedRegion = intent.getStringExtra("selectedRegion")

                if (selectedRegion != null) {

                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    val appWidgetIds = appWidgetManager.getAppWidgetIds(context?.let {
                        ComponentName(
                            it, varsom::class.java
                        )
                    })

                    if (context != null) {
                        val sharedPreferences =
                            context.getSharedPreferences("WidgetPrefs", Context.MODE_PRIVATE)
                        val editor = sharedPreferences.edit()
                        editor.putString("selectedRegion", selectedRegion)
                        editor.putString("fetchedCoord", null)

                        editor.apply()
                        onUpdate(context, appWidgetManager, appWidgetIds)
                    }
                }

            } else if (intent.action == "setCoord") {
                Log.d("COORD", "POSITION: SETTING COORDS")

                val coord = intent.getStringExtra("fetchedCoord")

                if (coord != null) {
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    val appWidgetIds = appWidgetManager.getAppWidgetIds(context?.let {
                        ComponentName(
                            it, varsom::class.java
                        )
                    })

                    if (context != null) {
                        val sharedPreferences =
                            context.getSharedPreferences("WidgetPrefs", Context.MODE_PRIVATE)
                        val editor = sharedPreferences.edit()
                        editor.putString("fetchedCoord", coord)
                        editor.putString("selectedRegion", null)

                        editor.apply()
                        onUpdate(context, appWidgetManager, appWidgetIds)
                    }
                }
            }
        }
    }
}


@RequiresApi(Build.VERSION_CODES.S)
private fun updateWidgetViews(client: OkHttpClient, context: Context) {
    val smallView = RemoteViews(context.packageName, R.layout.varsom_small)
    val mediumView = RemoteViews(context.packageName, R.layout.varsom_medium)
    val largeView = RemoteViews(context.packageName, R.layout.varsom_large2)
    val lang = Locale.getDefault()
    var langCode = 2
    //Log.d("KANG", "LANGUAGE: ${lang}")
    if(lang.toString().contains("nb")){
        langCode = 1
    }
    val httpClient = HttpClient(client)
    val (yesterdayDate, dayAfterTomorrowDate) = getYesterdayAndDayAfterTomorrow()
    val selectedRegion = context.getSharedPreferences("WidgetPrefs", Context.MODE_PRIVATE)
        .getString("selectedRegion", "3011")

    val coord = context.getSharedPreferences("WidgetPrefs", Context.MODE_PRIVATE)
        .getString("fetchedCoord", null)

    var url = ""
    if (selectedRegion != null) {
        url =
            "https://api01.nve.no/hydrology/forecast/avalanche/v6.2.1/api/AvalancheWarningByRegion/Simple/$selectedRegion/$langCode/$yesterdayDate/$dayAfterTomorrowDate";

    } else if(coord != null) {
        val obj = JSONObject(coord)



        url =
            "https://api01.nve.no/hydrology/forecast/avalanche/v6.2.1/api/AvalancheWarningByCoordinates/Simple/${
                obj.get("lat")
            }/${obj.get("lng")}/$langCode/$yesterdayDate/$dayAfterTomorrowDate";
    }else{
        url = "https://api01.nve.no/hydrology/forecast/avalanche/v6.2.1/api/AvalancheWarningByRegion/Simple/3011/1/"
    }
    Log.d("URL", "URL: ${url}")
    // Update the widget views based on the selectedRegion

    httpClient.makeRequest(url) { response, error ->
        if (error != null) {
            Log.d("HTTPCLIENT", "ERROR: $error")
        } else {

            //Log.d("HTTPCLIENT", "Response: $response")
            val list: ArrayList<AvalanceReport>? = response?.let { parseJsonToArrayList(it) }
            if (list != null) {

                val nameOfDay = parseDayName(list[1].ValidFrom)
                val dateString = parseDateString(list[1].ValidFrom)

                smallView.setTextViewText(R.id.area_name, list[1].RegionName)
                smallView.setTextViewText(R.id.risk_number, list[1].DangerLevel)
                smallView.setTextViewText(R.id.widget_current_day, nameOfDay)
                mediumView.setTextViewText(R.id.area_name, list[1].RegionName)
                mediumView.setTextViewText(R.id.risk_number, list[1].DangerLevel)
                mediumView.setTextViewText(R.id.risk_description, list[1].MainText)
                mediumView.setTextViewText(R.id.date, dateString)
                largeView.setTextViewText(R.id.area_name, list[1].RegionName)
                largeView.setTextViewText(R.id.risk_number, list[1].DangerLevel)
                largeView.setTextViewText(R.id.risk_description, list[1].MainText)
                largeView.setTextViewText(R.id.date, dateString)
                largeView.setTextViewText(R.id.risk_yesterday, list[0].DangerLevel)
                largeView.setTextViewText(
                    R.id.item_date_yesterday,
                    parseAndFormatDate(list[0].ValidFrom)
                )
                largeView.setTextViewText(R.id.risk_today, list[1].DangerLevel)
                largeView.setTextViewText(
                    R.id.item_date_today,
                    parseAndFormatDate(list[1].ValidFrom)
                )
                largeView.setTextViewText(R.id.risk_tomorrow, list[2].DangerLevel)
                largeView.setTextViewText(
                    R.id.item_date_tomorrow,
                    parseAndFormatDate(list[2].ValidFrom)
                )
                largeView.setTextViewText(R.id.risk_dayafter, list[3].DangerLevel)
                largeView.setTextViewText(
                    R.id.item_date_dayafter,
                    parseAndFormatDate(list[3].ValidFrom)
                )


                val warningIcon = when (list[1].DangerLevel) {
                    "0" -> R.drawable.level_0
                    "1" -> R.drawable.level_1
                    "2" -> R.drawable.level_2
                    "3" -> R.drawable.level_3
                    "4" -> R.drawable.level_4
                    else -> R.drawable.level_0
                }

                smallView.setViewBackgroundResource(
                    R.id.card,
                    getRiskColor(list[1].DangerLevel)
                )
                smallView.setImageViewResource(R.id.risk_image, warningIcon)
                mediumView.setViewBackgroundResource(
                    R.id.card,
                    getRiskColor(list[1].DangerLevel)
                )
                mediumView.setImageViewResource(R.id.risk_image, warningIcon)
                largeView.setViewBackgroundResource(
                    R.id.card,
                    getRiskColor(list[1].DangerLevel)
                )
                largeView.setViewBackgroundResource(
                    R.id.risk_yesterday,
                    getRiskColor(list[0].DangerLevel)
                )
                largeView.setViewBackgroundResource(
                    R.id.risk_today,
                    getRiskColor(list[1].DangerLevel)
                )
                largeView.setViewBackgroundResource(
                    R.id.risk_tomorrow,
                    getRiskColor(list[2].DangerLevel)
                )
                largeView.setViewBackgroundResource(
                    R.id.risk_dayafter,
                    getRiskColor(list[3].DangerLevel)
                )
                largeView.setImageViewResource(R.id.risk_image, warningIcon)

                val viewMapping: Map<SizeF, RemoteViews> = mapOf(
                    SizeF(180f, 40f) to smallView,
                    SizeF(200f, 80f) to mediumView,
                    SizeF(270f, 200f) to largeView
                )
                val views = RemoteViews(viewMapping)


                val appWidgetManager = AppWidgetManager.getInstance(context)
                val componentName = ComponentName(context, varsom::class.java)
                appWidgetManager.updateAppWidget(componentName, views)
            }
        }
    }


    // Update other views or perform any necessary operations


}

fun parseDayName(dateString: String): String {
    val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
    val date = format.parse(dateString)
    val calendar = Calendar.getInstance()
    calendar.time = date
    val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
    val dayName =
        calendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault())

    return dayName
}

fun getYesterdayAndDayAfterTomorrow(): Pair<String, String> {
    // Get yesterday's date
    val yesterdayCalendar = Calendar.getInstance()
    yesterdayCalendar.add(Calendar.DAY_OF_MONTH, -1)
    val yesterdayDate = yesterdayCalendar.time
    val yesterdayFormatted = SimpleDateFormat("yyyy-MM-dd").format(yesterdayDate)

    // Get the date for the day after tomorrow
    val dayAfterTomorrowCalendar = Calendar.getInstance()
    dayAfterTomorrowCalendar.add(Calendar.DAY_OF_MONTH, 2)
    val dayAfterTomorrowDate = dayAfterTomorrowCalendar.time
    val dayAfterTomorrowFormatted = SimpleDateFormat("yyyy-MM-dd").format(dayAfterTomorrowDate)

    return Pair(yesterdayFormatted, dayAfterTomorrowFormatted)
}

fun getRiskColor(riskLevel: String): Int {

    val colorRes = when (riskLevel) {
        "1" -> R.drawable.warning_1
        "2" -> R.drawable.warning_2
        "3" -> R.drawable.warning_3
        "4" -> R.drawable.warning_4
        else -> R.drawable.warning_00
    }

    return colorRes
}

fun parseDateString(dateString: String): String {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
    val dateTime = LocalDateTime.parse(dateString, formatter)

    val dayOfWeek = dateTime.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    val month = dateTime.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
    val dayOfMonth = dateTime.dayOfMonth
    val year = dateTime.year

    return "$dayOfWeek, $month $dayOfMonth, $year"
}

fun parseAndFormatDate(dateString: String): String {
    val dateTime =
        LocalDateTime.parse(dateString, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
    return dateTime.format(DateTimeFormatter.ofPattern("dd/MM"))
}

fun parseJsonToArrayList(jsonString: String): ArrayList<AvalanceReport> {
    val gson = Gson()
    val listType = object : TypeToken<ArrayList<AvalanceReport>>() {}.type
    return gson.fromJson(jsonString, listType)
}

fun updateAPp(
    client: OkHttpClient,
    views: RemoteViews,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int
) {
    val httpClient = HttpClient(client)
    val regionId = 3011
    val url =
        "https://api01.nve.no/hydrology/forecast/avalanche/v6.2.1/api/AvalancheWarningByRegion/Simple/$regionId";
    // There may be multiple widgets active, so update all of them

    httpClient.makeRequest(url) { response, error ->
        if (error != null) {
            Log.d("HTTPCLIENT", "ERROR: $error")
        } else {
            Log.d("HTTPCLIENT", "Response: $response")
            val json = JSONArray(response).getJSONObject(0)
            Log.d("HTTPCLIENT", "Response: $json")
            views.setTextViewText(R.id.area_name, "TROMSØ")
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

}


internal fun updateAppWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int
) {
    val widgetText = "SENJA"
    // Construct the RemoteViews object
    //val views = RemoteViews(context.packageName, R.layout.varsom)
    //views.setTextViewText(R.id.area_name, widgetText)
    // Instruct the widget manager to update the widget
    //appWidgetManager.updateAppWidget(appWidgetId, views)
    val remoteViews = RemoteViews(context.packageName, R.layout.varsom_small).also {
        it.setTextViewText(R.id.area_name, widgetText)
    }
    appWidgetManager.partiallyUpdateAppWidget(appWidgetId, remoteViews)
}

