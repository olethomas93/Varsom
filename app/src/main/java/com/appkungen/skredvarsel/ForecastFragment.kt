package com.appkungen.skredvarsel

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import com.appkungen.varsomwidget.R
import com.appkungen.skredvarsel.models.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class ForecastFragment : Fragment() {

    private lateinit var forecasts: ArrayList<AvalancheReport>
    private lateinit var detailedList: ArrayList<DetailedAvalancheReport>
    private var selectedDayIndex = 1
    private var regionId: String = "3011"
    private val httpClient = HttpClient(NetworkModule.httpClient)

    companion object {
        private const val ARG_FORECASTS_JSON = "forecasts_json"
        private const val ARG_REGION_ID = "region_id"

        fun newInstance(forecasts: ArrayList<AvalancheReport>, regionId: String): ForecastFragment {
            val fragment = ForecastFragment()
            val args = Bundle()
            args.putString(ARG_FORECASTS_JSON, Gson().toJson(forecasts))
            args.putString(ARG_REGION_ID, regionId)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            val json = it.getString(ARG_FORECASTS_JSON)
            forecasts = if (json != null) parseJsonToArrayList(json) else ArrayList()
            regionId = it.getString(ARG_REGION_ID) ?: "3011"
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_forecast, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupVarsomButton(view)
        setupDaySelector(view)
        loadDetailedForecast()
    }

    private fun setupVarsomButton(view: View) {
        view.findViewById<Button>(R.id.open_varsom_button).setOnClickListener {
            openVarsomWebsite()
        }
    }

    private fun openVarsomWebsite() {
        if (forecasts.isEmpty()) return

        val report = forecasts[selectedDayIndex]
        val regionName = android.net.Uri.encode(report.RegionName)
        val date = report.ValidFrom.substring(0, 10)

        val url = "https://www.varsom.no/snoskred/varsling/varsel/$regionName/$date"

        val browserIntent = android.content.Intent(
            android.content.Intent.ACTION_VIEW,
            android.net.Uri.parse(url)
        )
        browserIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(browserIntent)
    }

    private fun loadDetailedForecast() {
        val lang = java.util.Locale.getDefault()
        val langCode = if (lang.toString().contains("nb")) 1 else 2

        val (yesterdayDate, dayAfterTomorrowDate) = getYesterdayAndDayAfterTomorrow()
        val url = "${WidgetConstants.API_BASE_URL}/AvalancheWarningByRegion/detail/$regionId/$langCode/$yesterdayDate/$dayAfterTomorrowDate"

        Log.d("ForecastFragment", "Loading detailed forecast from: $url")

        httpClient.makeRequest(url) { response, error ->
            if (error != null) {
                Log.e("ForecastFragment", "Error loading detailed forecast", error)
                activity?.runOnUiThread { updateDay(1) }
            } else {
                response?.let {
                    try {
                        val gson = Gson()
                        val listType = object : TypeToken<ArrayList<DetailedAvalancheReport>>() {}.type
                        detailedList = gson.fromJson(it, listType)
                        activity?.runOnUiThread { updateDay(1) }
                    } catch (e: Exception) {
                        Log.e("ForecastFragment", "Error parsing detailed forecast", e)
                        activity?.runOnUiThread { updateDay(1) }
                    }
                }
            }
        }
    }

    private fun setupDaySelector(rootView: View) {
        val container = rootView.findViewById<LinearLayout>(R.id.day_selector_container)
        container.removeAllViews()

        forecasts.forEachIndexed { index, report ->
            val cardView = CardView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = 12
                }
                radius = 12f
                cardElevation = if (index == selectedDayIndex) 8f else 4f
                setCardBackgroundColor(requireContext().getColor(android.R.color.transparent))
            }

            val innerLayout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                setPadding(20, 16, 20, 16)
                setBackgroundResource(DangerLevelMapper.getWarningDrawable(report.DangerLevel))
                alpha = if (index == selectedDayIndex) 1.0f else 0.7f
            }

            val dangerText = TextView(requireContext()).apply {
                text = report.DangerLevel
                textSize = 32f
                setTextColor(requireContext().getColor(android.R.color.white))
                gravity = android.view.Gravity.CENTER
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setShadowLayer(4f, 0f, 2f, requireContext().getColor(android.R.color.black))
            }

            val dateText = TextView(requireContext()).apply {
                text = parseAndFormatDate(report.ValidFrom)
                textSize = 12f
                setTextColor(requireContext().getColor(android.R.color.white))
                gravity = android.view.Gravity.CENTER
                setPadding(0, 4, 0, 0)
            }

            innerLayout.addView(dangerText)
            innerLayout.addView(dateText)
            cardView.addView(innerLayout)

            cardView.setOnClickListener {
                selectedDayIndex = index
                updateDay(index)
                setupDaySelector(rootView)
            }

            container.addView(cardView)
        }
    }

    private fun updateDay(index: Int) {
        val view = view ?: return
        val report = forecasts[index]

        view.findViewById<TextView>(R.id.preview_risk_number).text = report.DangerLevel
        view.findViewById<TextView>(R.id.preview_area_name).text = report.RegionName
        view.findViewById<TextView>(R.id.preview_day).text = parseDayName(report.ValidFrom)
        view.findViewById<TextView>(R.id.preview_date).text = parseDateString(report.ValidFrom)

        view.findViewById<ImageView>(R.id.preview_risk_image)
            .setImageResource(DangerLevelMapper.getLevelIcon(report.DangerLevel))

        view.findViewById<CardView>(R.id.preview_card)
            .setCardBackgroundColor(requireContext().getColor(
                when(report.DangerLevel) {
                    "0" -> android.R.color.darker_gray
                    "1" -> android.R.color.holo_green_dark
                    "2" -> android.R.color.holo_orange_light
                    "3" -> android.R.color.holo_orange_dark
                    "4" -> android.R.color.holo_red_dark
                    else -> android.R.color.darker_gray
                }
            ))

        if (::detailedList.isInitialized && index < detailedList.size) {
            displayDetailedInfo(detailedList[index])
        }
    }

    private fun displayDetailedInfo(detailedReport: DetailedAvalancheReport) {
        val view = view ?: return

        // Clear all containers
        view.findViewById<LinearLayout>(R.id.oppsummering_container).removeAllViews()
        view.findViewById<LinearLayout>(R.id.skredfare_container).removeAllViews()
        view.findViewById<LinearLayout>(R.id.skredproblemer_container).removeAllViews()
        view.findViewById<LinearLayout>(R.id.snow_weather_container).removeAllViews()
        view.findViewById<LinearLayout>(R.id.advice_container).removeAllViews()
        view.findViewById<LinearLayout>(R.id.observations_container).removeAllViews()

        // 1. OPPSUMMERING - MainText
        if (!detailedReport.mainText.isNullOrBlank()) {
            addSectionCard(view.findViewById(R.id.oppsummering_container),
                null, detailedReport.mainText)
        } else {
            addSectionCard(view.findViewById(R.id.oppsummering_container),
                null, "Ingen oppsummering tilgjengelig.")
        }

        // 2. SKREDFAREVURDERING - Avalanche Danger
        if (!detailedReport.avalancheDanger.isNullOrBlank()) {
            addSectionCard(view.findViewById(R.id.skredfare_container),
                null, detailedReport.avalancheDanger)
        } else {
            addSectionCard(view.findViewById(R.id.skredfare_container),
                null, "Ingen skredfarevurdering tilgjengelig.")
        }

        // 3. SKREDPROBLEMER - Avalanche Problems
        val problemsContainer = view.findViewById<LinearLayout>(R.id.skredproblemer_container)
        detailedReport.avalancheProblems?.let { problems ->
            if (problems.isNotEmpty()) {
                problems.forEach { problem ->
                    addProblemCard(problemsContainer, problem)
                }
            } else {
                addSectionCard(problemsContainer,
                    null, "Ingen spesifikke skredproblemer rapportert for denne dagen.")
            }
        } ?: run {
            addSectionCard(problemsContainer,
                null, "Ingen skredproblemdata tilgjengelig.")
        }

        // 4. SNØDEKKE OG FJELLVÆR
        val snowWeatherContainer = view.findViewById<LinearLayout>(R.id.snow_weather_container)
        var hasSnowWeatherData = false

        // Snow Surface
        if (!detailedReport.snowSurface.isNullOrBlank()) {
            addSectionCard(snowWeatherContainer, "Snøoverflate", detailedReport.snowSurface)
            hasSnowWeatherData = true
        }

        // Current Weak Layers
        if (!detailedReport.currentWeakLayers.isNullOrBlank()) {
            addSectionCard(snowWeatherContainer, "Svake lag", detailedReport.currentWeakLayers)
            hasSnowWeatherData = true
        }

        // Mountain Weather
        detailedReport.mountainWeather?.let { weather ->
            val weatherText = buildWeatherText(weather)
            if (weatherText.isNotBlank()) {
                addSectionCard(snowWeatherContainer, "Fjellvær", weatherText)
                hasSnowWeatherData = true
            }
        }

        if (!hasSnowWeatherData) {
            addSectionCard(snowWeatherContainer, null, "Ingen snø- og værdata tilgjengelig.")
        }

        // 5. RÅD - Latest Avalanche Activity
        if (!detailedReport.latestAvalancheActivity.isNullOrBlank()) {
            addSectionCard(view.findViewById(R.id.advice_container),
                "Siste skredaktivitet", detailedReport.latestAvalancheActivity)
        } else {
            addSectionCard(view.findViewById(R.id.advice_container),
                null, "Ingen råd tilgjengelig for denne dagen.")
        }

        // 6. OBSERVASJONER SISTE 3 DØGN
        if (!detailedReport.latestObservations.isNullOrBlank()) {
            addSectionCard(view.findViewById(R.id.observations_container),
                null, detailedReport.latestObservations)
        } else {
            addSectionCard(view.findViewById(R.id.observations_container),
                null, "Ingen observasjoner rapportert de siste 3 døgnene.")
        }
    }

    private fun addSectionCard(container: LinearLayout, title: String?, content: String) {
        val card = LayoutInflater.from(requireContext())
            .inflate(R.layout.view_info_card, container, false)

        if (title != null) {
            card.findViewById<TextView>(R.id.card_title).text = title
            card.findViewById<TextView>(R.id.card_title).visibility = View.VISIBLE
        } else {
            card.findViewById<TextView>(R.id.card_title).visibility = View.GONE
        }

        card.findViewById<TextView>(R.id.card_content).text = content
        container.addView(card)
    }

    private fun addProblemCard(container: LinearLayout, problem: AvalancheProblem) {
        val problemCard = LayoutInflater.from(requireContext())
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
            weather.comment?.let {
                append("💬 ")
                append(it)
                append("\n\n")
            }

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