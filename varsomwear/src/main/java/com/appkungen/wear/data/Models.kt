package com.appkungen.wear.data

import com.google.gson.annotations.SerializedName

/**
 * Speiler datamodellene fra telefon-appen
 */
data class AvalancheReport(
    @SerializedName("RegionName") val regionName: String,
    @SerializedName("DangerLevel") val dangerLevel: String,
    @SerializedName("MainText") val mainText: String,
    @SerializedName("ValidFrom") val validFrom: String,
    @SerializedName("PublishTime") val publishTime: String
)

object ApiConstants {
    const val API_BASE_URL = "https://api01.nve.no/hydrology/forecast/avalanche/v6.2.1/api"
    const val DEFAULT_REGION_ID = "3011"
    const val LANG_CODE_NORWEGIAN = 1
    const val LANG_CODE_ENGLISH = 2

    // Wear OS farger (uten alpha for runde displayer)
    const val COLOR_LEVEL_0 = 0xFF888888.toInt()  // Grå - Ikke vurdert
    const val COLOR_LEVEL_1 = 0xFF6BF198.toInt()  // Grønn - Liten
    const val COLOR_LEVEL_2 = 0xFFFFD046.toInt()  // Gul - Moderat
    const val COLOR_LEVEL_3 = 0xFFFF9A24.toInt()  // Oransje - Betydelig
    const val COLOR_LEVEL_4 = 0xFFFF3131.toInt()  // Rød - Stor

    fun getDangerColor(level: String): Int = when (level) {
        "1" -> COLOR_LEVEL_1
        "2" -> COLOR_LEVEL_2
        "3" -> COLOR_LEVEL_3
        "4" -> COLOR_LEVEL_4
        else -> COLOR_LEVEL_0
    }

    fun getDangerText(level: String): String = when (level) {
        "1" -> "Liten"
        "2" -> "Moderat"
        "3" -> "Betydelig"
        "4" -> "Stor"
        "5" -> "Meget stor"
        else -> "Ikke vurdert"
    }
}
