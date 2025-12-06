package com.appkungen.skredvarsel

import HttpClient
import NetworkModule
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.appkungen.varsomwidget.R
import com.google.android.material.appbar.MaterialToolbar
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import java.util.*
import com.appkungen.skredvarsel.models.*


// Detailed models for the detail API
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

class ForecastDetailActivity : AppCompatActivity() {

    private lateinit var list: ArrayList<AvalancheReport>
    private lateinit var detailedList: ArrayList<DetailedAvalancheReport>
    private var selectedDayIndex = 1
    private val httpClient = HttpClient(NetworkModule.httpClient)
    private var regionId: String = "3011"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forecast_detail)

        val toolbar = findViewById<MaterialToolbar>(R.id.detail_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        val json = intent.getStringExtra("forecastJson") ?: return
        list = parseJsonToArrayList(json)

        if (list.isNotEmpty()) {
            val widgetPrefs = WidgetPreferences(this)
            regionId = widgetPrefs.selectedRegion ?: "3011"
        }

        setupVarsomButton()
        setupDaySelector()
        loadDetailedForecast()
    }

    private fun setupVarsomButton() {
        findViewById<android.widget.Button>(R.id.open_varsom_button).setOnClickListener {
            openVarsomWebsite()
        }
    }

    private fun openVarsomWebsite() {
        if (list.isEmpty()) return

        val report = list[selectedDayIndex]
        val regionName = report.RegionName.replace(" ", "%20")
        val date = report.ValidFrom.substring(0, 10) // Format: yyyy-MM-dd

        // URL format: https://www.varsom.no/snoskred/varsling/varsel/Tromsø/2025-12-02
        val url = "https://www.varsom.no/snoskred/varsling/varsel/$regionName/$date"

        val browserIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
        browserIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(browserIntent)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    private fun loadDetailedForecast() {
        val lang = Locale.getDefault()
        val langCode = if (lang.toString().contains("nb")) 1 else 2

        val (yesterdayDate, dayAfterTomorrowDate) = getYesterdayAndDayAfterTomorrow()
        val url = "${WidgetConstants.API_BASE_URL}/AvalancheWarningByRegion/detail/$regionId/$langCode/$yesterdayDate/$dayAfterTomorrowDate"

        Log.d("ForecastDetail", "Loading detailed forecast from: $url")

        httpClient.makeRequest(url) { response, error ->
            if (error != null) {
                Log.e("ForecastDetail", "Error loading detailed forecast", error)
                runOnUiThread { updateDay(1) }
            } else {
                response?.let {
                    try {
                        val gson = Gson()
                        val listType = object : TypeToken<ArrayList<DetailedAvalancheReport>>() {}.type
                        detailedList = gson.fromJson(it, listType)
                        runOnUiThread { updateDay(1) }
                    } catch (e: Exception) {
                        Log.e("ForecastDetail", "Error parsing detailed forecast", e)
                        runOnUiThread { updateDay(1) }
                    }
                }
            }
        }
    }

    private fun setupDaySelector() {
        val container = findViewById<LinearLayout>(R.id.day_selector_container)
        container.removeAllViews()

        list.forEachIndexed { index, report ->
            val cardView = CardView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = 12
                }
                radius = 12f
                cardElevation = if (index == selectedDayIndex) 8f else 4f
                setCardBackgroundColor(getColor(android.R.color.transparent))
            }

            val innerLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                setPadding(20, 16, 20, 16)
                // Always use the danger level color, but make selected one brighter
                setBackgroundResource(DangerLevelMapper.getWarningDrawable(report.DangerLevel))
                alpha = if (index == selectedDayIndex) 1.0f else 0.7f
            }

            val dangerText = TextView(this).apply {
                text = report.DangerLevel
                textSize = 32f
                setTextColor(getColor(android.R.color.white))
                gravity = android.view.Gravity.CENTER
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setShadowLayer(4f, 0f, 2f, getColor(android.R.color.black))
            }

            val dateText = TextView(this).apply {
                text = parseAndFormatDate(report.ValidFrom)
                textSize = 12f
                setTextColor(getColor(android.R.color.white))
                gravity = android.view.Gravity.CENTER
                setPadding(0, 4, 0, 0)
            }

            innerLayout.addView(dangerText)
            innerLayout.addView(dateText)
            cardView.addView(innerLayout)

            cardView.setOnClickListener {
                selectedDayIndex = index
                updateDay(index)
                setupDaySelector()
            }

            container.addView(cardView)
        }
    }

    private fun updateDay(index: Int) {
        val report = list[index]

        findViewById<TextView>(R.id.preview_risk_number).text = report.DangerLevel
        findViewById<TextView>(R.id.preview_area_name).text = report.RegionName
        findViewById<TextView>(R.id.preview_day).text = parseDayName(report.ValidFrom)
        findViewById<TextView>(R.id.preview_date).text = parseDateString(report.ValidFrom)

        findViewById<ImageView>(R.id.preview_risk_image)
            .setImageResource(DangerLevelMapper.getLevelIcon(report.DangerLevel))

        findViewById<CardView>(R.id.preview_card)
            .setCardBackgroundColor(getColor(
                when(report.DangerLevel) {
                    "0" -> android.R.color.darker_gray
                    "1" -> android.R.color.holo_green_dark
                    "2" -> android.R.color.holo_orange_light
                    "3" -> android.R.color.holo_orange_dark
                    "4" -> android.R.color.holo_red_dark
                    else -> android.R.color.darker_gray
                }
            ))

        // Update button for current day
        updateVarsomButton()

        if (::detailedList.isInitialized && index < detailedList.size) {
            displayDetailedInfo(detailedList[index])
        }
    }

    private fun updateVarsomButton() {
        val button = findViewById<android.widget.Button>(R.id.open_varsom_button)
        val report = list[selectedDayIndex]
        val date = parseAndFormatDate(report.ValidFrom)
        button.text = "🔗 Åpne Varsom.no"
    }

    private fun displayDetailedInfo(detailedReport: DetailedAvalancheReport) {
        // Clear all containers
        findViewById<LinearLayout>(R.id.oppsummering_container).removeAllViews()
        findViewById<LinearLayout>(R.id.skredfare_container).removeAllViews()
        findViewById<LinearLayout>(R.id.skredproblemer_container).removeAllViews()
        findViewById<LinearLayout>(R.id.snow_weather_container).removeAllViews()
        findViewById<LinearLayout>(R.id.advice_container).removeAllViews()
        findViewById<LinearLayout>(R.id.observations_container).removeAllViews()

        // 1. OPPSUMMERING - MainText
        if (!detailedReport.mainText.isNullOrBlank()) {
            addSectionCard(findViewById(R.id.oppsummering_container),
                null, detailedReport.mainText)
        }

        // 2. SKREDFAREVURDERING - Avalanche Danger
        if (!detailedReport.avalancheDanger.isNullOrBlank()) {
            addSectionCard(findViewById(R.id.skredfare_container),
                null, detailedReport.avalancheDanger)
        }

        // 3. SKREDPROBLEMER - Avalanche Problems
        detailedReport.avalancheProblems?.let { problems ->
            if (problems.isNotEmpty()) {
                problems.forEach { problem ->
                    addProblemCard(findViewById(R.id.skredproblemer_container), problem)
                }
            } else {
                addSectionCard(findViewById(R.id.skredproblemer_container),
                    null, "Ingen spesifikke skredproblemer rapportert for denne dagen.")
            }
        }

        // 4. SNØDEKKE OG FJELLVÆR
        val snowWeatherContainer = findViewById<LinearLayout>(R.id.snow_weather_container)

        // Snow Surface
        if (!detailedReport.snowSurface.isNullOrBlank()) {
            addSectionCard(snowWeatherContainer, "Snøoverflate", detailedReport.snowSurface)
        }

        // Current Weak Layers
        if (!detailedReport.currentWeakLayers.isNullOrBlank()) {
            addSectionCard(snowWeatherContainer, "Svake lag", detailedReport.currentWeakLayers)
        }

        // Mountain Weather
        detailedReport.mountainWeather?.let { weather ->
            val weatherText = buildWeatherText(weather)
            if (weatherText.isNotBlank()) {
                addSectionCard(snowWeatherContainer, "Fjellvær", weatherText)
            }
        }

        // 5. RÅD - Latest Avalanche Activity (brukes som råd)
        if (!detailedReport.latestAvalancheActivity.isNullOrBlank()) {
            addSectionCard(findViewById(R.id.advice_container),
                "Siste skredaktivitet", detailedReport.latestAvalancheActivity)
        }

        // 6. OBSERVASJONER SISTE 3 DØGN
        if (!detailedReport.latestObservations.isNullOrBlank()) {
            addSectionCard(findViewById(R.id.observations_container),
                null, detailedReport.latestObservations)
        } else {
            addSectionCard(findViewById(R.id.observations_container),
                null, "Ingen observasjoner rapportert de siste 3 døgnene.")
        }
    }

    private fun addSectionCard(container: LinearLayout, title: String?, content: String) {
        val card = LayoutInflater.from(this)
            .inflate(R.layout.view_info_card, container, false)

        if (title != null) {
            card.findViewById<TextView>(R.id.card_title).text = title
            card.findViewById<TextView>(R.id.card_title).visibility = android.view.View.VISIBLE
        } else {
            card.findViewById<TextView>(R.id.card_title).visibility = android.view.View.GONE
        }

        card.findViewById<TextView>(R.id.card_content).text = content
        container.addView(card)
    }

    private fun addProblemCard(container: LinearLayout, problem: AvalancheProblem) {
        val problemCard = LayoutInflater.from(this)
            .inflate(R.layout.view_problem, container, false)

        problemCard.findViewById<TextView>(R.id.problem_title).text =
            problem.typeName ?: "Skredproblem"

        val description = buildString {
            problem.causeName?.let { append("Årsak: $it\n") }
            problem.triggerName?.let { append("Utløsning: $it\n") }
            problem.sizeName?.let { append("Størrelse: $it\n") }
            problem.extName?.let { append("Utbredelse: $it\n") }
            problem.propagationName?.let { append("Propogering: $it\n") }
            problem.validExpositions?.let { append("Himmelretninger: $it\n") }
            if (problem.exposedHeight1 != null && problem.exposedHeight2 != null) {
                append("Høyde: ${problem.exposedHeight1}-${problem.exposedHeight2}m")
            }
        }

        problemCard.findViewById<TextView>(R.id.problem_description).text =
            description.ifEmpty { "Ingen ytterligere detaljer tilgjengelig." }

        container.addView(problemCard)
    }

    private fun buildWeatherText(weather: MountainWeather): String {
        return buildString {
            // Main weather comment
            weather.comment?.let {
                append("💬 ")
                append(it)
                append("\n\n")
            }

            // Measurement texts (sorted summaries)
            weather.measurementTexts?.sortedBy { it.sortOrder }?.forEach { measurement ->
                measurement.text?.let {
                    append("• ")
                    append(it)
                    append("\n")
                }
            }

            if (weather.measurementTexts?.isNotEmpty() == true) {
                append("\n")
            }

            // Detailed measurements
            weather.measurementTypes?.sortedBy { it.sortOrder }?.forEach { measurementType ->
                when (measurementType.name) {
                    "Rainfall" -> {
                        append("🌧️ Nedbør\n")
                        measurementType.subTypes?.forEach { subType ->
                            when (subType.name) {
                                "Average" -> append("Gjennomsnitt: ${subType.value} mm\n")
                                "Most exposed area" -> append("Mest utsatte områder: ${subType.value} mm\n")
                            }
                        }
                        append("\n")
                    }
                    "Wind" -> {
                        append("🌬️ Vind\n")
                        var direction = ""
                        var speed = ""
                        measurementType.subTypes?.forEach { subType ->
                            when (subType.name) {
                                "Direction" -> direction = subType.value ?: ""
                                "Speed" -> speed = subType.value ?: ""
                            }
                        }
                        if (speed.isNotEmpty()) append("Styrke: $speed\n")
                        if (direction.isNotEmpty()) append("Retning: $direction\n")
                        append("\n")
                    }
                    "Temperature" -> {
                        append("🌡️ Temperatur\n")
                        var min = ""
                        var max = ""
                        var elevation = ""
                        measurementType.subTypes?.forEach { subType ->
                            when (subType.name) {
                                "Min" -> min = subType.value ?: ""
                                "Max" -> max = subType.value ?: ""
                                "masl" -> elevation = subType.value ?: ""
                            }
                        }
                        if (min.isNotEmpty() && max.isNotEmpty()) {
                            append("$min°C til $max°C")
                            if (elevation.isNotEmpty()) {
                                append(" på ${elevation}m o.h.")
                            }
                            append("\n")
                        }
                        append("\n")
                    }
                    "Freezing Level" -> {
                        append("❄️ Frysepunkt\n")
                        var elevation = ""
                        var timeEnd = ""
                        measurementType.subTypes?.forEach { subType ->
                            when (subType.name) {
                                "masl" -> elevation = subType.value ?: ""
                                "Timeperiode end" -> timeEnd = subType.value ?: ""
                            }
                        }
                        if (elevation.isNotEmpty()) {
                            append("${elevation}m o.h.")
                            if (timeEnd.isNotEmpty()) {
                                append(" (frem til kl. $timeEnd)")
                            }
                            append("\n")
                        }
                        append("\n")
                    }
                    "Rain/Snow Line Altitude" -> {
                        append("🌨️ Regn/Snøgrense\n")
                        var elevation = ""
                        var timeStart = ""
                        var timeEnd = ""
                        measurementType.subTypes?.forEach { subType ->
                            when (subType.name) {
                                "masl" -> elevation = subType.value ?: ""
                                "Timeperiode start" -> timeStart = subType.value ?: ""
                                "Timeperiode end" -> timeEnd = subType.value ?: ""
                            }
                        }
                        if (elevation.isNotEmpty()) {
                            append("${elevation}m o.h.")
                            if (timeStart.isNotEmpty() && timeEnd.isNotEmpty()) {
                                append(" (kl. $timeStart-$timeEnd)")
                            } else if (timeEnd.isNotEmpty()) {
                                append(" (frem til kl. $timeEnd)")
                            }
                            append("\n")
                        }
                        append("\n")
                    }
                }
            }

            // Cloud cover
            weather.cloudCover?.let {
                append("☁️ Skydekke: ")
                append(it)
                append("\n")
            }
        }.trim()
    }

    override fun onDestroy() {
        super.onDestroy()
        httpClient.cancelAll()
    }
}