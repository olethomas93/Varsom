package com.appkungen.skredvarsel.models

/**
 * Data models for avalanche forecasts
 */

data class AvalancheReport(
    val RegionName: String,
    val DangerLevel: String,
    val MainText: String,
    val ValidFrom: String,
    val PublishTime: String
)

data class Region(
    val Name: String,
    val Id: String,
    val image: Int,
    val color: Int,
    val TypeName: String,
    val AvalancheWarningList: Array<AvalancheWarning>
)

data class AvalancheWarning(
    val DangerLevel: String
)