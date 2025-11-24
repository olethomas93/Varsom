package com.northei.skredvarsel

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
import android.util.SizeF
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.northei.varsomwidget.R
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.*

class AvalancheReport(
    var RegionName: String,
    var DangerLevel: String,
    var image: Int,
    var color: Int,
    var MainText: String,
    var ValidFrom: String,
    var PublishTime: String
)

class varsom : AppWidgetProvider() {

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidgetViews(context, appWidgetId)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onEnabled(context: Context) {
        Log.d("Widget", "Widget enabled")
        updateAllWidgets(context)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateWidgetViews(context, appWidgetId)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onReceive(context: Context?, intent: Intent?) {
        super.onReceive(context, intent)
        
        if (context == null || intent == null) return

        when (intent.action) {
            WidgetConstants.ACTION_DOUBLE_CLICK -> {
                openBrowser(context, WidgetConstants.VARSOM_URL)
            }
            WidgetConstants.ACTION_SET_REGION -> {
                handleRegionUpdate(context, intent)
            }
            WidgetConstants.ACTION_SET_COORD -> {
                handleCoordUpdate(context, intent)
            }
        }
    }

    private fun handleRegionUpdate(context: Context, intent: Intent) {
        val selectedRegion = intent.getStringExtra("selectedRegion") ?: return
        
        val widgetPrefs = WidgetPreferences(context)
        widgetPrefs.selectedRegion = selectedRegion
        
        updateAllWidgets(context)
    }

    private fun handleCoordUpdate(context: Context, intent: Intent) {
        val coord = intent.getStringExtra("fetchedCoord") ?: return
        
        val widgetPrefs = WidgetPreferences(context)
        widgetPrefs.fetchedCoord = coord
        
        updateAllWidgets(context)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun updateAllWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, varsom::class.java)
        )
        
        for (appWidgetId in appWidgetIds) {
            updateWidgetViews(context, appWidgetId)
        }
    }

    private fun openBrowser(context: Context, url: String) {
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(browserIntent)
    }
}

@RequiresApi(Build.VERSION_CODES.S)
private fun updateWidgetViews(context: Context, appWidgetId: Int) {
    val appWidgetManager = AppWidgetManager.getInstance(context)
    val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
    
    // Get widget dimensions
    val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
    val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
    val maxWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
    val maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
    
    Log.d("Widget", "Widget size - minW: $minWidth, minH: $minHeight, maxW: $maxWidth, maxH: $maxHeight")
    
    val widgetPrefs = WidgetPreferences(context)
    val httpClient = HttpClient(NetworkModule.httpClient)
    
    val lang = Locale.getDefault()
    val langCode = if (lang.toString().contains("nb")) {
        WidgetConstants.LANG_CODE_NORWEGIAN
    } else {
        WidgetConstants.LANG_CODE_ENGLISH
    }
    
    val (yesterdayDate, dayAfterTomorrowDate) = getYesterdayAndDayAfterTomorrow()
    
    val url = buildApiUrl(widgetPrefs, langCode, yesterdayDate, dayAfterTomorrowDate)
    
    Log.d("Widget", "Fetching from URL: $url")
    
    httpClient.makeRequest(url) { response, error ->
        if (error != null) {
            Log.e("Widget", "Failed to fetch data", error)
            showErrorState(context, appWidgetId, minWidth, minHeight)
        } else {
            response?.let {
                updateWidgetWithData(context, appWidgetId, it, minWidth, minHeight)
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
}

private fun showErrorState(context: Context, appWidgetId: Int, width: Int, height: Int) {
    val layoutId = determineLayout(width, height)
    val views = RemoteViews(context.packageName, layoutId)
    
    views.setTextViewText(R.id.area_name, "Error")
    views.setTextViewText(R.id.risk_number, "-")
    
    val appWidgetManager = AppWidgetManager.getInstance(context)
    appWidgetManager.updateAppWidget(appWidgetId, views)
}

@RequiresApi(Build.VERSION_CODES.S)
private fun updateWidgetWithData(
    context: Context,
    appWidgetId: Int,
    jsonResponse: String,
    width: Int,
    height: Int
) {
    val list: ArrayList<AvalancheReport>? = parseJsonToArrayList(jsonResponse)
    
    if (list == null || list.size < 4) {
        Log.e("Widget", "Invalid data received")
        showErrorState(context, appWidgetId, width, height)
        return
    }
    
    // Create views for different sizes
    val smallView = createSmallView(context, list)
    val mediumView = createMediumView(context, list)
    val largeView = createLargeView(context, list)
    
    // Add click handler
    val intent = Intent(context, varsom::class.java)
    intent.action = WidgetConstants.ACTION_DOUBLE_CLICK
    
    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        PendingIntent.FLAG_IMMUTABLE
    } else {
        0
    }
    
    val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, flags)
    smallView.setOnClickPendingIntent(R.id.card, pendingIntent)
    mediumView.setOnClickPendingIntent(R.id.card, pendingIntent)
    largeView.setOnClickPendingIntent(R.id.card, pendingIntent)
    
    // Create responsive layout mapping with better size thresholds
    val viewMapping: Map<SizeF, RemoteViews> = mapOf(
        SizeF(0f, 0f) to smallView,                    // Smallest size
        SizeF(WidgetConstants.SMALL_WIDTH_THRESHOLD, WidgetConstants.SMALL_HEIGHT_THRESHOLD) to mediumView,
        SizeF(WidgetConstants.MEDIUM_WIDTH_THRESHOLD, WidgetConstants.MEDIUM_HEIGHT_THRESHOLD) to largeView
    )
    
    val views = RemoteViews(viewMapping)
    
    val appWidgetManager = AppWidgetManager.getInstance(context)
    appWidgetManager.updateAppWidget(appWidgetId, views)
}

private fun determineLayout(width: Int, height: Int): Int {
    return when {
        width >= WidgetConstants.MEDIUM_WIDTH_THRESHOLD && height >= WidgetConstants.MEDIUM_HEIGHT_THRESHOLD -> R.layout.varsom_large2
        width >= WidgetConstants.SMALL_WIDTH_THRESHOLD && height >= WidgetConstants.SMALL_HEIGHT_THRESHOLD -> R.layout.varsom_medium
        else -> R.layout.varsom_small
    }
}

private fun createSmallView(context: Context, list: ArrayList<AvalancheReport>): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.varsom_small)
    
    val today = list[1]
    val nameOfDay = parseDayName(today.ValidFrom)
    val warningIcon = DangerLevelMapper.getLevelIcon(today.DangerLevel)
    
    views.setTextViewText(R.id.area_name, today.RegionName)
    views.setTextViewText(R.id.risk_number, today.DangerLevel)
    views.setTextViewText(R.id.widget_current_day, nameOfDay)
    views.setViewBackgroundResource(R.id.card, DangerLevelMapper.getWarningDrawable(today.DangerLevel))
    views.setImageViewResource(R.id.risk_image, warningIcon)
    
    return views
}

