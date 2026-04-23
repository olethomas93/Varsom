package com.appkungen.wear.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class ForecastRepository(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "varsom_wear_cache", Context.MODE_PRIVATE
    )

    companion object {
        private const val TAG = "VarsomWearRepo"
        private const val CACHE_KEY = "cached_forecast"
        private const val CACHE_TIMESTAMP = "cache_timestamp"
        private const val PREF_REGION_ID = "selected_region_id"
        private const val PREF_REGION_NAME = "selected_region_name"
        private const val CACHE_DURATION_MS = 60 * 60 * 1000L // 1 time
    }

    /**
     * Hent varsel - bruker cache hvis fersk nok
     */
    suspend fun getForecast(forceRefresh: Boolean = false): Result<List<AvalancheReport>> =
        withContext(Dispatchers.IO) {
            try {
                // Sjekk cache først
                if (!forceRefresh) {
                    getCachedForecast()?.let { cached ->
                        Log.d(TAG, "Returnerer fra cache")
                        return@withContext Result.success(cached)
                    }
                }

                val regionId = getSelectedRegionId()
                val url = buildApiUrl(regionId)

                Log.d(TAG, "Henter fra nett: $url")

                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    // Prøv cache som fallback
                    val cached = getCachedForecast(ignoreExpiry = true)
                    return@withContext if (cached != null) {
                        Result.success(cached)
                    } else {
                        Result.failure(Exception("HTTP ${response.code}"))
                    }
                }

                val body = response.body?.string() ?: return@withContext Result.failure(
                    Exception("Tom respons")
                )

                val type = object : TypeToken<List<AvalancheReport>>() {}.type
                val forecasts: List<AvalancheReport> = gson.fromJson(body, type)

                // Lagre i cache
                saveForecastToCache(body)

                Result.success(forecasts)
            } catch (e: Exception) {
                Log.e(TAG, "Feil ved henting av varsel", e)
                val cached = getCachedForecast(ignoreExpiry = true)
                if (cached != null) {
                    Result.success(cached)
                } else {
                    Result.failure(e)
                }
            }
        }

    private fun buildApiUrl(regionId: String): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val calendar = Calendar.getInstance()

        calendar.add(Calendar.DAY_OF_YEAR, -1)
        val yesterday = dateFormat.format(calendar.time)

        calendar.add(Calendar.DAY_OF_YEAR, 4) // -1 + 4 = +3 from today
        val dayAfterTomorrow = dateFormat.format(calendar.time)

        val lang = if (Locale.getDefault().language == "nb" || Locale.getDefault().language == "no") {
            ApiConstants.LANG_CODE_NORWEGIAN
        } else {
            ApiConstants.LANG_CODE_ENGLISH
        }

        return "${ApiConstants.API_BASE_URL}/AvalancheWarningByRegion/Simple/$regionId/$lang/$yesterday/$dayAfterTomorrow"
    }

    private fun getCachedForecast(ignoreExpiry: Boolean = false): List<AvalancheReport>? {
        val timestamp = prefs.getLong(CACHE_TIMESTAMP, 0)
        if (!ignoreExpiry && System.currentTimeMillis() - timestamp > CACHE_DURATION_MS) {
            return null
        }

        val json = prefs.getString(CACHE_KEY, null) ?: return null
        return try {
            val type = object : TypeToken<List<AvalancheReport>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            null
        }
    }

    private fun saveForecastToCache(json: String) {
        prefs.edit()
            .putString(CACHE_KEY, json)
            .putLong(CACHE_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }

    fun getSelectedRegionId(): String {
        return prefs.getString(PREF_REGION_ID, ApiConstants.DEFAULT_REGION_ID)
            ?: ApiConstants.DEFAULT_REGION_ID
    }

    fun getSelectedRegionName(): String {
        return prefs.getString(PREF_REGION_NAME, "Tromsø") ?: "Tromsø"
    }

    fun setSelectedRegion(id: String, name: String) {
        prefs.edit()
            .putString(PREF_REGION_ID, id)
            .putString(PREF_REGION_NAME, name)
            .apply()
    }
}
