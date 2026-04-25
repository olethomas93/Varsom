package com.appkungen.skredvarsel.map

import com.google.gson.annotations.SerializedName

/**
 * Subset of Regobs v5 RegistrationViewModel — just the fields we need for map markers
 * and the bottom-sheet summary. Everything else is ignored by Gson (lenient parsing).
 */
data class RegobsObservation(
    @SerializedName("RegId") val regId: Long,
    @SerializedName("DtObsTime") val dtObsTime: String?,
    @SerializedName("DtRegTime") val dtRegTime: String?,
    @SerializedName("ObsLocation") val location: ObsLocation?,
    @SerializedName("Observer") val observer: Observer?,
    @SerializedName("GeoHazardTID") val geoHazardTid: Int?,
    @SerializedName("GeoHazardName") val geoHazardName: String?,
    @SerializedName("Summaries") val summaries: List<Summary>?,
    @SerializedName("Url") val url: String?
)

data class ObsLocation(
    @SerializedName("Latitude") val latitude: Double?,
    @SerializedName("Longitude") val longitude: Double?,
    @SerializedName("MunicipalName") val municipalName: String?,
    @SerializedName("ForecastRegionName") val forecastRegionName: String?
)

data class Observer(
    @SerializedName("NickName") val nickName: String?,
    @SerializedName("ObserverGroupName") val observerGroupName: String?
)

data class Summary(
    @SerializedName("RegistrationName") val registrationName: String?,
    @SerializedName("RegistrationTID") val registrationTid: Int?,
    @SerializedName("Summary") val summary: String?
)

object RegobsConstants {
    const val GEO_HAZARD_SNOW = 10
    const val SEARCH_URL = "https://api.regobs.no/v5/Search"
    /** Public web URL for an individual observation — used in "open full" action. */
    fun observationUrl(regId: Long): String =
        "https://www.regobs.no/Registration/$regId"
}
