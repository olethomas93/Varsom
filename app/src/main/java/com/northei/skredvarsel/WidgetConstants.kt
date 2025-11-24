package com.northei.skredvarsel

object WidgetConstants {
    const val DEFAULT_REGION_ID = "3011"
    const val SHARED_PREFS_NAME = "WidgetPrefs"
    const val PREF_SELECTED_REGION = "selectedRegion"
    const val PREF_FETCHED_COORD = "fetchedCoord"
    const val ACTION_SET_REGION = "setRegion"
    const val ACTION_SET_COORD = "setCoord"
    const val ACTION_DOUBLE_CLICK = "com.northei.skredvarsel.DOUBLE_CLICK"
    const val VARSOM_URL = "https://www.varsom.no"
    const val API_BASE_URL = "https://api01.nve.no/hydrology/forecast/avalanche/v6.2.1/api"
    const val LANG_CODE_NORWEGIAN = 1
    const val LANG_CODE_ENGLISH = 2
    
    // Widget size thresholds in dp
    const val SMALL_WIDTH_THRESHOLD = 140f
    const val MEDIUM_WIDTH_THRESHOLD = 220f
    const val SMALL_HEIGHT_THRESHOLD = 100f
    const val MEDIUM_HEIGHT_THRESHOLD = 180f
    
    // Permission request codes
    const val LOCATION_PERMISSION_REQUEST = 1
    const val GPS_ENABLE_REQUEST = 2
}

object DangerLevelMapper {
    fun getWarningDrawable(dangerLevel: String): Int {
        return when (dangerLevel) {
            "0" -> com.northei.varsomwidget.R.drawable.warning_00
            "1" -> com.northei.varsomwidget.R.drawable.warning_1
            "2" -> com.northei.varsomwidget.R.drawable.warning_2
            "3" -> com.northei.varsomwidget.R.drawable.warning_3
            "4" -> com.northei.varsomwidget.R.drawable.warning_4
            else -> com.northei.varsomwidget.R.drawable.warning_00
        }
    }
    
    fun getLevelIcon(dangerLevel: String): Int {
        return when (dangerLevel) {
            "0" -> com.northei.varsomwidget.R.drawable.level_0
            "1" -> com.northei.varsomwidget.R.drawable.level_1
            "2" -> com.northei.varsomwidget.R.drawable.level_2
            "3" -> com.northei.varsomwidget.R.drawable.level_3
            "4" -> com.northei.varsomwidget.R.drawable.level_4
            else -> com.northei.varsomwidget.R.drawable.level_0
        }
    }
}