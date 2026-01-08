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

