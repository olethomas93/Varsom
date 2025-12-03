package com.appkungen.skredvarsel

import HttpClient
import NetworkModule
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
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.appkungen.varsomwidget.R
import okhttp3.OkHttpClient
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

class varsom : AppWidgetProvider() {

    private var areaName: String? = null

    @RequiresApi(Build.VERSION_CODES.S)
    private val client = OkHttpClient()

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onUpdate(
        context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)

        appWidgetIds.forEach { appWidgetId ->
            updateWidgetViews(context, appWidgetManager, appWidgetId, client)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val appWidgetIds =
            appWidgetManager.getAppWidgetIds(ComponentName(context, varsom::class.java))
        appWidgetIds.forEach { appWidgetId ->
            updateWidgetViews(context, appWidgetManager, appWidgetId, client)
        }
    }

    private fun openBrowser(context: Context, url: String) {
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(browserIntent)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onAppWidgetOptionsChanged(
        context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateWidgetViews(context, appWidgetManager, appWidgetId, client)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onReceive(context: Context?, intent: Intent?) {
        super.onReceive(context, intent)
        if (intent != null && context != null) {

            if (intent.action != null && intent.action.equals(WidgetConstants.ACTION_DOUBLE_CLICK)) {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val url = intent.getStringExtra("URL") ?: WidgetConstants.VARSOM_URL
                openBrowser(context, url)
            }

            if (intent.action == WidgetConstants.ACTION_SET_REGION) {
                val selectedRegion = intent.getStringExtra("selectedRegion")

                if (selectedRegion != null) {
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    val appWidgetIds = appWidgetManager.getAppWidgetIds(
                        ComponentName(context, varsom::class.java)
                    )

                    val widgetPrefs = WidgetPreferences(context)
                    widgetPrefs.selectedRegion = selectedRegion

                    onUpdate(context, appWidgetManager, appWidgetIds)
                }
            } else if (intent.action == WidgetConstants.ACTION_SET_COORD) {
                Log.d("COORD", "POSITION: SETTING COORDS")

                val coord = intent.getStringExtra("fetchedCoord")

                if (coord != null) {
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    val appWidgetIds = appWidgetManager.getAppWidgetIds(
                        ComponentName(context, varsom::class.java)
                    )

                    val widgetPrefs = WidgetPreferences(context)
                    widgetPrefs.fetchedCoord = coord

                    onUpdate(context, appWidgetManager, appWidgetIds)

                    Log.d("COORD", coord)
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun updateWidgetViews(
        context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, client: OkHttpClient
    ) {
        val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        val maxWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
        val maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)

        Log.d("WidgetSize", "========================================")
        Log.d("WidgetSize", "Widget ID: $appWidgetId")
        Log.d("WidgetSize", "Min: ${minWidth}dp x ${minHeight}dp")
        Log.d("WidgetSize", "Max: ${maxWidth}dp x ${maxHeight}dp")

        // Use max dimensions for better layout selection
        val width = maxWidth.coerceAtLeast(minWidth)
        val height = maxHeight.coerceAtLeast(minHeight)

        Log.d("WidgetSize", "Using: ${width}dp x ${height}dp")

        val layoutId = when {
            width >= 330 && height >= 140 -> {
                Log.d("WidgetSize", "✓ LARGE layout")
                R.layout.varsom_large2
            }

            width >= 330 -> {
                Log.d("WidgetSize", "✓ MEDIUM layout")
                R.layout.varsom_medium
            }

            width >= 180 && height >= 180 -> {
                Log.d("WidgetSize", "✓ 2×2 SMALL layout (varsom_small2)")
                R.layout.varsom_small2
            }

            else -> {
                Log.d("WidgetSize", "✓ 1×1 SMALL layout (varsom_small)")
                R.layout.varsom_small
            }
        }

        Log.d("WidgetSize", "========================================")

        val views = RemoteViews(context.packageName, layoutId)
        fetchDataAndUpdateWidget(context, views, appWidgetManager, appWidgetId, client)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun fetchDataAndUpdateWidget(
        context: Context,
        views: RemoteViews,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        client: OkHttpClient
    ) {
        val lang = Locale.getDefault()
        var langCode = 2
        Log.d("onUpdate", "LANGUAGE: $lang")
        if (lang.toString().contains("nb")) {
            langCode = 1
        }

        val httpClient = HttpClient(client)
        val (yesterdayDate, dayAfterTomorrowDate) = getYesterdayAndDayAfterTomorrow()
        val widgetPrefs = WidgetPreferences(context)

        val selectedRegion = widgetPrefs.selectedRegion
        val coord = widgetPrefs.fetchedCoord

        Log.d("DEBUG", "selected region: $selectedRegion")
        Log.d("DEBUG", "selected coord: $coord")

        val url = when {
            coord != null -> {
                val obj = JSONObject(coord)
                "${WidgetConstants.API_BASE_URL}/AvalancheWarningByCoordinates/Simple/${obj.get("lat")}/${obj.get("lng")}/$langCode/$yesterdayDate/$dayAfterTomorrowDate"
            }
            selectedRegion != null -> {
                "${WidgetConstants.API_BASE_URL}/AvalancheWarningByRegion/Simple/$selectedRegion/$langCode/$yesterdayDate/$dayAfterTomorrowDate"
            }
            else -> {
                "${WidgetConstants.API_BASE_URL}/AvalancheWarningByRegion/Simple/${WidgetConstants.DEFAULT_REGION_ID}/$langCode/$yesterdayDate/$dayAfterTomorrowDate"
            }
        }

        Log.d("DEBUG", "URL: $url")

        try {
            httpClient.makeRequest(url) { response, error ->
                if (error != null) {
                    Log.e("DEBUG", "ERROR: $error")
                } else if (response != null) {
                    Log.d("DEBUG", "Response: ok")
                    val list: ArrayList<AvalanceReport>? = parseJsonToArrayList(response)
                    if (list != null && list.size >= 4) {
                        val fetchedAreaName = list[1].RegionName
                        Log.d("DEBUG", "fetchedAreaName: $fetchedAreaName")

                        updateWidgetViews(context, appWidgetManager, appWidgetId, views, list)

                        val detailUrl = "https://www.varsom.no/snoskred/varsling/varsel/$fetchedAreaName"

                        val intent = Intent(context, ForecastDetailActivity::class.java)
                        intent.putExtra("forecastJson", response)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

                        val pendingIntent = PendingIntent.getActivity(
                            context,
                            appWidgetId,
                            intent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )

                        views.setOnClickPendingIntent(R.id.root, pendingIntent)





                        appWidgetManager.updateAppWidget(appWidgetId, views)
                    } else {
                        Log.d("DEBUG", "Response: No data available in the response")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("DEBUG", "Exception in fetchDataAndUpdateWidget", e)
        }
    }

    private fun updateWidgetViews(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        views: RemoteViews,
        list: ArrayList<AvalanceReport>
    ) {
        Log.d("DEBUG", "UPDATING WIDGET VIEWS")

        val nameOfDay = parseDayName(list[1].ValidFrom)
        val dateString = parseDateString(list[1].ValidFrom)
        // Shorten long region names for small widgets
        // Determine selected layout
        val isSmallWidget = views.layoutId == R.layout.varsom_small

        val region = list[1].RegionName


        views.setTextViewText(R.id.area_name, region)

        // Set text for all layouts

        views.setTextViewText(R.id.risk_number, list[1].DangerLevel)
        views.setTextViewText(R.id.widget_current_day, nameOfDay)
        views.setTextViewText(R.id.risk_description, list[1].MainText)
        views.setTextViewText(R.id.date, dateString)

        // Set forecast data (for large layout)
        views.setTextViewText(R.id.risk_yesterday, list[0].DangerLevel)
        views.setTextViewText(R.id.item_date_yesterday, parseAndFormatDate(list[0].ValidFrom))
        views.setTextViewText(R.id.risk_today, list[1].DangerLevel)
        views.setTextViewText(R.id.item_date_today, parseAndFormatDate(list[1].ValidFrom))
        views.setTextViewText(R.id.risk_tomorrow, list[2].DangerLevel)
        views.setTextViewText(R.id.item_date_tomorrow, parseAndFormatDate(list[2].ValidFrom))
        views.setTextViewText(R.id.risk_dayafter, list[3].DangerLevel)
        views.setTextViewText(R.id.item_date_dayafter, parseAndFormatDate(list[3].ValidFrom))

        val warningIcon = DangerLevelMapper.getLevelIcon(list[1].DangerLevel)

        // Set background colors using setInt for compatibility
        views.setInt(R.id.root, "setBackgroundResource", DangerLevelMapper.getWarningDrawable(list[1].DangerLevel))
        views.setImageViewResource(R.id.risk_image, warningIcon)

        views.setInt(R.id.risk_yesterday, "setBackgroundResource", DangerLevelMapper.getWarningDrawable(list[0].DangerLevel))
        views.setInt(R.id.risk_today, "setBackgroundResource", DangerLevelMapper.getWarningDrawable(list[1].DangerLevel))
        views.setInt(R.id.risk_tomorrow, "setBackgroundResource", DangerLevelMapper.getWarningDrawable(list[2].DangerLevel))
        views.setInt(R.id.risk_dayafter, "setBackgroundResource", DangerLevelMapper.getWarningDrawable(list[3].DangerLevel))

        //appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}

// Utility functions
fun parseDayName(dateString: String): String {
    val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
    val date = format.parse(dateString) ?: return ""
    val calendar = Calendar.getInstance()
    calendar.time = date
    return calendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault()) ?: ""
}

fun getYesterdayAndDayAfterTomorrow(): Pair<String, String> {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    val yesterday = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_MONTH, -1)
    }
    val yesterdayFormatted = dateFormat.format(yesterday.time)

    val dayAfterTomorrow = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_MONTH, 2)
    }
    val dayAfterTomorrowFormatted = dateFormat.format(dayAfterTomorrow.time)

    return Pair(yesterdayFormatted, dayAfterTomorrowFormatted)
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
    val dateTime = LocalDateTime.parse(dateString, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
    return dateTime.format(DateTimeFormatter.ofPattern("dd/MM"))
}

fun parseJsonToArrayList(jsonString: String): ArrayList<AvalanceReport> {
    val gson = Gson()
    val listType = object : TypeToken<ArrayList<AvalanceReport>>() {}.type
    return gson.fromJson(jsonString, listType)
}