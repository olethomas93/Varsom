package com.appkungen.skredvarsel

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.SizeF
import android.widget.RemoteViews
import com.appkungen.skredvarsel.repository.AvalancheForecastRepository
import com.appkungen.varsomwidget.R
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import com.appkungen.skredvarsel.models.*

/**
 * Improved widget provider with proper error handling and lifecycle management
 */
class VarsomWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "VarsomWidget"
        const val ACTION_REFRESH = "com.appkungen.skredvarsel.REFRESH"
        const val ACTION_OPEN_DETAIL = "com.appkungen.skredvarsel.OPEN_DETAIL"

        // Below this height we hide the 4-day timeline row in varsom_horizontal,
        // so a wide-but-short widget (≈4×1 cells) still looks clean.
        private const val TIMELINE_MIN_HEIGHT_DP = 140

        // Shared scope — AppWidgetProvider instances are short-lived BroadcastReceivers,
        // so the scope must outlive any one instance.
        private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        // Track ongoing updates to prevent duplicate requests
        private val ongoingUpdates: MutableSet<Int> = ConcurrentHashMap.newKeySet()
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)

        Log.d(TAG, "onUpdate called for ${appWidgetIds.size} widgets")

        // goAsync keeps the process alive past onReceive return so the network
        // fetch in updateWidget can complete.
        val pendingResult = goAsync()
        scope.launch {
            try {
                appWidgetIds.forEach { appWidgetId ->
                    updateWidget(context, appWidgetManager, appWidgetId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Log.d(TAG, "Widget enabled")
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        Log.d(TAG, "Widget disabled")
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        Log.d(TAG, "Widget $appWidgetId resized")
        launchUpdate(appWidgetManager, intArrayOf(appWidgetId), context, forceRefresh = false)
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) {
            super.onReceive(context, intent)
            return
        }

        when (intent.action) {
            ACTION_REFRESH -> {
                val appWidgetId = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
                )
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    Log.d(TAG, "Manual refresh triggered for widget $appWidgetId")
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    launchUpdate(appWidgetManager, intArrayOf(appWidgetId), context, forceRefresh = true)
                }
            }

            WidgetConstants.ACTION_SET_REGION -> {
                val selectedRegion = intent.getStringExtra("selectedRegion")
                if (selectedRegion != null) {
                    Log.d(TAG, "Region updated to $selectedRegion")
                    WidgetPreferences(context).selectedRegion = selectedRegion
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    val ids = appWidgetManager.getAppWidgetIds(
                        ComponentName(context, VarsomWidgetProvider::class.java)
                    )
                    launchUpdate(appWidgetManager, ids, context, forceRefresh = true)
                }
            }

            WidgetConstants.ACTION_SET_COORD -> {
                val coord = intent.getStringExtra("fetchedCoord")
                if (coord != null) {
                    Log.d(TAG, "Coordinates updated")
                    WidgetPreferences(context).fetchedCoord = coord
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    val ids = appWidgetManager.getAppWidgetIds(
                        ComponentName(context, VarsomWidgetProvider::class.java)
                    )
                    launchUpdate(appWidgetManager, ids, context, forceRefresh = true)
                }
            }

            else -> super.onReceive(context, intent)
        }
    }

    private fun launchUpdate(
        appWidgetManager: AppWidgetManager,
        ids: IntArray,
        context: Context,
        forceRefresh: Boolean
    ) {
        val pendingResult = goAsync()
        scope.launch {
            try {
                ids.forEach { id ->
                    updateWidget(context, appWidgetManager, id, forceRefresh)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        forceRefresh: Boolean = false
    ) {
        if (!ongoingUpdates.add(appWidgetId)) {
            Log.d(TAG, "Update already in progress for widget $appWidgetId")
            return
        }

        showLoadingState(context, appWidgetManager, appWidgetId)

        try {
            val repo = AvalancheForecastRepository(context)
            val widgetPrefs = WidgetPreferences(context)

            val coordinates = widgetPrefs.fetchedCoord?.let { parseCoordinates(it) }
            val regionId = widgetPrefs.selectedRegion

            when (val result = repo.getForecast(regionId, coordinates, forceRefresh)) {
                is AvalancheForecastRepository.Result.Success -> {
                    Log.d(TAG, "Successfully fetched forecast")
                    showForecast(context, appWidgetManager, appWidgetId, result.data)
                }

                is AvalancheForecastRepository.Result.Error -> {
                    Log.e(TAG, "Error fetching forecast", result.exception)
                    if (result.cachedData != null) {
                        showForecast(
                            context, appWidgetManager, appWidgetId,
                            result.cachedData, isStale = true
                        )
                    } else {
                        showErrorState(
                            context, appWidgetManager, appWidgetId,
                            getErrorMessage(result.exception)
                        )
                    }
                }

                else -> Log.w(TAG, "Unexpected result type")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error updating widget", e)
            showErrorState(context, appWidgetManager, appWidgetId, "Uventet feil")
        } finally {
            ongoingUpdates.remove(appWidgetId)
        }
    }

    private fun parseCoordinates(coordString: String): Pair<Double, Double>? {
        return try {
            val json = org.json.JSONObject(coordString)
            Pair(json.getDouble("lat"), json.getDouble("lng"))
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing coordinates", e)
            null
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

        val today = forecasts[1]
        val options = appWidgetManager.getAppWidgetOptions(appWidgetId)

        // On API 31+ the launcher can ask the widget to render at multiple sizes
        // (portrait, landscape, lock screen, …). Provide one RemoteViews per size and
        // let the system pick. Falls back to a single layout on older devices.
        val sizes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getWidgetSizes(options)
        } else {
            emptyList()
        }

        val remoteViews = if (sizes.isNotEmpty()) {
            val byLayout = sizes.associateWith { size ->
                buildRemoteViewsForSize(
                    context, size.width.toInt(), size.height.toInt(),
                    today, forecasts, appWidgetId, isStale
                )
            }
            RemoteViews(byLayout)
        } else {
            val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
            val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)
            buildRemoteViewsForSize(
                context, width, height, today, forecasts, appWidgetId, isStale
            )
        }

        appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
    }

    private fun buildRemoteViewsForSize(
        context: Context,
        widthDp: Int,
        heightDp: Int,
        today: AvalancheReport,
        forecasts: List<AvalancheReport>,
        appWidgetId: Int,
        isStale: Boolean
    ): RemoteViews {
        val layoutId = selectLayout(widthDp, heightDp)
        Log.d(TAG, "Selected layout for ${widthDp}×${heightDp}dp → ${context.resources.getResourceEntryName(layoutId)}")
        val views = RemoteViews(context.packageName, layoutId)
        populateWidgetViews(views, today, forecasts, isStale, heightDp)
        setupClickHandlers(context, views, appWidgetId, forecasts)
        return views
    }

    @Suppress("DEPRECATION")
    private fun getWidgetSizes(options: Bundle): List<SizeF> {
        val key = AppWidgetManager.OPTION_APPWIDGET_SIZES
        val list: ArrayList<SizeF>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            options.getParcelableArrayList(key, SizeF::class.java)
        } else {
            options.getParcelableArrayList(key)
        }
        return list ?: emptyList()
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
        val repo = AvalancheForecastRepository(context)
        val widgetPrefs = WidgetPreferences(context)
        val coordinates = widgetPrefs.fetchedCoord?.let { parseCoordinates(it) }
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


    /**
     * Pick a layout from raw dp dimensions of the area we'll render in.
     *
     * Three layouts cover everything; the horizontal one toggles its timeline row at runtime
     * via [TIMELINE_MIN_HEIGHT_DP] so we don't need a separate "wide & short" XML.
     *
     *   width ≥ 250  → varsom_horizontal  (timeline shown if height ≥ TIMELINE_MIN_HEIGHT_DP)
     *   width ≥ 160  → varsom_small2
     *   else         → varsom_small
     */
    private fun selectLayout(widthDp: Int, heightDp: Int): Int = when {
        widthDp >= 250 -> R.layout.varsom_horizontal
        widthDp >= 160 -> R.layout.varsom_small2
        else           -> R.layout.varsom_small
    }

    private fun populateWidgetViews(
        views: RemoteViews,
        today: AvalancheReport,
        forecasts: List<AvalancheReport>,
        isStale: Boolean,
        heightDp: Int
    ) {
        // Set main forecast data
        views.setTextViewText(R.id.risk_number, today.DangerLevel)
        views.setTextViewText(R.id.area_name, today.RegionName)
        views.setTextViewText(R.id.widget_current_day, parseDayName(today.ValidFrom))
        views.setTextViewText(R.id.risk_description, today.MainText)
        views.setTextViewText(R.id.date, parseDateString(today.ValidFrom))

        val icon = DangerLevelMapper.getLevelIcon(today.DangerLevel)
        views.setImageViewResource(R.id.risk_image, icon)

        val bgColor = DangerLevelMapper.getWarningDrawable(today.DangerLevel)
        views.setInt(R.id.root, "setBackgroundResource", bgColor)

        // Timeline row (varsom_horizontal only). Hidden when the widget is too short
        // to render it cleanly, or when we don't have 4 days of data.
        val showTimeline = forecasts.size >= 4 && heightDp >= TIMELINE_MIN_HEIGHT_DP
        try {
            views.setViewVisibility(
                R.id.timeline_row,
                if (showTimeline) android.view.View.VISIBLE else android.view.View.GONE
            )
        } catch (_: Exception) {
            // Layout doesn't have timeline_row — fine, the small layouts don't.
        }

        if (showTimeline) {
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