private fun createMediumView(context: Context, list: ArrayList<AvalancheReport>): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.varsom_medium)
    
    val today = list[1]
    val dateString = parseDateString(today.ValidFrom)
    val warningIcon = DangerLevelMapper.getLevelIcon(today.DangerLevel)
    
    views.setTextViewText(R.id.area_name, today.RegionName)
    views.setTextViewText(R.id.risk_number, today.DangerLevel)
    views.setTextViewText(R.id.risk_description, today.MainText)
    views.setTextViewText(R.id.date, dateString)
    views.setViewBackgroundResource(R.id.card, DangerLevelMapper.getWarningDrawable(today.DangerLevel))
    views.setImageViewResource(R.id.risk_image, warningIcon)
    
    return views
}

private fun createLargeView(context: Context, list: ArrayList<AvalancheReport>): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.varsom_large2)
    
    val today = list[1]
    val dateString = parseDateString(today.ValidFrom)
    val warningIcon = DangerLevelMapper.getLevelIcon(today.DangerLevel)
    
    // Set main content
    views.setTextViewText(R.id.area_name, today.RegionName)
    views.setTextViewText(R.id.risk_number, today.DangerLevel)
    views.setTextViewText(R.id.risk_description, today.MainText)
    views.setTextViewText(R.id.date, dateString)
    
    // Set 4-day forecast
    views.setTextViewText(R.id.risk_yesterday, list[0].DangerLevel)
    views.setTextViewText(R.id.item_date_yesterday, parseAndFormatDate(list[0].ValidFrom))
    views.setViewBackgroundResource(R.id.risk_yesterday, DangerLevelMapper.getWarningDrawable(list[0].DangerLevel))
    
    views.setTextViewText(R.id.risk_today, list[1].DangerLevel)
    views.setTextViewText(R.id.item_date_today, parseAndFormatDate(list[1].ValidFrom))
    views.setViewBackgroundResource(R.id.risk_today, DangerLevelMapper.getWarningDrawable(list[1].DangerLevel))
    
    views.setTextViewText(R.id.risk_tomorrow, list[2].DangerLevel)
    views.setTextViewText(R.id.item_date_tomorrow, parseAndFormatDate(list[2].ValidFrom))
    views.setViewBackgroundResource(R.id.risk_tomorrow, DangerLevelMapper.getWarningDrawable(list[2].DangerLevel))
    
    views.setTextViewText(R.id.risk_dayafter, list[3].DangerLevel)
    views.setTextViewText(R.id.item_date_dayafter, parseAndFormatDate(list[3].ValidFrom))
    views.setViewBackgroundResource(R.id.risk_dayafter, DangerLevelMapper.getWarningDrawable(list[3].DangerLevel))
    
    views.setViewBackgroundResource(R.id.card, DangerLevelMapper.getWarningDrawable(today.DangerLevel))
    views.setImageViewResource(R.id.risk_image, warningIcon)
    
    return views
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
    val dateTime = LocalDateTime.parse(
        dateString,
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    )
    return dateTime.format(DateTimeFormatter.ofPattern("dd/MM"))
}

fun parseJsonToArrayList(jsonString: String): ArrayList<AvalancheReport> {
    val gson = Gson()
    val listType = object : TypeToken<ArrayList<AvalancheReport>>() {}.type
    return gson.fromJson(jsonString, listType)
}