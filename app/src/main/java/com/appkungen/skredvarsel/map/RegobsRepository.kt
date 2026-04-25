package com.appkungen.skredvarsel.map

import android.util.Log
import com.appkungen.skredvarsel.NetworkModule
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Fetches recent snow/avalanche observations from Regobs.
 *
 * Uses the public v5 search endpoint. No auth required for reads.
 * Filters server-side by GeoHazard=10 (snow) + a date range; we keep the result count
 * bounded so the map doesn't choke on markers.
 */
class RegobsRepository {

    private val gson = Gson()
    private val client = NetworkModule.httpClient

    /**
     * Snow observations from the last [days] days. Capped at [maxRecords].
     */
    suspend fun recentSnowObservations(
        days: Int = 7,
        maxRecords: Int = 200
    ): Result<List<RegobsObservation>> = withContext(Dispatchers.IO) {
        try {
            val (from, to) = dateRange(days)
            val body = JsonObject().apply {
                add("SelectedGeoHazards", gson.toJsonTree(listOf(RegobsConstants.GEO_HAZARD_SNOW)))
                addProperty("FromDtObsTime", from)
                addProperty("ToDtObsTime", to)
                addProperty("NumberOfRecords", maxRecords)
                addProperty("Offset", 0)
                addProperty("LangKey", 1)
                addProperty("OrderBy", "DtObsTime")
            }

            val request = Request.Builder()
                .url(RegobsConstants.SEARCH_URL)
                .post(
                    body.toString().toRequestBody("application/json".toMediaType())
                )
                .header("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()
            response.use {
                if (!it.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("Regobs HTTP ${it.code}")
                    )
                }
                val text = it.body?.string()
                    ?: return@withContext Result.failure(Exception("Empty Regobs response"))
                val type = object : TypeToken<List<RegobsObservation>>() {}.type
                val list: List<RegobsObservation> = gson.fromJson(text, type) ?: emptyList()
                Log.d(TAG, "Regobs returned ${list.size} observations")
                Result.success(list.filter { obs -> obs.location?.latitude != null && obs.location.longitude != null })
            }
        } catch (e: Exception) {
            Log.e(TAG, "Regobs fetch failed", e)
            Result.failure(e)
        }
    }

    private fun dateRange(days: Int): Pair<String, String> {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val to = Calendar.getInstance()
        val from = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -days) }
        return fmt.format(from.time) to fmt.format(to.time)
    }

    companion object {
        private const val TAG = "RegobsRepo"
    }
}
