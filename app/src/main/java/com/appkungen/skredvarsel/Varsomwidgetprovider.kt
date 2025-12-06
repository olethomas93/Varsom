package com.appkungen.skredvarsel

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import com.appkungen.skredvarsel.repository.AvalancheForecastRepository
import com.appkungen.varsomwidget.R
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*
import com.appkungen.skredvarsel.models.*

/**
 * Improved widget provider with proper error handling and lifecycle management
 */
class VarsomWidgetProvider : AppWidgetProvider() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var repository: AvalancheForecastRepository? = null

    companion object {
        private const val TAG = "VarsomWidget"
        const val ACTION_REFRESH = "com.appkungen.skredvarsel.REFRESH"
        const val ACTION_OPEN_DETAIL = "com.appkungen.skredvarsel.OPEN_DETAIL"

        // Track ongoing updates to prevent duplicate requests
        private val ongoingUpdates = mutableSetOf<Int>()
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)

        Log.d(TAG, "onUpdate called for ${appWidgetIds.size} widgets")

        appWidgetIds.forEach { appWidgetId ->
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Log.d(TAG, "Widget enabled")

        // Initialize repository
        repository = AvalancheForecastRepository(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        Log.d(TAG, "Widget disabled")

        // Clean up
        scope.cancel()
        repository = null
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)

        Log.d(TAG, "Widget options changed for widget $appWidgetId")
        updateWidget(context, appWidgetManager, appWidgetId)
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        super.onReceive(context, intent)

        if (context == null || intent == null) return

        when (intent.action) {
            ACTION_REFRESH -> {
                val appWidgetId = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
                )

                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    Log.d(TAG, "Manual refresh triggered for widget $appWidgetId")
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    updateWidget(context, appWidgetManager, appWidgetId, forceRefresh = true)
                }
            }

            WidgetConstants.ACTION_SET_REGION -> {
                val selectedRegion = intent.getStringExtra("selectedRegion")
                if (selectedRegion != null) {
                    Log.d(TAG, "Region updated to $selectedRegion")
                    val widgetPrefs = WidgetPreferences(context)
                    widgetPrefs.selectedRegion = selectedRegion

                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    val appWidgetIds = appWidgetManager.getAppWidgetIds(
                        ComponentName(context, VarsomWidgetProvider::class.java)
                    )
                    onUpdate(context, appWidgetManager, appWidgetIds)
                }
            }

            WidgetConstants.ACTION_SET_COORD -> {
                val coord = intent.getStringExtra("fetchedCoord")
                if (coord != null) {
                    Log.d(TAG, "Coordinates updated")
                    val widgetPrefs = WidgetPreferences(context)
                    widgetPrefs.fetchedCoord = coord

                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    val appWidgetIds = appWidgetManager.getAppWidgetIds(
                        ComponentName(context, VarsomWidgetProvider::class.java)
                    )
                    onUpdate(context, appWidgetManager, appWidgetIds)
                }
            }
        }
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        forceRefresh: Boolean = false
    ) {
        // Prevent duplicate updates
        if (ongoingUpdates.contains(appWidgetId)) {
            Log.d(TAG, "Update already in progress for widget $appWidgetId")
            return
        }

        ongoingUpdates.add(appWidgetId)

        // Show loading state
        showLoadingState(context, appWidgetManager, appWidgetId)

        // Fetch data
        scope.launch {
            try {
                val repo = repository ?: AvalancheForecastRepository(context)
                val widgetPrefs = WidgetPreferences(context)

                // Determine location
                val coordinates = widgetPrefs.fetchedCoord?.let { coordString ->
                    try {
                        val json = org.json.JSONObject(coordString)
                        Pair(
                            json.getDouble("lat"),
                            json.getDouble("lng")
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing coordinates", e)
                        null
                    }
                }

                val regionId = widgetPrefs.selectedRegion

                // Fetch forecast
                when (val result = repo.getForecast(regionId, coordinates, forceRefresh)) {
                    is AvalancheForecastRepository.Result.Success -> {
                        Log.d(TAG, "Successfully fetched forecast")
                        showForecast(context, appWidgetManager, appWidgetId, result.data)
                    }

                    is AvalancheForecastRepository.Result.Error -> {
                        Log.e(TAG, "Error fetching forecast", result.exception)

                        if (result.cachedData != null) {
                            // Show cached data with warning
                            showForecast(
                                context,
                                appWidgetManager,
                                appWidgetId,
                                result.cachedData,
                                isStale = true
                            )
                        } else {
                            // Show error state
                            showErrorState(
                                context,
                                appWidgetManager,
                                appWidgetId,
                                getErrorMessage(result.exception)
                            )
                        }
                    }

                    else -> {
                        Log.w(TAG, "Unexpected result type")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error updating widget", e)
                showErrorState(context, appWidgetManager, appWidgetId, "Uventet feil")
            } finally {
                ongoingUpdates.remove(appWidgetId)
            }
        }
    }

    private fun showLoadingState(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_loading)
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun showForecast(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        forecasts: List<AvalancheReport>,
        isStale: Boolean = false
    ) {
        if (forecasts.size < 2) {
            showErrorState(context, appWidgetManager, appWidgetId, "Ikke nok data")
            return
        }

        // Get appropriate layout based on widget size
        val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
        val layoutId = selectLayout(options)

        val views = RemoteViews(context.packageName, layoutId)

        // Today's forecast (index 1)
        val today = forecasts[1]

        // Populate widget views
        populateWidgetViews(views, today, forecasts, isStale)

        // Set up click handlers
        setupClickHandlers(context, views, appWidgetId, forecasts)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun showErrorState(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        message: String
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_error)

        views.setTextViewText(R.id.error_message, message)

        // Show last update time if available
        val repo = repository ?: AvalancheForecastRepository(context)
        val widgetPrefs = WidgetPreferences(context)

        val coordinates = widgetPrefs.fetchedCoord?.let { coordString ->
            try {
                val json = org.json.JSONObject(coordString)
                Pair(json.getDouble("lat"), json.getDouble("lng"))
            } catch (e: Exception) {
                null
            }
        }

        val lastUpdate = repo.getLastUpdateTime(widgetPrefs.selectedRegion, coordinates)
        if (lastUpdate != null) {
            val timeAgo = getTimeAgo(lastUpdate)
            views.setTextViewText(R.id.last_update, "Sist oppdatert: $timeAgo")
        }

        // Set up retry click
        val retryIntent = Intent(context, VarsomWidgetProvider::class.java).apply {
            action = ACTION_REFRESH
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }

        val retryPendingIntent = PendingIntent.getBroadcast(
            context,
            appWidgetId,
            retryIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        views.setOnClickPendingIntent(R.id.root, retryPendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun selectLayout(options: Bundle): Int {
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        val maxWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
        val maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)

        val width = maxWidth.coerceAtLeast(minWidth)
        val height = maxHeight.coerceAtLeast(minHeight)

        return when {
            width >= 330 && height >= 140 -> R.layout.varsom_large2
            width >= 330 -> R.layout.varsom_medium
            width >= 180 && height >= 180 -> R.layout.varsom_small2
            else -> R.layout.varsom_small
        }
    }

    private fun populateWidgetViews(
        views: RemoteViews,
        today: AvalancheReport,
        forecasts: List<AvalancheReport>,
        isStale: Boolean
    ) {
        // Set main forecast data
        views.setTextViewText(R.id.risk_number, today.DangerLevel)
        views.setTextViewText(R.id.area_name, today.RegionName)
        views.setTextViewText(R.id.widget_current_day, parseDayName(today.ValidFrom))
        views.setTextViewText(R.id.risk_description, today.MainText)
        views.setTextViewText(R.id.date, parseDateString(today.ValidFrom))

        // Set icon
        val icon = DangerLevelMapper.getLevelIcon(today.DangerLevel)
        views.setImageViewResource(R.id.risk_image, icon)

        // Set background color
        val bgColor = DangerLevelMapper.getWarningDrawable(today.DangerLevel)
        views.setInt(R.id.root, "setBackgroundResource", bgColor)

        // Set forecast timeline if available in layout
        if (forecasts.size >= 4) {
            try {
                views.setTextViewText(R.id.risk_yesterday, forecasts[0].DangerLevel)
                views.setTextViewText(R.id.item_date_yesterday, parseAndFormatDate(forecasts[0].ValidFrom))
                views.setInt(R.id.risk_yesterday, "setBackgroundResource",
                    DangerLevelMapper.getWarningDrawable(forecasts[0].DangerLevel))

                views.setTextViewText(R.id.risk_today, forecasts[1].DangerLevel)
                views.setTextViewText(R.id.item_date_today, parseAndFormatDate(forecasts[1].ValidFrom))
                views.setInt(R.id.risk_today, "setBackgroundResource",
                    DangerLevelMapper.getWarningDrawable(forecasts[1].DangerLevel))

                views.setTextViewText(R.id.risk_tomorrow, forecasts[2].DangerLevel)
                views.setTextViewText(R.id.item_date_tomorrow, parseAndFormatDate(forecasts[2].ValidFrom))
                views.setInt(R.id.risk_tomorrow, "setBackgroundResource",
                    DangerLevelMapper.getWarningDrawable(forecasts[2].DangerLevel))

                views.setTextViewText(R.id.risk_dayafter, forecasts[3].DangerLevel)
                views.setTextViewText(R.id.item_date_dayafter, parseAndFormatDate(forecasts[3].ValidFrom))
                views.setInt(R.id.risk_dayafter, "setBackgroundResource",
                    DangerLevelMapper.getWarningDrawable(forecasts[3].DangerLevel))
            } catch (e: Exception) {
                Log.w(TAG, "Could not set forecast timeline", e)
            }
        }
    }

    private fun setupClickHandlers(
        context: Context,
        views: RemoteViews,
        appWidgetId: Int,
        forecasts: List<AvalancheReport>
    ) {
        val gson = com.google.gson.Gson()
        val forecastJson = gson.toJson(forecasts)

        val intent = Intent(context, ForecastDetailActivity::class.java).apply {
            putExtra("forecastJson", forecastJson)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        views.setOnClickPendingIntent(R.id.root, pendingIntent)
    }

    private fun getErrorMessage(exception: Exception): String {
        return when {
            exception is java.net.UnknownHostException -> "Ingen internettforbindelse"
            exception is java.net.SocketTimeoutException -> "Forespørselen tok for lang tid"
            exception.message?.contains("404") == true -> "Data ikke funnet"
            exception.message?.contains("500") == true -> "Serverfeil"
            else -> "Kunne ikke laste data"
        }
    }

    private fun getTimeAgo(timestamp: Long): String {
        val diff = System.currentTimeMillis() - timestamp
        val minutes = diff / (1000 * 60)
        val hours = diff / (1000 * 60 * 60)
        val days = diff / (1000 * 60 * 60 * 24)

        return when {
            minutes < 1 -> "Nå nettopp"
            minutes < 60 -> "$minutes min siden"
            hours < 24 -> "$hours timer siden"
            days == 1L -> "I går"
            else -> "$days dager siden"
        }
    }
}

