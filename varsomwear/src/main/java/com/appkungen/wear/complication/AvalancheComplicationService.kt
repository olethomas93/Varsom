package com.appkungen.wear.complication

import android.util.Log
import androidx.wear.watchface.complications.data.*
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import com.appkungen.wear.data.ApiConstants
import com.appkungen.wear.data.ForecastRepository
import kotlinx.coroutines.runBlocking

/**
 * Complication som viser faregrad direkte på urskiven.
 *
 * Støtter tre typer:
 * - SHORT_TEXT: "3" med fargekode
 * - RANGED_VALUE: Visuell bue 0-5 med faregrad
 * - LONG_TEXT: "Betydelig (3) - Tromsø"
 */
class AvalancheComplicationService : ComplicationDataSourceService() {

    companion object {
        private const val TAG = "VarsomComplication"
    }

    override fun onComplicationRequest(
        request: ComplicationRequest,
        listener: ComplicationRequestListener
    ) {
        Log.d(TAG, "Complication request type: ${request.complicationType}")

        val repo = ForecastRepository(applicationContext)

        // Hent varsel (blokkerende — complications kjører i bakgrunnen)
        val result = runBlocking {
            repo.getForecast()
        }

        val forecasts = result.getOrNull()
        if (forecasts == null || forecasts.size < 2) {
            listener.onComplicationData(null)
            return
        }

        val today = forecasts[1]
        val dangerLevel = today.dangerLevel
        val dangerText = ApiConstants.getDangerText(dangerLevel)
        val levelInt = dangerLevel.toIntOrNull() ?: 0

        val complicationData = when (request.complicationType) {
            ComplicationType.SHORT_TEXT -> {
                ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder("$dangerLevel").build(),
                    contentDescription = PlainComplicationText.Builder(
                        "Snøskredfare $dangerText"
                    ).build()
                )
                    .setTitle(
                        PlainComplicationText.Builder("Skred").build()
                    )
                    .build()
            }

            ComplicationType.RANGED_VALUE -> {
                RangedValueComplicationData.Builder(
                    value = levelInt.toFloat(),
                    min = 0f,
                    max = 5f,
                    contentDescription = PlainComplicationText.Builder(
                        "Snøskredfare nivå $dangerLevel: $dangerText"
                    ).build()
                )
                    .setText(
                        PlainComplicationText.Builder(dangerLevel).build()
                    )
                    .setTitle(
                        PlainComplicationText.Builder(dangerText).build()
                    )
                    .build()
            }

            ComplicationType.LONG_TEXT -> {
                LongTextComplicationData.Builder(
                    text = PlainComplicationText.Builder(
                        "$dangerText ($dangerLevel) - ${today.regionName}"
                    ).build(),
                    contentDescription = PlainComplicationText.Builder(
                        "Snøskredfare ${today.regionName}: $dangerText, nivå $dangerLevel"
                    ).build()
                )
                    .setTitle(
                        PlainComplicationText.Builder("Skredvarsel").build()
                    )
                    .build()
            }

            else -> null
        }

        listener.onComplicationData(complicationData)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        return when (type) {
            ComplicationType.SHORT_TEXT -> {
                ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder("3").build(),
                    contentDescription = PlainComplicationText.Builder(
                        "Snøskredfare Betydelig"
                    ).build()
                )
                    .setTitle(PlainComplicationText.Builder("Skred").build())
                    .build()
            }

            ComplicationType.RANGED_VALUE -> {
                RangedValueComplicationData.Builder(
                    value = 3f,
                    min = 0f,
                    max = 5f,
                    contentDescription = PlainComplicationText.Builder(
                        "Snøskredfare nivå 3"
                    ).build()
                )
                    .setText(PlainComplicationText.Builder("3").build())
                    .setTitle(PlainComplicationText.Builder("Betydelig").build())
                    .build()
            }

            ComplicationType.LONG_TEXT -> {
                LongTextComplicationData.Builder(
                    text = PlainComplicationText.Builder(
                        "Betydelig (3) - Tromsø"
                    ).build(),
                    contentDescription = PlainComplicationText.Builder(
                        "Skredvarsel forhåndsvisning"
                    ).build()
                )
                    .setTitle(PlainComplicationText.Builder("Skredvarsel").build())
                    .build()
            }

            else -> null
        }
    }
}
