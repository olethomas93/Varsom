package com.appkungen.skredvarsel.models

import com.google.gson.annotations.SerializedName

/**
 * Data models for avalanche forecasts
 */

data class AvalancheReport(
    @SerializedName("RegionName") val RegionName: String,
    @SerializedName("DangerLevel") val DangerLevel: String,
    @SerializedName("MainText") val MainText: String,
    @SerializedName("ValidFrom") val ValidFrom: String,
    @SerializedName("PublishTime") val PublishTime: String
)

