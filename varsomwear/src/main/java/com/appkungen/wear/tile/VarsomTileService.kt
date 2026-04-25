package com.appkungen.wear.tile

import android.util.Log
import androidx.wear.protolayout.*
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.*
import androidx.wear.protolayout.LayoutElementBuilders.*
import androidx.wear.protolayout.ModifiersBuilders.*
import androidx.wear.protolayout.ResourceBuilders.*
import androidx.wear.protolayout.TimelineBuilders.*
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.TileService
import com.appkungen.wear.data.ApiConstants
import com.appkungen.wear.data.AvalancheReport
import com.appkungen.wear.data.ForecastRepository
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Wear OS Tile som viser skredvarsel.
 *
 * Viser faregrad med fargekode, regionnavn og kort beskrivelse.
 * Oppdateres hver time automatisk.
 */
class VarsomTileService : TileService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var repo: ForecastRepository

    companion object {
        private const val TAG = "VarsomTile"
        private const val RESOURCES_VERSION = "1"

        // Refresh interval: 1 time
        private const val REFRESH_INTERVAL_MS = 3600000L
    }

    override fun onCreate() {
        super.onCreate()
        repo = ForecastRepository(applicationContext)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<Tile> {
        return serviceScope.future {
            try {
                val result = repo.getForecast()
                val forecasts = result.getOrNull()

                if (forecasts != null && forecasts.size >= 2) {
                    val today = forecasts[1] // Index 1 = i dag
                    buildTile(today, forecasts)
                } else {
                    buildErrorTile("Kunne ikke hente varsel")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Feil i tile request", e)
                buildErrorTile("Feil ved lasting")
            }
        }
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ListenableFuture<Resources> {
        return Futures.immediateFuture(
            Resources.Builder()
                .setVersion(RESOURCES_VERSION)
                .build()
        )
    }

    private fun buildTile(today: AvalancheReport, forecasts: List<AvalancheReport>): Tile {
        val dangerColor = ApiConstants.getDangerColor(today.dangerLevel)
        val dangerText = ApiConstants.getDangerText(today.dangerLevel)

        val layout = Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setModifiers(
                Modifiers.Builder()
                    .setBackground(
                        Background.Builder()
                            .setColor(argb(dangerColor))
                            .setCorner(
                                Corner.Builder()
                                    .setRadius(dp(999f)) // Rund for klokke-display
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .addContent(
                Column.Builder()
                    .setWidth(expand())
                    .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
                    .setModifiers(
                        Modifiers.Builder()
                            .setPadding(
                                Padding.Builder()
                                    .setTop(dp(24f))
                                    .setBottom(dp(16f))
                                    .setStart(dp(20f))
                                    .setEnd(dp(20f))
                                    .build()
                            )
                            .build()
                    )
                    // Faregrad-tall (stort)
                    .addContent(
                        Text.Builder()
                            .setText(today.dangerLevel)
                            .setFontStyle(
                                FontStyle.Builder()
                                    .setSize(sp(48f))
                                    .setWeight(FONT_WEIGHT_BOLD)
                                    .setColor(argb(0xFF000000.toInt()))
                                    .build()
                            )
                            .build()
                    )
                    // Faregrads-beskrivelse
                    .addContent(
                        Text.Builder()
                            .setText(dangerText)
                            .setFontStyle(
                                FontStyle.Builder()
                                    .setSize(sp(14f))
                                    .setWeight(FONT_WEIGHT_BOLD)
                                    .setColor(argb(0xFF000000.toInt()))
                                    .build()
                            )
                            .build()
                    )
                    // Regionnavn
                    .addContent(
                        Spacer.Builder().setHeight(dp(6f)).build()
                    )
                    .addContent(
                        Text.Builder()
                            .setText(today.regionName)
                            .setFontStyle(
                                FontStyle.Builder()
                                    .setSize(sp(13f))
                                    .setColor(argb(0xDD000000.toInt()))
                                    .build()
                            )
                            .setMaxLines(1)
                            .build()
                    )
                    // Dato
                    .addContent(
                        Text.Builder()
                            .setText(formatDate(today.validFrom))
                            .setFontStyle(
                                FontStyle.Builder()
                                    .setSize(sp(11f))
                                    .setColor(argb(0xAA000000.toInt()))
                                    .build()
                            )
                            .build()
                    )
                    // Mini-tidslinje (3 dager)
                    .addContent(
                        Spacer.Builder().setHeight(dp(8f)).build()
                    )
                    .addContent(buildForecastTimeline(forecasts))
                    .build()
            )
            .build()

        val timeline = Timeline.Builder()
            .addTimelineEntry(
                TimelineEntry.Builder()
                    .setLayout(Layout.Builder().setRoot(layout).build())
                    .build()
            )
            .build()

        return Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(timeline)
            .setFreshnessIntervalMillis(REFRESH_INTERVAL_MS)
            .build()
    }

    /**
     * Bygger en liten tidslinje med 3 kommende dager
     */
    private fun buildForecastTimeline(forecasts: List<AvalancheReport>): LayoutElement {
        val row = Row.Builder()
            .setVerticalAlignment(VERTICAL_ALIGN_CENTER)

        // Vis dag 1-3 (index 1, 2, 3)
        val count = minOf(3, forecasts.size - 1)
        for (i in 1..count) {
            if (i > 1) {
                row.addContent(Spacer.Builder().setWidth(dp(6f)).build())
            }

            val forecast = forecasts[i]
            val color = ApiConstants.getDangerColor(forecast.dangerLevel)
            val dayLabel = if (i == 1) "I dag" else formatShortDay(forecast.validFrom)

            row.addContent(
                Column.Builder()
                    .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
                    .addContent(
                        // Liten farget boks med tall
                        Box.Builder()
                            .setWidth(dp(28f))
                            .setHeight(dp(20f))
                            .setModifiers(
                                Modifiers.Builder()
                                    .setBackground(
                                        Background.Builder()
                                            .setColor(argb(color))
                                            .setCorner(
                                                Corner.Builder()
                                                    .setRadius(dp(4f))
                                                    .build()
                                            )
                                            .build()
                                    )
                                    .build()
                            )
                            .addContent(
                                Text.Builder()
                                    .setText(forecast.dangerLevel)
                                    .setFontStyle(
                                        FontStyle.Builder()
                                            .setSize(sp(12f))
                                            .setWeight(FONT_WEIGHT_BOLD)
                                            .setColor(argb(0xFF000000.toInt()))
                                            .build()
                                    )
                                    .build()
                            )
                            .build()
                    )
                    .addContent(
                        Text.Builder()
                            .setText(dayLabel)
                            .setFontStyle(
                                FontStyle.Builder()
                                    .setSize(sp(9f))
                                    .setColor(argb(0xAA000000.toInt()))
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
        }

        return row.build()
    }

    private fun buildErrorTile(message: String): Tile {
        val layout = Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setModifiers(
                Modifiers.Builder()
                    .setBackground(
                        Background.Builder()
                            .setColor(argb(0xFF333333.toInt()))
                            .build()
                    )
                    .build()
            )
            .addContent(
                Column.Builder()
                    .setWidth(expand())
                    .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
                    .addContent(
                        Text.Builder()
                            .setText("Varsom")
                            .setFontStyle(
                                FontStyle.Builder()
                                    .setSize(sp(16f))
                                    .setWeight(FONT_WEIGHT_BOLD)
                                    .setColor(argb(0xFFFFFFFF.toInt()))
                                    .build()
                            )
                            .build()
                    )
                    .addContent(
                        Spacer.Builder().setHeight(dp(8f)).build()
                    )
                    .addContent(
                        Text.Builder()
                            .setText(message)
                            .setFontStyle(
                                FontStyle.Builder()
                                    .setSize(sp(12f))
                                    .setColor(argb(0xAAFFFFFF.toInt()))
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()

        val timeline = Timeline.Builder()
            .addTimelineEntry(
                TimelineEntry.Builder()
                    .setLayout(Layout.Builder().setRoot(layout).build())
                    .build()
            )
            .build()

        return Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(timeline)
            .setFreshnessIntervalMillis(300000) // Prøv igjen om 5 min ved feil
            .build()
    }

    private fun formatDate(validFrom: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("d. MMMM", Locale.forLanguageTag("no"))
            val date = inputFormat.parse(validFrom)
            outputFormat.format(date!!)
        } catch (e: Exception) {
            validFrom.take(10)
        }
    }

    private fun formatShortDay(validFrom: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("EEE", Locale.forLanguageTag("no"))
            val date = inputFormat.parse(validFrom)
            outputFormat.format(date!!).replaceFirstChar { it.uppercase() }
        } catch (e: Exception) {
            "?"
        }
    }
}

/**
 * Hjelpefunksjon for å bruke coroutines med ListenableFuture
 */
fun <T> CoroutineScope.future(
    block: suspend CoroutineScope.() -> T
): ListenableFuture<T> {
    val future = com.google.common.util.concurrent.SettableFuture.create<T>()
    launch {
        try {
            future.set(block())
        } catch (e: Exception) {
            future.setException(e)
        }
    }
    return future
}
