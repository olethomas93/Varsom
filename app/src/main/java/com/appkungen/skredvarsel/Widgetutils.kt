package com.appkungen.skredvarsel

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*
import com.appkungen.skredvarsel.models.*


/**
 * Utility functions for widget data formatting
 */

fun parseDayName(dateString: String): String {
    val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
    val date = format.parse(dateString) ?: return ""
    val calendar = Calendar.getInstance()
    calendar.time = date
    return calendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault()) ?: ""
}

fun parseDateString(dateString: String): String {
    val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
    val dateTime = java.time.LocalDateTime.parse(dateString, formatter)
    val dayOfWeek = dateTime.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())
    val month = dateTime.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())
    return "$dayOfWeek, $month ${dateTime.dayOfMonth}, ${dateTime.year}"
}

fun parseAndFormatDate(dateString: String): String {
    val dateTime = java.time.LocalDateTime.parse(
        dateString,
        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    )
    return dateTime.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM"))
}

fun getYesterdayAndDayAfterTomorrow(): Pair<String, String> {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    val yesterday = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_MONTH, -1)
    }
    val yesterdayFormatted = dateFormat.format(yesterday.time)

    val dayAfterTomorrow = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_MONTH, 2)
    }
    val dayAfterTomorrowFormatted = dateFormat.format(dayAfterTomorrow.time)

    return Pair(yesterdayFormatted, dayAfterTomorrowFormatted)
}

fun parseJsonToArrayList(jsonString: String): ArrayList<AvalancheReport> {
    val gson = Gson()
    val listType = object : TypeToken<ArrayList<AvalancheReport>>() {}.type
    return gson.fromJson(jsonString, listType)
}