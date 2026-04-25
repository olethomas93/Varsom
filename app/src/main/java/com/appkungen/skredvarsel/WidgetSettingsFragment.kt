package com.appkungen.skredvarsel

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.appkungen.skredvarsel.models.AvalancheWarning
import com.appkungen.skredvarsel.models.Region
import com.appkungen.varsomwidget.R
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

class WidgetSettingsFragment : Fragment() {

    private val httpClient = HttpClient()
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var widgetPreferences: WidgetPreferences
    private lateinit var adapter: RegionAdapter

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            getCurrentLocation()
        } else {
            Toast.makeText(requireContext(), "Location permission is required", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetPreferences = WidgetPreferences(requireContext())
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_widget_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUI(view)
        updateCurrentRegionDisplay(view)
        loadRegions(view)
    }

    private fun setupUI(view: View) {
        val useLocationButton = view.findViewById<Button>(R.id.use_location_button)
        val selectRegionButton = view.findViewById<Button>(R.id.select_region_button)
        val searchView = view.findViewById<SearchView>(R.id.region_search)
        val listView = view.findViewById<ListView>(R.id.region_list)

        useLocationButton.setOnClickListener {
            getCurrentLocation()
        }

        selectRegionButton.setOnClickListener {
            // Just scroll to list
            view.findViewById<ListView>(R.id.region_list).requestFocus()
        }

        listView.setOnItemClickListener { parent, _, position, _ ->
            val region = parent.getItemAtPosition(position) as Region

            widgetPreferences.selectedRegion = region.Id
            widgetPreferences.selectedRegionName = region.Name
            updateCurrentRegionDisplay(view)
            updateWidget()

            Toast.makeText(requireContext(), "Valgt ${region.Name}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateCurrentRegionDisplay(view: View) {
        val currentRegionText = view.findViewById<TextView>(R.id.current_region_text)

        val coord = widgetPreferences.fetchedCoord
        if (coord != null) {
            try {
                val obj = org.json.JSONObject(coord)
                val lat = String.format(Locale.US, "%.2f", obj.getDouble("lat"))
                val lng = String.format(Locale.US, "%.2f", obj.getDouble("lng"))
                currentRegionText.text = "📍 Min Posisjon ($lat, $lng)"
            } catch (e: Exception) {
                currentRegionText.text = "📍 Min Posisjon"
            }
        } else {
            val regionId = widgetPreferences.selectedRegion
            val regionName = widgetPreferences.selectedRegionName

            if (regionName != null) {
                currentRegionText.text = regionName
            } else if (regionId != null) {
                currentRegionText.text = "Region $regionId"
            } else {
                currentRegionText.text = "Ingen region valgt"
            }
        }
    }

    private fun loadRegions(view: View) {
        val (todayDate, dayAfterTomorrowDate) = getTodayAndDayAfterTomorrow()
        val url = "${WidgetConstants.API_BASE_URL}/RegionSummary/detail/${WidgetConstants.LANG_CODE_NORWEGIAN}/$todayDate/$dayAfterTomorrowDate"

        httpClient.makeRequest(url) { response, error ->
            if (error != null) {
                Log.e("WidgetSettings", "Failed to load regions", error)
                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), "Failed to load regions", Toast.LENGTH_SHORT).show()
                }
            } else {
                response?.let { parseAndDisplayRegions(it, view) }
            }
        }
    }

    private fun parseAndDisplayRegions(jsonResponse: String, view: View) {
        val list: ArrayList<Region>? = parseJsonToArrayList(jsonResponse)
        if (list != null) {
            activity?.runOnUiThread {
                try {
                    val filteredList = list.filter { it.TypeName == "A" }

                    val searchView = view.findViewById<SearchView>(R.id.region_search)
                    val listView = view.findViewById<ListView>(R.id.region_list)

                    adapter = RegionAdapter(requireContext(), ArrayList(filteredList))
                    listView.adapter = adapter

                    // Set ListView height dynamically for NestedScrollView
                    listView.post {
                        setListViewHeightBasedOnChildren(listView)
                    }

                    searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                        override fun onQueryTextSubmit(query: String?): Boolean = false

                        override fun onQueryTextChange(newText: String?): Boolean {
                            adapter.filter.filter(newText)
                            // Update height after filtering
                            listView.post {
                                setListViewHeightBasedOnChildren(listView)
                            }
                            return true
                        }
                    })
                } catch (e: Exception) {
                    Log.e("WidgetSettings", "Error displaying regions", e)
                    Toast.makeText(requireContext(), "Error displaying regions", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Helper method to set ListView height dynamically in NestedScrollView
     * This allows the ListView to show all items and scroll properly
     */
    private fun setListViewHeightBasedOnChildren(listView: ListView) {
        val listAdapter = listView.adapter ?: return

        var totalHeight = 0
        for (i in 0 until listAdapter.count) {
            val listItem = listAdapter.getView(i, null, listView)
            listItem.measure(
                View.MeasureSpec.makeMeasureSpec(listView.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.UNSPECIFIED
            )
            totalHeight += listItem.measuredHeight
        }

        val params = listView.layoutParams
        params.height = totalHeight + (listView.dividerHeight * (listAdapter.count - 1))
        listView.layoutParams = params
        listView.requestLayout()
    }

    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            if (isGPSEnabled()) {
                Log.d("WidgetSettings", "Getting current location")
                fusedLocationClient.lastLocation
                    .addOnSuccessListener { location: Location? ->
                        if (location != null) {
                            val coords = """{"lat":"${location.latitude}","lng":"${location.longitude}"}"""

                            widgetPreferences.fetchedCoord = coords
                            view?.let { updateCurrentRegionDisplay(it) }
                            updateWidget()

                            Toast.makeText(requireContext(), "Bruker din posisjon", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(requireContext(), "Unable to get location", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e("WidgetSettings", "Failed to get location", e)
                        Toast.makeText(requireContext(), "Failed to get location", Toast.LENGTH_SHORT).show()
                    }
            } else {
                Toast.makeText(requireContext(), "Vennligst aktiver GPS", Toast.LENGTH_SHORT).show()
            }
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun isGPSEnabled(): Boolean {
        val locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    private fun updateWidget() {
        // Send broadcast to update all widgets
        val intent = android.content.Intent(requireContext(), VarsomWidgetProvider::class.java)
        intent.action = android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE

        val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(requireContext())
        val appWidgetIds = appWidgetManager.getAppWidgetIds(
            android.content.ComponentName(requireContext(), VarsomWidgetProvider::class.java)
        )
        intent.putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)

        requireContext().sendBroadcast(intent)

        Log.d("WidgetSettings", "Widget update broadcast sent for ${appWidgetIds.size} widgets")
    }

    private fun parseJsonToArrayList(jsonString: String): ArrayList<Region> {
        val gson = Gson()
        val listType = object : TypeToken<ArrayList<Region>>() {}.type
        return gson.fromJson(jsonString, listType)
    }

    private fun getTodayAndDayAfterTomorrow(): Pair<String, String> {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        val today = Calendar.getInstance()
        val todayFormatted = dateFormat.format(today.time)

        val dayAfterTomorrow = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_MONTH, 2)
        }
        val dayAfterTomorrowFormatted = dateFormat.format(dayAfterTomorrow.time)

        return Pair(todayFormatted, dayAfterTomorrowFormatted)
    }

    override fun onDestroy() {
        super.onDestroy()
        httpClient.cancelAll()
    }
}