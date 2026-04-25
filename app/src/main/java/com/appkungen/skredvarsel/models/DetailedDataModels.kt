package com.appkungen.skredvarsel.models

import com.google.gson.annotations.SerializedName

/**
 * Basic models (used across the app)
 */
data class Region(
    @SerializedName("Name") val Name: String,
    @SerializedName("Id") val Id: String,
    @SerializedName("image") val image: Int,
    @SerializedName("color") val color: Int,
    @SerializedName("TypeName") val TypeName: String,
    @SerializedName("AvalancheWarningList") val AvalancheWarningList: Array<AvalancheWarning>
)

data class AvalancheWarning(
    @SerializedName("DangerLevel") val DangerLevel: String
)

/**
 * Detailed models for the detail API endpoint
 * Used in ForecastFragment for showing detailed avalanche information
 */

data class DetailedAvalancheReport(
    @SerializedName("RegId") val regId: Int?,
    @SerializedName("RegionId") val regionId: Int?,
    @SerializedName("RegionName") val regionName: String,
    @SerializedName("RegionTypeName") val regionTypeName: String?,
    @SerializedName("DangerLevel") val dangerLevel: String,
    @SerializedName("ValidFrom") val validFrom: String,
    @SerializedName("ValidTo") val validTo: String?,
    @SerializedName("NextWarningTime") val nextWarningTime: String?,
    @SerializedName("PublishTime") val publishTime: String?,
    @SerializedName("MainText") val mainText: String?,
    @SerializedName("LangKey") val langKey: Int?,

    // Avalanche problems
    @SerializedName("AvalancheProblems") val avalancheProblems: List<AvalancheProblem>?,

    // Mountain weather
    @SerializedName("MountainWeather") val mountainWeather: MountainWeather?,

    // Avalanche danger
    @SerializedName("AvalancheDanger") val avalancheDanger: String?,

    // Snow surface
    @SerializedName("SnowSurface") val snowSurface: String?,

    // Current weak layers
    @SerializedName("CurrentWeakLayers") val currentWeakLayers: String?,

    // Latest avalanche activity
    @SerializedName("LatestAvalancheActivity") val latestAvalancheActivity: String?,

    // Latest observations
    @SerializedName("LatestObservations") val latestObservations: String?
)

data class AvalancheProblem(
    @SerializedName("AvalancheProblemId") val id: Int?,
    @SerializedName("AvalancheProblemTypeId") val typeId: Int?,
    @SerializedName("AvalancheProblemTypeName") val typeName: String?,
    @SerializedName("AvalCauseId") val causeId: Int?,
    @SerializedName("AvalCauseName") val causeName: String?,
    @SerializedName("AvalancheExtId") val extId: Int?,
    @SerializedName("AvalancheExtName") val extName: String?,
    @SerializedName("AvalTriggerSimpleId") val triggerId: Int?,
    @SerializedName("AvalTriggerSimpleName") val triggerName: String?,
    @SerializedName("DestructiveSizeExtId") val sizeId: Int?,
    @SerializedName("DestructiveSizeExtName") val sizeName: String?,
    @SerializedName("AvalPropagationId") val propagationId: Int?,
    @SerializedName("AvalPropagationName") val propagationName: String?,
    @SerializedName("ExposedHeight1") val exposedHeight1: Int?,
    @SerializedName("ExposedHeight2") val exposedHeight2: Int?,
    @SerializedName("ExposedHeightFill") val exposedHeightFill: Int?,
    @SerializedName("ValidExpositions") val validExpositions: String?,
    @SerializedName("ExposedClimateName") val exposedClimateName: String?
)

data class MountainWeather(
    @SerializedName("MountainWeatherText") val text: String?,
    @SerializedName("Comment") val comment: String?,
    @SerializedName("CloudCoverName") val cloudCover: String?,
    @SerializedName("PrecipitationName") val precipitation: String?,
    @SerializedName("WindSpeedName") val windSpeed: String?,
    @SerializedName("WindDirectionName") val windDirection: String?,
    @SerializedName("FreezingLevel") val freezingLevel: String?,
    @SerializedName("TemperatureMin") val temperatureMin: Int?,
    @SerializedName("TemperatureMax") val temperatureMax: Int?,
    @SerializedName("MeasurementTexts") val measurementTexts: List<SortableText>?,
    @SerializedName("MeasurementTypes") val measurementTypes: List<MeasurementType>?
)

data class SortableText(
    @SerializedName("SortOrder") val sortOrder: Int?,
    @SerializedName("Text") val text: String?
)

data class MeasurementType(
    @SerializedName("Id") val id: Int?,
    @SerializedName("Name") val name: String?,
    @SerializedName("SortOrder") val sortOrder: Int?,
    @SerializedName("MeasurementSubTypes") val subTypes: List<MeasurementSubType>?
)

data class MeasurementSubType(
    @SerializedName("Id") val id: Int?,
    @SerializedName("Name") val name: String?,
    @SerializedName("SortOrder") val sortOrder: Int?,
    @SerializedName("Value") val value: String?
)