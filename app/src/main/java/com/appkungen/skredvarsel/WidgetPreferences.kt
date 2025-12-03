package com.appkungen.skredvarsel

import android.content.Context
import android.content.SharedPreferences

class WidgetPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        WidgetConstants.SHARED_PREFS_NAME,
        Context.MODE_PRIVATE
    )

    var selectedRegion: String?
        get() = prefs.getString(WidgetConstants.PREF_SELECTED_REGION, WidgetConstants.DEFAULT_REGION_ID)
        set(value) {
            prefs.edit().apply {
                putString(WidgetConstants.PREF_SELECTED_REGION, value)
                if (value != null) {
                    // Clear coordinates when setting a region
                    remove(WidgetConstants.PREF_FETCHED_COORD)
                }
                apply()
            }
        }

    var fetchedCoord: String?
        get() = prefs.getString(WidgetConstants.PREF_FETCHED_COORD, null)
        set(value) {
            prefs.edit().apply {
                putString(WidgetConstants.PREF_FETCHED_COORD, value)
                if (value != null) {
                    // Clear region when setting coordinates
                    remove(WidgetConstants.PREF_SELECTED_REGION)
                }
                apply()
            }
        }

    fun clear() {
        prefs.edit().clear().apply()
    }
}