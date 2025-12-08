package com.appkungen.skredvarsel.repository


import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.appkungen.skredvarsel.HttpClient
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit
import com.appkungen.skredvarsel.models.*

/**
 * Repository for managing avalanche forecast data
 * Handles caching, error handling, and data fetching
 */
class AvalancheForecastRepository(
    private val context: Context,
    private val httpClient: HttpClient = HttpClient()
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        CACHE_PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val gson = Gson()

    companion object {
        private const val CACHE_PREFS_NAME = "avalanche_forecast_cache"
        private const val CACHE_EXPIRY_HOURS = 1L
        private const val TAG = "AvalancheForecastRepo"

        // In-memory cache to prevent multiple widgets from making simultaneous requests
        private var memoryCache: CachedForecast? = null
        private val pendingRequests = mutableMapOf<String, Deferred<Result<List<AvalancheReport>>>>()
    }

    data class CachedForecast(
        val data: List<AvalancheReport>,
        val timestamp: Long,
        val cacheKey: String
    )

    sealed class Result<out T> {
        data class Success<T>(val data: T) : Result<T>()
        data class Error(val exception: Exception, val cachedData: List<AvalancheReport>? = null) : Result<Nothing>()
        object Loading : Result<Nothing>()
    }

    /**
     * Fetch forecast with caching and deduplication
     * Multiple simultaneous requests for the same region will share the same network call
     */
    suspend fun getForecast(
        regionId: String? = null,
        coordinates: Pair<Double, Double>? = null,
        forceRefresh: Boolean = false
    ): Result<List<AvalancheReport>> = withContext(Dispatchers.IO) {
        try {
            val cacheKey = buildCacheKey(regionId, coordinates)

            // Check in-memory cache first
            if (!forceRefresh) {
                memoryCache?.let { cached ->
                    if (cached.cacheKey == cacheKey && !isCacheExpired(cached.timestamp)) {
                        Log.d(TAG, "Returning from memory cache")
                        return@withContext Result.Success(cached.data)
                    }
                }

                // Check disk cache
                getCachedForecast(cacheKey)?.let { cached ->
                    if (!isCacheExpired(cached.timestamp)) {
                        Log.d(TAG, "Returning from disk cache")
                        memoryCache = cached
                        return@withContext Result.Success(cached.data)
                    }
                }
            }

            // Deduplicate pending requests
            pendingRequests[cacheKey]?.let { pending ->
                Log.d(TAG, "Waiting for pending request")
                return@withContext pending.await()
            }

            // Create new request
            val deferred = async {
                fetchFromNetwork(regionId, coordinates, cacheKey)
            }

            pendingRequests[cacheKey] = deferred

            try {
                deferred.await()
            } finally {
                pendingRequests.remove(cacheKey)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching forecast", e)

            // Try to return cached data even if expired
            val cacheKey = buildCacheKey(regionId, coordinates)
            val cachedData = getCachedForecast(cacheKey)?.data

            Result.Error(e, cachedData)
        }
    }

    private suspend fun fetchFromNetwork(
        regionId: String?,
        coordinates: Pair<Double, Double>?,
        cacheKey: String
    ): Result<List<AvalancheReport>> = suspendCancellableCoroutine { continuation ->
        val url = buildApiUrl(regionId, coordinates)

        Log.d(TAG, "Fetching from network: $url")

        val call = httpClient.makeRequest(url) { response, error ->
            if (error != null) {
                Log.e(TAG, "Network request failed", error)

                // Try to return cached data
                val cachedData = getCachedForecast(cacheKey)?.data
                continuation.resume(Result.Error(error, cachedData)) {}
            } else if (response != null) {
                try {
                    val data = parseResponse(response)

                    if (data.isEmpty()) {
                        continuation.resume(
                            Result.Error(
                                Exception("Empty response from API"),
                                getCachedForecast(cacheKey)?.data
                            )
                        ) {}
                    } else {
                        // Cache the successful response
                        cacheForecast(cacheKey, data)

                        // Update memory cache
                        memoryCache = CachedForecast(data, System.currentTimeMillis(), cacheKey)

                        continuation.resume(Result.Success(data)) {}
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing response", e)
                    continuation.resume(
                        Result.Error(e, getCachedForecast(cacheKey)?.data)
                    ) {}
                }
            }
        }

        continuation.invokeOnCancellation {
            call.cancel()
        }
    }

    private fun buildApiUrl(regionId: String?, coordinates: Pair<Double, Double>?): String {
        val baseUrl = "https://api01.nve.no/hydrology/forecast/avalanche/v6.2.1/api"
        val langCode = if (java.util.Locale.getDefault().toString().contains("nb")) 1 else 2

        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val yesterday = java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.DAY_OF_MONTH, -1)
        }
        val dayAfterTomorrow = java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.DAY_OF_MONTH, 2)
        }

        val yesterdayStr = dateFormat.format(yesterday.time)
        val dayAfterTomorrowStr = dateFormat.format(dayAfterTomorrow.time)

        return when {
            coordinates != null -> {
                "$baseUrl/AvalancheWarningByCoordinates/Simple/${coordinates.first}/${coordinates.second}/$langCode/$yesterdayStr/$dayAfterTomorrowStr"
            }
            regionId != null -> {
                "$baseUrl/AvalancheWarningByRegion/Simple/$regionId/$langCode/$yesterdayStr/$dayAfterTomorrowStr"
            }
            else -> {
                "$baseUrl/AvalancheWarningByRegion/Simple/3011/$langCode/$yesterdayStr/$dayAfterTomorrowStr"
            }
        }
    }

    private fun parseResponse(jsonString: String): List<AvalancheReport> {
        val listType = object : TypeToken<ArrayList<AvalancheReport>>() {}.type
        return gson.fromJson(jsonString, listType)
    }

    private fun buildCacheKey(regionId: String?, coordinates: Pair<Double, Double>?): String {
        return when {
            coordinates != null -> "coord_${coordinates.first}_${coordinates.second}"
            regionId != null -> "region_$regionId"
            else -> "region_3011"
        }
    }

    private fun getCachedForecast(cacheKey: String): CachedForecast? {
        val cachedJson = prefs.getString("forecast_$cacheKey", null) ?: return null
        val cachedTimestamp = prefs.getLong("timestamp_$cacheKey", 0)

        return try {
            val data: List<AvalancheReport> = gson.fromJson(
                cachedJson,
                object : TypeToken<ArrayList<AvalancheReport>>() {}.type
            )
            CachedForecast(data, cachedTimestamp, cacheKey)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading cache", e)
            null
        }
    }

    private fun cacheForecast(cacheKey: String, data: List<AvalancheReport>) {
        try {
            val json = gson.toJson(data)
            prefs.edit()
                .putString("forecast_$cacheKey", json)
                .putLong("timestamp_$cacheKey", System.currentTimeMillis())
                .apply()
            Log.d(TAG, "Cached forecast for $cacheKey")
        } catch (e: Exception) {
            Log.e(TAG, "Error caching forecast", e)
        }
    }

    private fun isCacheExpired(timestamp: Long): Boolean {
        val expiryTime = TimeUnit.HOURS.toMillis(CACHE_EXPIRY_HOURS)
        return System.currentTimeMillis() - timestamp > expiryTime
    }

    /**
     * Clear all cached data
     */
    fun clearCache() {
        prefs.edit().clear().apply()
        memoryCache = null
        Log.d(TAG, "Cache cleared")
    }

    /**
     * Get the timestamp of the last successful update
     */
    fun getLastUpdateTime(regionId: String?, coordinates: Pair<Double, Double>?): Long? {
        val cacheKey = buildCacheKey(regionId, coordinates)
        val timestamp = prefs.getLong("timestamp_$cacheKey", 0)
        return if (timestamp > 0) timestamp else null
    }
}

